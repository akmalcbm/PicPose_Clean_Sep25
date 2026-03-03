<?php
require_once __DIR__ . '/../lib/v2_auth.php';
require_once __DIR__ . '/../lib/v2_progress.php';
require_once __DIR__ . '/../lib/v2_personalization.php';
require_once __DIR__ . '/../referrals/mark_qualified.php';

if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') {
    json_err('Method Not Allowed', 405);
}

$user = require_user($conn);
$userId = (int)$user['id'];

$payload = json_decode(file_get_contents('php://input') ?: '', true);
if (!is_array($payload)) {
    json_err('Invalid JSON body', 400);
}

$postId = (int)($payload['post_id'] ?? 0);
if ($postId <= 0) {
    json_err('Invalid post_id', 400);
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
$post = $postRes ? $postRes->fetch_assoc() : null;
$postStmt->close();

if (!$post) {
    json_err('Prompt not eligible for token unlock', 404);
}

$conn->begin_transaction();
try {
    $tokenStmt = $conn->prepare("
        SELECT balance
        FROM user_tokens
        WHERE user_id = ?
          AND token_type = 'PROMPT_UNLOCK'
        FOR UPDATE
    ");
    if (!$tokenStmt) {
        throw new RuntimeException('Failed to prepare token lock');
    }
    $tokenStmt->bind_param('i', $userId);
    if (!$tokenStmt->execute()) {
        throw new RuntimeException('Failed to lock token balance');
    }
    $tokenRes = $tokenStmt->get_result();
    $tokenRow = $tokenRes ? $tokenRes->fetch_assoc() : null;
    $tokenStmt->close();

    $currentTokenBalance = (int)($tokenRow['balance'] ?? 0);

    $unlockStmt = $conn->prepare("
        INSERT INTO user_prompt_unlocks (user_id, post_id, unlock_type, points_spent, ref_type, ref_id)
        VALUES (?, ?, 'SUBSCRIPTION', 0, 'token', ?)
    ");
    if (!$unlockStmt) {
        throw new RuntimeException('Failed to prepare unlock insert');
    }
    $unlockRefId = (string)$postId;
    $unlockStmt->bind_param('iis', $userId, $postId, $unlockRefId);
    $unlockOk = $unlockStmt->execute();
    $unlockErr = (int)$unlockStmt->errno;
    $unlockStmt->close();

    if (!$unlockOk && $unlockErr === 1062) {
        $conn->commit();
        json_ok([
            'success' => true,
            'unlocked' => true,
            'token_balance' => $currentTokenBalance,
        ]);
    }

    if (!$unlockOk) {
        throw new RuntimeException('Failed to insert unlock');
    }

    if ($currentTokenBalance <= 0) {
        throw new RuntimeException('Insufficient tokens');
    }

    $newTokenBalance = $currentTokenBalance - 1;
    $updateTokenStmt = $conn->prepare("
        UPDATE user_tokens
        SET balance = ?
        WHERE user_id = ?
          AND token_type = 'PROMPT_UNLOCK'
    ");
    if (!$updateTokenStmt) {
        throw new RuntimeException('Failed to prepare token update');
    }
    $updateTokenStmt->bind_param('ii', $newTokenBalance, $userId);
    if (!$updateTokenStmt->execute()) {
        throw new RuntimeException('Failed to update token balance');
    }
    $updateTokenStmt->close();

    $xpAward = award_xp($conn, $userId, 'PREMIUM_UNLOCK', 30, 'premium_unlock_token_xp', (string)$postId);
    update_user_tag_scores($conn, $userId, v2_personalization_load_post_signals($conn, $postId), 8);

    $conn->commit();

    referral_mark_qualified($conn, $userId);

    json_ok([
        'success' => true,
        'unlocked' => true,
        'token_balance' => $newTokenBalance,
        'level_reward_points' => (int)($xpAward['levelRewardPoints'] ?? 0),
    ]);
} catch (Throwable $e) {
    $conn->rollback();

    if ($e->getMessage() === 'Insufficient tokens') {
        json_err('Insufficient PROMPT_UNLOCK tokens', 402);
    }

    json_err('Failed to unlock prompt with token', 500);
}
