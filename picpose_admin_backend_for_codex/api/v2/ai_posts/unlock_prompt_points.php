<?php
require_once __DIR__ . '/../lib/v2_auth.php';
require_once __DIR__ . '/../lib/v2_progress.php';
require_once __DIR__ . '/../lib/v2_personalization.php';
require_once __DIR__ . '/../lib/v2_prompt_access.php';
require_once __DIR__ . '/../referrals/mark_qualified.php';

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
// Backward compatible: existing clients implicitly request a lifetime unlock.
$unlockForever = !array_key_exists('unlock_forever', $payload)
    ? true
    : filter_var($payload['unlock_forever'], FILTER_VALIDATE_BOOLEAN, FILTER_NULL_ON_FAILURE);
if ($unlockForever === null) {
    $unlockForever = false;
}
if ($postId <= 0) {
    json_err('Invalid post_id', 400);
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
$post = $postRes ? $postRes->fetch_assoc() : null;
$postStmt->close();

if (!$post) {
    json_err('Prompt not found', 404);
}

$flags = v2_prompt_resolve_flags_from_row(
    $post,
    ((int)($post['is_in_pack'] ?? 0) === 1)
);

if (!($flags['is_premium'] ?? false) || !($flags['is_credit_unlockable'] ?? false)) {
    json_err('Prompt not eligible for points unlock', 404);
}

$costMeta = v2_prompt_apply_effective_credit_cost($conn, $postId, $flags, $userId);
$cost = (int)($costMeta['cost'] ?? 0);
$potdMode = strtoupper((string)($costMeta['potd_mode'] ?? 'NORMAL'));

if ($potdMode === 'FREE' && !$unlockForever) {
    $balStmt = $conn->prepare('SELECT points_balance FROM user_wallet WHERE user_id = ? LIMIT 1');
    if (!$balStmt) {
        json_err('Database query preparation failed', 500);
    }
    $balStmt->bind_param('i', $userId);
    $balStmt->execute();
    $balRes = $balStmt->get_result();
    $balRow = $balRes ? $balRes->fetch_assoc() : null;
    $balStmt->close();

    json_ok([
        'success' => true,
        'unlocked' => true,
        'points_balance' => (int)($balRow['points_balance'] ?? 0),
        'cost' => 0,
    ]);
}

$currentBalanceForError = null;
$conn->begin_transaction();
try {
    $walletStmt = $conn->prepare('SELECT points_balance FROM user_wallet WHERE user_id = ? FOR UPDATE');
    if (!$walletStmt) {
        throw new RuntimeException('Failed to prepare wallet lock');
    }
    $walletStmt->bind_param('i', $userId);
    if (!$walletStmt->execute()) {
        throw new RuntimeException('Failed to lock wallet');
    }
    $walletRes = $walletStmt->get_result();
    $walletRow = $walletRes ? $walletRes->fetch_assoc() : null;
    $walletStmt->close();

    if (!$walletRow) {
        $insertWalletStmt = $conn->prepare('INSERT INTO user_wallet (user_id, points_balance) VALUES (?, 0)');
        if (!$insertWalletStmt) {
            throw new RuntimeException('Failed to prepare wallet creation');
        }
        $insertWalletStmt->bind_param('i', $userId);
        if (!$insertWalletStmt->execute()) {
            throw new RuntimeException('Failed to create wallet');
        }
        $insertWalletStmt->close();
        $walletStmt = $conn->prepare('SELECT points_balance FROM user_wallet WHERE user_id = ? FOR UPDATE');
        if (!$walletStmt) {
            throw new RuntimeException('Failed to re-lock wallet');
        }
        $walletStmt->bind_param('i', $userId);
        if (!$walletStmt->execute()) {
            throw new RuntimeException('Failed to re-lock wallet');
        }
        $walletRes = $walletStmt->get_result();
        $walletRow = $walletRes ? $walletRes->fetch_assoc() : null;
        $walletStmt->close();
    }

    if (!$walletRow) {
        throw new RuntimeException('Wallet row missing');
    }

    $currentBalance = (int)$walletRow['points_balance'];
    $currentBalanceForError = $currentBalance;

    $unlockStmt = $conn->prepare("
        INSERT INTO user_prompt_unlocks (user_id, post_id, unlock_type, points_spent, ref_type, ref_id)
        VALUES (?, ?, 'POINTS', ?, 'post', ?)
    ");
    if (!$unlockStmt) {
        throw new RuntimeException('Failed to prepare unlock insert');
    }
    $refId = (string)$postId;
    $unlockStmt->bind_param('iiis', $userId, $postId, $cost, $refId);
    $unlockOk = $unlockStmt->execute();
    $unlockErr = (int)$unlockStmt->errno;
    $unlockStmt->close();

    if (!$unlockOk && $unlockErr === 1062) {
        $conn->commit();
        json_ok([
            'success' => true,
            'message' => 'You already unlocked this prompt.',
            'unlocked' => true,
            'duplicate' => true,
            'points_balance' => $currentBalance,
            'cost' => $cost,
        ]);
    }

    if (!$unlockOk) {
        throw new RuntimeException('Failed to insert unlock');
    }

    if ($currentBalance < $cost) {
        throw new RuntimeException('Insufficient points');
    }

    $newBalance = $currentBalance - $cost;

    $deductStmt = $conn->prepare('UPDATE user_wallet SET points_balance = ? WHERE user_id = ?');
    if (!$deductStmt) {
        throw new RuntimeException('Failed to prepare wallet debit');
    }
    $newBalanceParam = (string)$newBalance;
    $deductStmt->bind_param('si', $newBalanceParam, $userId);
    if (!$deductStmt->execute()) {
        throw new RuntimeException('Failed to debit wallet');
    }
    $deductStmt->close();

    $ledgerStmt = $conn->prepare("
        INSERT INTO points_ledger
            (user_id, type, delta_points, balance_after, ref_type, ref_id, meta_json)
        VALUES
            (?, 'DEBIT_PROMPT_UNLOCK', ?, ?, 'post', ?, NULL)
    ");
    if (!$ledgerStmt) {
        throw new RuntimeException('Failed to prepare ledger insert');
    }
    $delta = -$cost;
    $deltaParam = (string)$delta;
    $balanceAfterParam = (string)$newBalance;
    // Keep refs user-scoped so global legacy unique keys do not collide
    // when different users unlock the same premium post.
    $ledgerRefId = $userId . ':' . (string)$postId;
    $ledgerStmt->bind_param('isss', $userId, $deltaParam, $balanceAfterParam, $ledgerRefId);
    $ledgerOk = $ledgerStmt->execute();
    $ledgerErr = (int)$ledgerStmt->errno;
    if (!$ledgerOk && $ledgerErr !== 1062) {
        $ledgerStmt->close();
        throw new RuntimeException('Failed to insert ledger row');
    }
    $ledgerStmt->close();

    $xpAward = award_xp($conn, $userId, 'PREMIUM_UNLOCK', 30, 'premium_unlock_xp', (string)$postId);
    update_user_tag_scores($conn, $userId, v2_personalization_load_post_signals($conn, $postId), 8);
    $finalPointsBalance = $newBalance + (int)($xpAward['levelRewardPoints'] ?? 0);

    $conn->commit();

    if ($unlockForever) {
        referral_mark_qualified($conn, $userId);
    }

    json_ok([
        'success' => true,
        'message' => 'Prompt unlocked successfully.',
        'unlocked' => true,
        'points_balance' => $finalPointsBalance,
        'cost' => $cost,
    ]);
} catch (Throwable $e) {
    $conn->rollback();

    if ($e->getMessage() === 'Insufficient points') {
        http_response_code(402);
        echo json_encode([
            'success' => false,
            'message' => 'Insufficient points',
            'required_points' => $cost,
            'current_points' => $currentBalanceForError ?? 0,
        ]);
        exit();
    }

    error_log('unlock_prompt_points failed for user ' . $userId . ' post ' . $postId . ': ' . $e->getMessage());
    json_err('Failed to unlock prompt with points', 500);
}
