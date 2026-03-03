<?php
require_once __DIR__ . '/../lib/v2_auth.php';
require_once __DIR__ . '/../lib/v2_progress.php';

if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') {
    json_err('Method Not Allowed', 405);
}

$user = require_user($conn);
$userId = (int)$user['id'];

$today = date('Y-m-d');
$yesterday = date('Y-m-d', strtotime('-1 day'));

function v2_default_daily_rewards(): array
{
    return [10, 20, 30, 40, 50, 60, 100];
}

function v2_sanitize_daily_rewards(array $arr): array
{
    $out = [];
    for ($i = 0; $i < 7; $i++) {
        $v = isset($arr[$i]) ? (int)$arr[$i] : 0;
        if ($v < 0) $v = 0;
        if ($v > 1000) $v = 1000;
        $out[] = $v;
    }
    return $out;
}

function v2_detect_config_table(mysqli $conn): ?string
{
    $sql = "SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ? LIMIT 1";
    $stmt = $conn->prepare($sql);
    if (!$stmt) return null;

    $table = 'pricing_config';
    $stmt->bind_param('s', $table);
    $stmt->execute();
    $exists = (bool)$stmt->get_result()->fetch_assoc();
    if ($exists) {
        $stmt->close();
        return 'pricing_config';
    }

    $table = 'app_config';
    $stmt->bind_param('s', $table);
    $stmt->execute();
    $exists = (bool)$stmt->get_result()->fetch_assoc();
    $stmt->close();
    if ($exists) {
        return 'app_config';
    }

    $createSql = "
        CREATE TABLE IF NOT EXISTS app_config (
            key_name VARCHAR(120) NOT NULL,
            value_json JSON NULL,
            updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            PRIMARY KEY (key_name)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    ";
    if (!$conn->query($createSql)) {
        return null;
    }
    return 'app_config';
}

function v2_load_daily_rewards(mysqli $conn): array
{
    $defaults = v2_default_daily_rewards();
    $table = v2_detect_config_table($conn);
    if ($table === null) return $defaults;

    $sql = "SELECT value_json FROM {$table} WHERE key_name = 'daily_login_rewards' LIMIT 1";
    $res = $conn->query($sql);
    if (!$res) return $defaults;
    $row = $res->fetch_assoc();
    if (!$row || !isset($row['value_json'])) return $defaults;

    $decoded = json_decode((string)$row['value_json'], true);
    if (!is_array($decoded)) return $defaults;
    $rewards = $decoded['rewards'] ?? null;
    if (!is_array($rewards)) return $defaults;
    return v2_sanitize_daily_rewards($rewards);
}

$dailyRewards = v2_load_daily_rewards($conn);
$milestoneTargetDay = max(1, count($dailyRewards));

$conn->begin_transaction();
try {
    $streakStmt = $conn->prepare('SELECT streak_count, last_claim_date FROM user_streaks WHERE user_id = ? FOR UPDATE');
    if (!$streakStmt) {
        throw new RuntimeException('Failed to prepare streak lock');
    }
    $streakStmt->bind_param('i', $userId);
    if (!$streakStmt->execute()) {
        throw new RuntimeException('Failed to lock streak');
    }
    $streakRes = $streakStmt->get_result();
    $streakRow = $streakRes ? $streakRes->fetch_assoc() : null;
    $streakStmt->close();

    if (!$streakRow) {
        $insStreakStmt = $conn->prepare('INSERT INTO user_streaks (user_id, streak_count, last_claim_date) VALUES (?, 0, NULL)');
        if (!$insStreakStmt) {
            throw new RuntimeException('Failed to prepare streak creation');
        }
        $insStreakStmt->bind_param('i', $userId);
        if (!$insStreakStmt->execute()) {
            throw new RuntimeException('Failed to create streak row');
        }
        $insStreakStmt->close();

        $streakStmt = $conn->prepare('SELECT streak_count, last_claim_date FROM user_streaks WHERE user_id = ? FOR UPDATE');
        if (!$streakStmt) {
            throw new RuntimeException('Failed to re-lock streak');
        }
        $streakStmt->bind_param('i', $userId);
        if (!$streakStmt->execute()) {
            throw new RuntimeException('Failed to re-lock streak');
        }
        $streakRes = $streakStmt->get_result();
        $streakRow = $streakRes ? $streakRes->fetch_assoc() : null;
        $streakStmt->close();
    }

    if (!$streakRow) {
        throw new RuntimeException('Streak row missing');
    }

    $lastClaimDate = $streakRow['last_claim_date'] ? (string)$streakRow['last_claim_date'] : null;
    $existingStreakCount = (int)($streakRow['streak_count'] ?? 0);

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
        $insWalletStmt = $conn->prepare('INSERT INTO user_wallet (user_id, points_balance) VALUES (?, 0)');
        if (!$insWalletStmt) {
            throw new RuntimeException('Failed to prepare wallet creation');
        }
        $insWalletStmt->bind_param('i', $userId);
        if (!$insWalletStmt->execute()) {
            throw new RuntimeException('Failed to create wallet row');
        }
        $insWalletStmt->close();

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

    if ($lastClaimDate === $today) {
        $conn->commit();
        json_ok([
            'success' => true,
            'claimed' => false,
            'streak_count' => $existingStreakCount,
            'points_added' => 0,
            'points_balance' => $currentBalance,
            'milestone_hit' => false,
            'already_claimed' => true,
        ]);
    }

    if ($lastClaimDate === $yesterday) {
        $newStreakCount = $existingStreakCount + 1;
    } else {
        $newStreakCount = 1;
    }

    $rewardIndex = min($newStreakCount, $milestoneTargetDay) - 1;
    if ($rewardIndex < 0) $rewardIndex = 0;
    $pointsAdded = (int)($dailyRewards[$rewardIndex] ?? 0);
    $milestoneHit = ($newStreakCount === $milestoneTargetDay);

    $loginClaimStmt = $conn->prepare("
        INSERT INTO user_daily_claims (user_id, claim_date, claim_type, ref_id)
        VALUES (?, CURDATE(), 'LOGIN', NULL)
    ");
    if (!$loginClaimStmt) {
        throw new RuntimeException('Failed to prepare login claim insert');
    }
    $loginClaimStmt->bind_param('i', $userId);
    $loginClaimOk = $loginClaimStmt->execute();
    $loginClaimErr = (int)$loginClaimStmt->errno;
    $loginClaimStmt->close();
    if (!$loginClaimOk) {
        if ($loginClaimErr === 1062) {
            $conn->commit();
            json_ok([
                'success' => true,
                'claimed' => false,
                'streak_count' => $existingStreakCount,
                'points_added' => 0,
                'points_balance' => $currentBalance,
                'milestone_hit' => false,
                'already_claimed' => true,
            ]);
        }
        throw new RuntimeException('Failed to insert login claim');
    }

    if ($milestoneHit) {
        $milestoneRef = 'streak_' . $milestoneTargetDay . '_' . $today;
        $mileClaimStmt = $conn->prepare("
            INSERT INTO user_daily_claims (user_id, claim_date, claim_type, ref_id)
            VALUES (?, CURDATE(), 'STREAK_MILESTONE', ?)
        ");
        if (!$mileClaimStmt) {
            throw new RuntimeException('Failed to prepare milestone claim insert');
        }
        $mileClaimStmt->bind_param('is', $userId, $milestoneRef);
        if (!$mileClaimStmt->execute()) {
            throw new RuntimeException('Failed to insert milestone claim');
        }
        $mileClaimStmt->close();
    }

    $updateStreakStmt = $conn->prepare('UPDATE user_streaks SET streak_count = ?, last_claim_date = ?, updated_at = CURRENT_TIMESTAMP WHERE user_id = ?');
    if (!$updateStreakStmt) {
        throw new RuntimeException('Failed to prepare streak update');
    }
    $updateStreakStmt->bind_param('isi', $newStreakCount, $today, $userId);
    if (!$updateStreakStmt->execute()) {
        throw new RuntimeException('Failed to update streak');
    }
    $updateStreakStmt->close();

    $newBalance = $currentBalance + $pointsAdded;
    $newBalanceParam = (string)$newBalance;

    $updateWalletStmt = $conn->prepare('UPDATE user_wallet SET points_balance = ? WHERE user_id = ?');
    if (!$updateWalletStmt) {
        throw new RuntimeException('Failed to prepare wallet update');
    }
    $updateWalletStmt->bind_param('si', $newBalanceParam, $userId);
    if (!$updateWalletStmt->execute()) {
        throw new RuntimeException('Failed to update wallet');
    }
    $updateWalletStmt->close();

    $baseDelta = $pointsAdded;
    $baseBalanceAfter = $newBalance;
    $baseRefId = $today;
    $ledgerBaseStmt = $conn->prepare("
        INSERT INTO points_ledger
            (user_id, type, delta_points, balance_after, ref_type, ref_id, meta_json)
        VALUES
            (?, 'EARN_DAILY_LOGIN', ?, ?, 'daily_login', ?, NULL)
    ");
    if (!$ledgerBaseStmt) {
        throw new RuntimeException('Failed to prepare base ledger insert');
    }
    $baseDeltaParam = (string)$baseDelta;
    $baseBalanceAfterParam = (string)$baseBalanceAfter;
    $ledgerBaseStmt->bind_param('isss', $userId, $baseDeltaParam, $baseBalanceAfterParam, $baseRefId);
    if (!$ledgerBaseStmt->execute()) {
        throw new RuntimeException('Failed to insert base ledger');
    }
    $ledgerBaseStmt->close();

    $xpAward = award_xp($conn, $userId, 'DAILY_LOGIN', 10, 'daily_login_xp', $today);
    $finalPointsBalance = $newBalance + (int)($xpAward['levelRewardPoints'] ?? 0);

    $conn->commit();

    json_ok([
        'success' => true,
        'claimed' => true,
        'streak_count' => $newStreakCount,
        'points_added' => $pointsAdded,
        'points_balance' => $finalPointsBalance,
        'milestone_hit' => $milestoneHit,
    ]);
} catch (Throwable $e) {
    $conn->rollback();
    json_err('Failed to claim daily login reward', 500);
}
