<?php
require_once __DIR__ . '/../lib/v2_auth.php';
require_once __DIR__ . '/../lib/v2_prompt_access.php';
require_once __DIR__ . '/../referrals/mark_qualified.php';

const V2_PROMPT_AD_UNLOCK_DAILY_CAP = 10;

function v2_prompt_ad_receipts_table_exists(mysqli $conn): bool
{
    static $exists = null;
    if ($exists !== null) {
        return $exists;
    }

    $stmt = $conn->prepare("
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = DATABASE()
          AND table_name = 'user_prompt_ad_unlock_receipts'
        LIMIT 1
    ");
    if (!$stmt) {
        $exists = false;
        return false;
    }
    $stmt->execute();
    $res = $stmt->get_result();
    $exists = (bool)($res && $res->fetch_assoc());
    $stmt->close();

    return $exists;
}

if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') {
    json_err('Method Not Allowed', 405);
}

$user = require_user($conn);
$userId = (int)$user['id'];

$raw = file_get_contents('php://input');
$payload = json_decode($raw ?? '', true);
if (!is_array($payload)) {
    json_err('Invalid JSON body', 400);
}

$postId = intval($payload['post_id'] ?? 0);
$adRewardId = trim((string)($payload['ad_reward_id'] ?? ''));

if ($postId <= 0) {
    json_err('Invalid post_id', 400);
}
if ($adRewardId === '' || strlen($adRewardId) > 80) {
    json_err('Invalid ad_reward_id', 400);
}
if (!preg_match('/^[A-Za-z0-9._:-]+$/', $adRewardId)) {
    json_err('Invalid ad_reward_id format', 400);
}

$isVisibleSelect = v2_prompt_select_column_expr($conn, 'ai_posts', 'is_visible_in_general_feed');
$creditEnabledSelect = v2_prompt_select_column_expr($conn, 'ai_posts', 'credit_unlock_enabled');
$rewardEnabledSelect = v2_prompt_select_column_expr($conn, 'ai_posts', 'reward_unlock_enabled');
$tokenEnabledSelect = v2_prompt_select_column_expr($conn, 'ai_posts', 'token_unlock_enabled');
$subscriberEnabledSelect = v2_prompt_select_column_expr($conn, 'ai_posts', 'subscriber_unlock_enabled');

$postStmt = $conn->prepare(" 
    SELECT
        id,
        tier,
        COALESCE(premium_unlock_cost_points, 0) AS premium_unlock_cost_points,
        {$isVisibleSelect},
        {$creditEnabledSelect},
        {$rewardEnabledSelect},
        {$tokenEnabledSelect},
        {$subscriberEnabledSelect},
        EXISTS(SELECT 1 FROM premium_pack_items ppi WHERE ppi.post_id = ai_posts.id) AS is_in_pack
    FROM ai_posts
    WHERE id = ?
      AND status = 'published'
    LIMIT 1
");
if (!$postStmt) {
    json_err('Database query preparation failed', 500);
}
$postStmt->bind_param('i', $postId);
$postStmt->execute();
$postRes = $postStmt->get_result();
$postRow = $postRes ? $postRes->fetch_assoc() : null;
$postStmt->close();

if (!$postRow) {
    json_err('Prompt not found', 404);
}

$flags = v2_prompt_resolve_flags_from_row(
    $postRow,
    ((int)($postRow['is_in_pack'] ?? 0) === 1)
);
if (!($flags['is_premium'] ?? false) || !($flags['is_rewarded_unlockable'] ?? false)) {
    json_err('Prompt not eligible for ad unlock', 404);
}

$alreadyUnlockedStmt = $conn->prepare('SELECT 1 FROM user_prompt_unlocks WHERE user_id = ? AND post_id = ? LIMIT 1');
if (!$alreadyUnlockedStmt) {
    json_err('Database query preparation failed', 500);
}
$alreadyUnlockedStmt->bind_param('ii', $userId, $postId);
$alreadyUnlockedStmt->execute();
$alreadyUnlockedRes = $alreadyUnlockedStmt->get_result();
$alreadyUnlocked = (bool)($alreadyUnlockedRes && $alreadyUnlockedRes->fetch_assoc());
$alreadyUnlockedStmt->close();

if ($alreadyUnlocked) {
    json_ok([
        'success' => true,
        'unlocked' => true,
        'duplicate' => false,
    ]);
}

$useReceiptsTable = v2_prompt_ad_receipts_table_exists($conn);
$dupSql = $useReceiptsTable
    ? '
        SELECT 1
        FROM user_prompt_ad_unlock_receipts
        WHERE user_id = ?
          AND ad_reward_id = ?
        LIMIT 1
    '
    : "
        SELECT 1
        FROM user_daily_claims
        WHERE user_id = ?
          AND claim_type = 'AD_UNLOCK'
          AND ref_id = ?
        LIMIT 1
    ";
$dupStmt = $conn->prepare($dupSql);
if (!$dupStmt) {
    json_err('Database query preparation failed', 500);
}
$dupStmt->bind_param('is', $userId, $adRewardId);
$dupStmt->execute();
$dupRes = $dupStmt->get_result();
$isDuplicate = (bool)($dupRes && $dupRes->fetch_assoc());
$dupStmt->close();

if ($isDuplicate) {
    json_ok([
        'success' => true,
        'unlocked' => false,
        'duplicate' => true,
    ]);
}

$capSql = $useReceiptsTable
    ? '
        SELECT COUNT(*) AS total_claims
        FROM user_prompt_ad_unlock_receipts
        WHERE user_id = ?
          AND claim_date = CURDATE()
    '
    : "
        SELECT COUNT(*) AS total_claims
        FROM user_daily_claims
        WHERE user_id = ?
          AND claim_type = 'AD_UNLOCK'
          AND claim_date = CURDATE()
    ";
$capStmt = $conn->prepare($capSql);
if (!$capStmt) {
    json_err('Database query preparation failed', 500);
}
$capStmt->bind_param('i', $userId);
$capStmt->execute();
$capRes = $capStmt->get_result();
$capRow = $capRes ? $capRes->fetch_assoc() : ['total_claims' => 0];
$capStmt->close();

$dailyClaims = (int)($capRow['total_claims'] ?? 0);
if ($dailyClaims >= V2_PROMPT_AD_UNLOCK_DAILY_CAP) {
    json_err('Daily ad unlock limit reached', 429);
}

$conn->begin_transaction();
try {
    $receiptSql = $useReceiptsTable
        ? '
            INSERT INTO user_prompt_ad_unlock_receipts (user_id, post_id, ad_reward_id, claim_date)
            VALUES (?, ?, ?, CURDATE())
        '
        : "
            INSERT INTO user_daily_claims (user_id, claim_date, claim_type, ref_id)
            VALUES (?, CURDATE(), 'AD_UNLOCK', ?)
        ";
    $receiptStmt = $conn->prepare($receiptSql);
    if (!$receiptStmt) {
        throw new RuntimeException('Failed to prepare ad receipt insert');
    }
    if ($useReceiptsTable) {
        $receiptStmt->bind_param('iis', $userId, $postId, $adRewardId);
    } else {
        $receiptStmt->bind_param('is', $userId, $adRewardId);
    }
    if (!$receiptStmt->execute()) {
        $errno = (int)$receiptStmt->errno;
        $receiptStmt->close();
        if ($errno === 1062) {
            throw new RuntimeException('DUPLICATE_AD_RECEIPT');
        }
        throw new RuntimeException('Failed to insert ad receipt');
    }
    $receiptStmt->close();

    $unlockStmt = $conn->prepare('
        INSERT INTO user_prompt_unlocks (user_id, post_id, unlock_type, points_spent, ref_type, ref_id)
        VALUES (?, ?, \'AD\', 0, \'ad_reward\', ?)
    ');
    if (!$unlockStmt) {
        throw new RuntimeException('Failed to prepare unlock insert');
    }
    $unlockStmt->bind_param('iis', $userId, $postId, $adRewardId);
    if (!$unlockStmt->execute()) {
        $errno = (int)$unlockStmt->errno;
        $unlockStmt->close();
        if ($errno === 1062) {
            throw new RuntimeException('ALREADY_UNLOCKED');
        }
        throw new RuntimeException('Unlock insert failed');
    }
    $unlockStmt->close();

    $conn->commit();
} catch (Throwable $e) {
    $conn->rollback();

    if ($e->getMessage() === 'DUPLICATE_AD_RECEIPT') {
        json_ok([
            'success' => true,
            'unlocked' => false,
            'duplicate' => true,
        ]);
    }

    if ($e->getMessage() === 'ALREADY_UNLOCKED') {
        json_ok([
            'success' => true,
            'unlocked' => true,
            'duplicate' => true,
        ]);
    }

    error_log('unlock_prompt_ad failed for user ' . $userId . ' post ' . $postId . ': ' . $e->getMessage());
    json_err('Failed to unlock prompt', 500);
}

mark_referral_qualified($conn, $userId);

json_ok([
    'success' => true,
    'unlocked' => true,
    'duplicate' => false,
]);
