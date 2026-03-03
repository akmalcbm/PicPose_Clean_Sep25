<?php
require_once __DIR__ . '/v2_auth.php';

function v2_progress_optional_user_id(mysqli $conn): ?int
{
    $token = get_bearer_token();
    if ($token === null) {
        return null;
    }

    $stmt = $conn->prepare('SELECT id FROM users WHERE api_token = ? LIMIT 1');
    if (!$stmt) {
        return null;
    }
    $stmt->bind_param('s', $token);
    $stmt->execute();
    $res = $stmt->get_result();
    $row = $res ? $res->fetch_assoc() : null;
    $stmt->close();

    return $row ? (int)$row['id'] : null;
}

function v2_level_formula(int $xp): int
{
    if ($xp < 0) {
        $xp = 0;
    }
    return (int)floor(sqrt($xp / 100)) + 1;
}

function v2_level_threshold_xp(int $level): int
{
    if ($level <= 1) {
        return 0;
    }
    $prev = $level - 1;
    return $prev * $prev * 100;
}

function v2_ensure_user_progress_locked(mysqli $conn, int $userId): array
{
    $stmt = $conn->prepare('SELECT xp, level FROM user_progress WHERE user_id = ? FOR UPDATE');
    if (!$stmt) {
        throw new RuntimeException('Failed to prepare progress lock');
    }
    $stmt->bind_param('i', $userId);
    if (!$stmt->execute()) {
        throw new RuntimeException('Failed to lock progress');
    }
    $res = $stmt->get_result();
    $row = $res ? $res->fetch_assoc() : null;
    $stmt->close();

    if (!$row) {
        $insertStmt = $conn->prepare('INSERT INTO user_progress (user_id, xp, level) VALUES (?, 0, 1)');
        if (!$insertStmt) {
            throw new RuntimeException('Failed to prepare progress creation');
        }
        $insertStmt->bind_param('i', $userId);
        if (!$insertStmt->execute()) {
            throw new RuntimeException('Failed to create progress');
        }
        $insertStmt->close();

        $stmt = $conn->prepare('SELECT xp, level FROM user_progress WHERE user_id = ? FOR UPDATE');
        if (!$stmt) {
            throw new RuntimeException('Failed to re-lock progress');
        }
        $stmt->bind_param('i', $userId);
        if (!$stmt->execute()) {
            throw new RuntimeException('Failed to re-lock progress');
        }
        $res = $stmt->get_result();
        $row = $res ? $res->fetch_assoc() : null;
        $stmt->close();
    }

    return [
        'xp' => (int)($row['xp'] ?? 0),
        'level' => (int)($row['level'] ?? 1),
    ];
}

function v2_ensure_wallet_locked(mysqli $conn, int $userId): int
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

function award_xp(mysqli $conn, int $userId, string $eventType, int $xpDelta, string $refType, string $refId): array
{
    if ($xpDelta <= 0) {
        return [
            'awarded' => false,
            'duplicate' => false,
            'xp' => 0,
            'level' => 1,
            'levelUps' => 0,
            'levelRewardPoints' => 0,
        ];
    }

    $progress = v2_ensure_user_progress_locked($conn, $userId);
    $currentXp = $progress['xp'];
    $currentLevel = $progress['level'];

    $insertStmt = $conn->prepare("
        INSERT INTO xp_ledger (user_id, event_type, xp_delta, ref_type, ref_id)
        VALUES (?, ?, ?, ?, ?)
    ");
    if (!$insertStmt) {
        throw new RuntimeException('Failed to prepare XP ledger insert');
    }
    $insertStmt->bind_param('isiss', $userId, $eventType, $xpDelta, $refType, $refId);
    $ok = $insertStmt->execute();
    $errno = (int)$insertStmt->errno;
    $insertStmt->close();

    if (!$ok && $errno === 1062) {
        return [
            'awarded' => false,
            'duplicate' => true,
            'xp' => $currentXp,
            'level' => $currentLevel,
            'levelUps' => 0,
            'levelRewardPoints' => 0,
        ];
    }
    if (!$ok) {
        throw new RuntimeException('Failed to insert XP ledger');
    }

    $newXp = $currentXp + $xpDelta;
    $newLevel = v2_level_formula($newXp);
    $levelUps = max(0, $newLevel - $currentLevel);
    $levelRewardPoints = $levelUps * 50;

    $updateStmt = $conn->prepare('UPDATE user_progress SET xp = ?, level = ?, updated_at = CURRENT_TIMESTAMP WHERE user_id = ?');
    if (!$updateStmt) {
        throw new RuntimeException('Failed to prepare progress update');
    }
    $updateStmt->bind_param('iii', $newXp, $newLevel, $userId);
    if (!$updateStmt->execute()) {
        throw new RuntimeException('Failed to update progress');
    }
    $updateStmt->close();

    if ($levelRewardPoints > 0) {
        $currentBalance = v2_ensure_wallet_locked($conn, $userId);
        $newBalance = $currentBalance + $levelRewardPoints;

        $walletUpdateStmt = $conn->prepare('UPDATE user_wallet SET points_balance = ? WHERE user_id = ?');
        if (!$walletUpdateStmt) {
            throw new RuntimeException('Failed to prepare level reward wallet update');
        }
        $newBalanceParam = (string)$newBalance;
        $walletUpdateStmt->bind_param('si', $newBalanceParam, $userId);
        if (!$walletUpdateStmt->execute()) {
            throw new RuntimeException('Failed to apply level reward');
        }
        $walletUpdateStmt->close();

        for ($level = $currentLevel + 1; $level <= $newLevel; $level++) {
            $ledgerStmt = $conn->prepare("
                INSERT INTO points_ledger
                    (user_id, type, delta_points, balance_after, ref_type, ref_id, meta_json)
                VALUES
                    (?, 'EARN_LEVEL_UP', ?, ?, 'level_up', ?, NULL)
            ");
            if (!$ledgerStmt) {
                throw new RuntimeException('Failed to prepare level reward ledger');
            }
            $deltaParam = (string)50;
            $balanceAfter = $currentBalance + (($level - $currentLevel) * 50);
            $balanceAfterParam = (string)$balanceAfter;
            $levelRefId = (string)$level;
            $ledgerStmt->bind_param('isss', $userId, $deltaParam, $balanceAfterParam, $levelRefId);
            if (!$ledgerStmt->execute()) {
                throw new RuntimeException('Failed to insert level reward ledger');
            }
            $ledgerStmt->close();
        }
    }

    return [
        'awarded' => true,
        'duplicate' => false,
        'xp' => $newXp,
        'level' => $newLevel,
        'levelUps' => $levelUps,
        'levelRewardPoints' => $levelRewardPoints,
    ];
}
