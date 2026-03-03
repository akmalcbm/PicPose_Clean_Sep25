<?php
require_once __DIR__ . '/../lib/v2_auth.php';

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

$postStmt = $conn->prepare("
    SELECT id
    FROM ai_posts
    WHERE id = ?
      AND status = 'published'
      AND tier = 'PREMIUM'
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

$dupStmt = $conn->prepare("
    SELECT 1
    FROM user_daily_claims
    WHERE user_id = ?
      AND claim_type = 'AD_UNLOCK'
      AND ref_id = ?
    LIMIT 1
");
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
        'unlocked' => $alreadyUnlocked,
        'duplicate' => true,
    ]);
}

$capStmt = $conn->prepare("
    SELECT COUNT(*) AS total_claims
    FROM user_daily_claims
    WHERE user_id = ?
      AND claim_type = 'AD_UNLOCK'
      AND claim_date = CURDATE()
");
if (!$capStmt) {
    json_err('Database query preparation failed', 500);
}
$capStmt->bind_param('i', $userId);
$capStmt->execute();
$capRes = $capStmt->get_result();
$capRow = $capRes ? $capRes->fetch_assoc() : ['total_claims' => 0];
$capStmt->close();

$dailyClaims = (int)($capRow['total_claims'] ?? 0);
if ($dailyClaims >= 10) {
    json_err('Daily ad unlock limit reached', 429);
}

if ($alreadyUnlocked) {
    json_ok([
        'success' => true,
        'unlocked' => true,
        'duplicate' => false,
    ]);
}

$conn->begin_transaction();
try {
    $sqlErrno = 0;

    $unlockStmt = $conn->prepare("
        INSERT INTO user_prompt_unlocks (user_id, post_id, unlock_type, points_spent, ref_type, ref_id)
        VALUES (?, ?, 'AD', 0, 'ad_reward', ?)
    ");
    if (!$unlockStmt) {
        throw new RuntimeException('Failed to prepare unlock insert');
    }
    $unlockStmt->bind_param('iis', $userId, $postId, $adRewardId);
    if (!$unlockStmt->execute()) {
        $sqlErrno = (int)$unlockStmt->errno;
        throw new RuntimeException('Unlock insert failed');
    }
    $unlockStmt->close();

    $claimStmt = $conn->prepare("
        INSERT INTO user_daily_claims (user_id, claim_date, claim_type, ref_id)
        VALUES (?, CURDATE(), 'AD_UNLOCK', ?)
    ");
    if (!$claimStmt) {
        throw new RuntimeException('Failed to prepare claim insert');
    }
    $claimStmt->bind_param('is', $userId, $adRewardId);
    if (!$claimStmt->execute()) {
        $sqlErrno = (int)$claimStmt->errno;
        throw new RuntimeException('Claim insert failed');
    }
    $claimStmt->close();

    $conn->commit();
} catch (Throwable $e) {
    $conn->rollback();

    // Duplicate-key safety for concurrent requests.
    if (isset($sqlErrno) && $sqlErrno === 1062) {
        $postUnlockCheckStmt = $conn->prepare('SELECT 1 FROM user_prompt_unlocks WHERE user_id = ? AND post_id = ? LIMIT 1');
        if ($postUnlockCheckStmt) {
            $postUnlockCheckStmt->bind_param('ii', $userId, $postId);
            $postUnlockCheckStmt->execute();
            $postUnlockCheckRes = $postUnlockCheckStmt->get_result();
            $isNowUnlocked = (bool)($postUnlockCheckRes && $postUnlockCheckRes->fetch_assoc());
            $postUnlockCheckStmt->close();

            json_ok([
                'success' => true,
                'unlocked' => $isNowUnlocked,
                'duplicate' => true,
            ]);
        }
    }

    json_err('Failed to unlock prompt', 500);
}

json_ok([
    'success' => true,
    'unlocked' => true,
    'duplicate' => false,
]);
