<?php
require_once __DIR__ . '/../lib/v2_auth.php';
require_once __DIR__ . '/../lib/v2_ab.php';
require_once __DIR__ . '/../lib/v2_progress.php';
require_once __DIR__ . '/../lib/v2_personalization.php';
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

$postStmt = $conn->prepare("
    SELECT id, COALESCE(premium_unlock_cost_points, 0) AS premium_unlock_cost_points
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
    json_err('Prompt not eligible for points unlock', 404);
}

$configuredCost = (int)($post['premium_unlock_cost_points'] ?? 0);
$normalCost = $configuredCost > 0 ? $configuredCost : 200;
$variant = get_user_variant($conn, $userId, 'premium_unlock_cost_multiplier');
$multiplier = v2_ab_variant_numeric($conn, 'premium_unlock_cost_multiplier', $variant, 1.0);
if ($multiplier <= 0) {
    $multiplier = 1.0;
}
$cost = max(0, (int)round($normalCost * $multiplier));

$potdMode = 'NORMAL';
$potdDiscountCost = 0;
$potdStmt = $conn->prepare("
    SELECT mode, discount_cost_points
    FROM daily_featured_prompts
    WHERE day_date = CURDATE()
      AND post_id = ?
    LIMIT 1
");
if ($potdStmt) {
    $potdStmt->bind_param('i', $postId);
    $potdStmt->execute();
    $potdRes = $potdStmt->get_result();
    $potdRow = $potdRes ? $potdRes->fetch_assoc() : null;
    $potdStmt->close();

    if ($potdRow) {
        $potdMode = strtoupper((string)($potdRow['mode'] ?? 'NORMAL'));
        $potdDiscountCost = (int)($potdRow['discount_cost_points'] ?? 0);
    }
}

if ($potdMode === 'DISCOUNT') {
    $potdVariant = get_user_variant($conn, $userId, 'potd_discount_cost');
    $potdCost = (int)round(v2_ab_variant_numeric($conn, 'potd_discount_cost', $potdVariant, (float)$potdDiscountCost));
    $cost = max(0, $potdCost);
} elseif ($potdMode === 'FREE' && !$unlockForever) {
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
            'unlocked' => true,
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
    $ledgerRefId = (string)$postId;
    $ledgerStmt->bind_param('isss', $userId, $deltaParam, $balanceAfterParam, $ledgerRefId);
    if (!$ledgerStmt->execute()) {
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
        'unlocked' => true,
        'points_balance' => $finalPointsBalance,
        'cost' => $cost,
    ]);
} catch (Throwable $e) {
    $conn->rollback();

    if ($e->getMessage() === 'Insufficient points') {
        json_err('Insufficient points', 402);
    }

    json_err('Failed to unlock prompt with points', 500);
}
