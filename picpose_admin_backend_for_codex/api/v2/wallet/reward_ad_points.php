<?php
require_once __DIR__ . '/../lib/v2_auth.php';
require_once __DIR__ . '/../lib/v2_ab.php';

const V2_AD_DAILY_REWARD_CAP = 1;

if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') {
    json_err('Method Not Allowed', 405);
}

$user = require_user($conn);
$userId = (int)$user['id'];

function v2_fetch_wallet_balance(mysqli $conn, int $userId): int
{
    $balStmt = $conn->prepare('SELECT points_balance FROM user_wallet WHERE user_id = ? LIMIT 1');
    if (!$balStmt) {
        return 0;
    }
    $balStmt->bind_param('i', $userId);
    $balStmt->execute();
    $balRes = $balStmt->get_result();
    $balRow = $balRes ? $balRes->fetch_assoc() : null;
    $balStmt->close();
    return (int)($balRow['points_balance'] ?? 0);
}

$raw = file_get_contents('php://input');
$payload = json_decode($raw ?? '', true);
if (!is_array($payload)) {
    json_err('Invalid JSON body', 400);
}

$adRewardId = trim((string)($payload['ad_reward_id'] ?? ''));
if ($adRewardId === '' || strlen($adRewardId) > 80) {
    json_err('Invalid ad_reward_id', 400);
}
if (!preg_match('/^[A-Za-z0-9._:-]+$/', $adRewardId)) {
    json_err('Invalid ad_reward_id format', 400);
}

$variant = get_user_variant($conn, $userId, 'ad_points_reward');
$pointsToAdd = (int)round(v2_ab_variant_numeric($conn, 'ad_points_reward', $variant, 10.0));
if ($pointsToAdd <= 0) {
    $pointsToAdd = 10;
}

$dupStmt = $conn->prepare("
    SELECT 1
    FROM user_daily_claims
    WHERE user_id = ?
      AND claim_type = 'AD_POINTS'
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
    $balance = v2_fetch_wallet_balance($conn, $userId);
    json_ok([
        'success' => true,
        'message' => 'Reward already processed.',
        'points_added' => 0,
        'points_balance' => $balance,
        'ad_daily_count' => 1,
        'ad_daily_cap' => V2_AD_DAILY_REWARD_CAP,
        'ad_reward_points' => $pointsToAdd,
        'ad_reward_available' => false,
    ]);
}

$capStmt = $conn->prepare("
    SELECT COUNT(*) AS total_claims
    FROM user_daily_claims
    WHERE user_id = ?
      AND claim_type = 'AD_POINTS'
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
if ($dailyClaims >= V2_AD_DAILY_REWARD_CAP) {
    $balance = v2_fetch_wallet_balance($conn, $userId);
    json_ok([
        'success' => true,
        'message' => 'Today\'s ad reward is already claimed.',
        'points_added' => 0,
        'points_balance' => $balance,
        'ad_daily_count' => $dailyClaims,
        'ad_daily_cap' => V2_AD_DAILY_REWARD_CAP,
        'ad_reward_points' => $pointsToAdd,
        'ad_reward_available' => false,
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
    $newBalance = $currentBalance + $pointsToAdd;

    $claimStmt = $conn->prepare("
        INSERT INTO user_daily_claims (user_id, claim_date, claim_type, ref_id)
        VALUES (?, CURDATE(), 'AD_POINTS', ?)
    ");
    if (!$claimStmt) {
        throw new RuntimeException('Failed to prepare claim insert');
    }
    $claimStmt->bind_param('is', $userId, $adRewardId);
    $claimOk = $claimStmt->execute();
    $claimErr = (int)$claimStmt->errno;
    $claimStmt->close();
    if (!$claimOk) {
        if ($claimErr === 1062) {
            throw new RuntimeException('Duplicate claim');
        }
        throw new RuntimeException('Failed to insert claim');
    }

    $deductStmt = $conn->prepare('UPDATE user_wallet SET points_balance = ? WHERE user_id = ?');
    if (!$deductStmt) {
        throw new RuntimeException('Failed to prepare wallet credit');
    }
    $newBalanceParam = (string)$newBalance;
    $deductStmt->bind_param('si', $newBalanceParam, $userId);
    if (!$deductStmt->execute()) {
        throw new RuntimeException('Failed to credit wallet');
    }
    $deductStmt->close();

    $ledgerStmt = $conn->prepare("
        INSERT INTO points_ledger
            (user_id, type, delta_points, balance_after, ref_type, ref_id, meta_json)
        VALUES
            (?, 'EARN_AD', ?, ?, 'ad_reward', ?, NULL)
    ");
    if (!$ledgerStmt) {
        throw new RuntimeException('Failed to prepare ledger insert');
    }
    $deltaParam = (string)$pointsToAdd;
    $balanceAfterParam = (string)$newBalance;
    $ledgerStmt->bind_param('isss', $userId, $deltaParam, $balanceAfterParam, $adRewardId);
    if (!$ledgerStmt->execute()) {
        throw new RuntimeException('Failed to insert ledger row');
    }
    $ledgerStmt->close();

    $conn->commit();

    json_ok([
        'success' => true,
        'message' => 'Ad reward credited successfully.',
        'points_added' => $pointsToAdd,
        'points_balance' => $newBalance,
        'ad_daily_count' => min($dailyClaims + 1, V2_AD_DAILY_REWARD_CAP),
        'ad_daily_cap' => V2_AD_DAILY_REWARD_CAP,
        'ad_reward_points' => $pointsToAdd,
        'ad_reward_available' => false,
    ]);
} catch (Throwable $e) {
    $conn->rollback();

    if ($e->getMessage() === 'Duplicate claim') {
        $balance = v2_fetch_wallet_balance($conn, $userId);
        json_ok([
            'success' => true,
            'message' => 'Today\'s ad reward is already claimed.',
            'points_added' => 0,
            'points_balance' => $balance,
            'ad_daily_count' => V2_AD_DAILY_REWARD_CAP,
            'ad_daily_cap' => V2_AD_DAILY_REWARD_CAP,
            'ad_reward_points' => $pointsToAdd,
            'ad_reward_available' => false,
        ]);
    }

    error_log('reward_ad_points failed for user ' . $userId . ': ' . $e->getMessage());
    json_err('Failed to reward ad points', 500);
}
