<?php
require_once __DIR__ . '/../lib/v2_auth.php';

if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') {
    json_err('Method Not Allowed', 405);
}

function referral_client_ip(): string
{
    $forwarded = $_SERVER['HTTP_X_FORWARDED_FOR'] ?? '';
    if ($forwarded !== '') {
        $first = trim(explode(',', $forwarded)[0]);
        if (filter_var($first, FILTER_VALIDATE_IP)) {
            return $first;
        }
    }
    $remote = $_SERVER['REMOTE_ADDR'] ?? '0.0.0.0';
    return filter_var($remote, FILTER_VALIDATE_IP) ? $remote : '0.0.0.0';
}

function referral_lock_wallet(mysqli $conn, int $userId): int
{
    $stmt = $conn->prepare('SELECT points_balance FROM user_wallet WHERE user_id = ? FOR UPDATE');
    if (!$stmt) {
        throw new RuntimeException('Failed to prepare wallet lock');
    }
    $stmt->bind_param('i', $userId);
    if (!$stmt->execute()) {
        throw new RuntimeException('Failed to lock wallet');
    }
    $res = $stmt->get_result();
    $row = $res ? $res->fetch_assoc() : null;
    $stmt->close();

    if (!$row) {
        $insertStmt = $conn->prepare('INSERT INTO user_wallet (user_id, points_balance) VALUES (?, 0)');
        if (!$insertStmt) {
            throw new RuntimeException('Failed to prepare wallet creation');
        }
        $insertStmt->bind_param('i', $userId);
        if (!$insertStmt->execute()) {
            throw new RuntimeException('Failed to create wallet');
        }
        $insertStmt->close();

        $stmt = $conn->prepare('SELECT points_balance FROM user_wallet WHERE user_id = ? FOR UPDATE');
        if (!$stmt) {
            throw new RuntimeException('Failed to re-lock wallet');
        }
        $stmt->bind_param('i', $userId);
        if (!$stmt->execute()) {
            throw new RuntimeException('Failed to re-lock wallet');
        }
        $res = $stmt->get_result();
        $row = $res ? $res->fetch_assoc() : null;
        $stmt->close();
    }

    return (int)($row['points_balance'] ?? 0);
}

function referral_update_wallet_balance(mysqli $conn, int $userId, int $newBalance): void
{
    $stmt = $conn->prepare('UPDATE user_wallet SET points_balance = ? WHERE user_id = ?');
    if (!$stmt) {
        throw new RuntimeException('Failed to prepare wallet update');
    }
    $newBalanceParam = (string)$newBalance;
    $stmt->bind_param('si', $newBalanceParam, $userId);
    if (!$stmt->execute()) {
        throw new RuntimeException('Failed to update wallet');
    }
    $stmt->close();
}

function referral_insert_ledger(mysqli $conn, int $userId, int $delta, int $balanceAfter, string $refType, string $refId, ?string $metaJson): void
{
    $stmt = $conn->prepare("
        INSERT INTO points_ledger
            (user_id, type, delta_points, balance_after, ref_type, ref_id, meta_json)
        VALUES
            (?, 'EARN_REFERRAL', ?, ?, ?, ?, ?)
    ");
    if (!$stmt) {
        throw new RuntimeException('Failed to prepare ledger insert');
    }
    $deltaParam = (string)$delta;
    $balanceAfterParam = (string)$balanceAfter;
    $stmt->bind_param('isssss', $userId, $deltaParam, $balanceAfterParam, $refType, $refId, $metaJson);
    if (!$stmt->execute()) {
        throw new RuntimeException('Failed to insert ledger');
    }
    $stmt->close();
}

$user = require_user($conn);
$refereeId = (int)$user['id'];
$clientIp = referral_client_ip();
$deviceId = trim((string)($_SERVER['HTTP_X_DEVICE_ID'] ?? ''));

$antiFraudPatterns = [];
if ($clientIp !== '') {
    $antiFraudPatterns[] = '%"ip":"' . $conn->real_escape_string($clientIp) . '"%';
}
if ($deviceId !== '') {
    $antiFraudPatterns[] = '%"device_id":"' . $conn->real_escape_string($deviceId) . '"%';
}

if (!empty($antiFraudPatterns)) {
    $conditions = [];
    foreach ($antiFraudPatterns as $_) {
        $conditions[] = 'meta_json LIKE ?';
    }
    $sql = "
        SELECT COUNT(*) AS cnt
        FROM points_ledger
        WHERE type = 'EARN_REFERRAL'
          AND ref_type = 'referral_referee_reward'
          AND created_at >= CURDATE()
          AND (" . implode(' OR ', $conditions) . ")
    ";
    $stmt = $conn->prepare($sql);
    if ($stmt) {
        $types = str_repeat('s', count($antiFraudPatterns));
        $stmt->bind_param($types, ...$antiFraudPatterns);
        $stmt->execute();
        $res = $stmt->get_result();
        $row = $res ? $res->fetch_assoc() : null;
        $stmt->close();
        if ((int)($row['cnt'] ?? 0) >= 5) {
            json_err('Referral reward rate limit reached', 429);
        }
    }
}

$conn->begin_transaction();
try {
    $refStmt = $conn->prepare("
        SELECT id, referrer_id, referee_id, status
        FROM referrals
        WHERE referee_id = ?
        LIMIT 1
        FOR UPDATE
    ");
    if (!$refStmt) {
        throw new RuntimeException('Failed to prepare referral lookup');
    }
    $refStmt->bind_param('i', $refereeId);
    $refStmt->execute();
    $refRes = $refStmt->get_result();
    $referral = $refRes ? $refRes->fetch_assoc() : null;
    $refStmt->close();

    if (!$referral) {
        throw new InvalidArgumentException('no_referral');
    }
    if ((string)$referral['status'] === 'REWARDED') {
        throw new InvalidArgumentException('already_rewarded');
    }
    if ((string)$referral['status'] !== 'QUALIFIED') {
        throw new InvalidArgumentException('not_qualified');
    }

    $referrerId = (int)$referral['referrer_id'];
    $referralId = (int)$referral['id'];
    $referrerReward = 200;
    $refereeReward = 100;

    $firstUserId = min($referrerId, $refereeId);
    $secondUserId = max($referrerId, $refereeId);
    $firstBalance = referral_lock_wallet($conn, $firstUserId);
    $secondBalance = ($secondUserId === $firstUserId) ? $firstBalance : referral_lock_wallet($conn, $secondUserId);

    $referrerBalance = ($referrerId === $firstUserId) ? $firstBalance : $secondBalance;
    $refereeBalance = ($refereeId === $firstUserId) ? $firstBalance : $secondBalance;

    $newReferrerBalance = $referrerBalance + $referrerReward;
    $newRefereeBalance = $refereeBalance + $refereeReward;

    referral_update_wallet_balance($conn, $referrerId, $newReferrerBalance);
    referral_update_wallet_balance($conn, $refereeId, $newRefereeBalance);

    $meta = json_encode([
        'referral_id' => $referralId,
        'ip' => $clientIp,
        'device_id' => $deviceId,
    ], JSON_UNESCAPED_UNICODE);

    referral_insert_ledger(
        $conn,
        $referrerId,
        $referrerReward,
        $newReferrerBalance,
        'referral_referrer_reward',
        (string)$referralId,
        $meta
    );
    referral_insert_ledger(
        $conn,
        $refereeId,
        $refereeReward,
        $newRefereeBalance,
        'referral_referee_reward',
        (string)$referralId,
        $meta
    );

    $updateStmt = $conn->prepare("UPDATE referrals SET status = 'REWARDED' WHERE id = ? LIMIT 1");
    if (!$updateStmt) {
        throw new RuntimeException('Failed to prepare referral update');
    }
    $updateStmt->bind_param('i', $referralId);
    if (!$updateStmt->execute()) {
        throw new RuntimeException('Failed to update referral status');
    }
    $updateStmt->close();

    $conn->commit();

    json_ok([
        'success' => true,
        'referrer_points_added' => $referrerReward,
        'referee_points_added' => $refereeReward,
    ]);
} catch (Throwable $e) {
    $conn->rollback();

    if ($e instanceof InvalidArgumentException) {
        if ($e->getMessage() === 'no_referral') {
            json_err('No referral found for this user', 404);
        }
        if ($e->getMessage() === 'already_rewarded') {
            json_err('Referral already rewarded', 409);
        }
        if ($e->getMessage() === 'not_qualified') {
            json_err('Referral is not qualified yet', 409);
        }
    }

    json_err('Failed to claim referral reward', 500);
}
