<?php
require_once __DIR__ . '/../lib/v2_auth.php';

if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'GET') {
    json_err('Method Not Allowed', 405);
}

$user = require_user($conn);
$userId = (int)$user['id'];

function v2_default_daily_rewards_status(): array
{
    return [10, 20, 30, 40, 50, 60, 100];
}

function v2_sanitize_daily_rewards_status(array $arr): array
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

function v2_detect_config_table_status(mysqli $conn): ?string
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

function v2_load_daily_rewards_status(mysqli $conn): array
{
    $defaults = v2_default_daily_rewards_status();
    $table = v2_detect_config_table_status($conn);
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
    return v2_sanitize_daily_rewards_status($rewards);
}

function v2_reward_for_streak_status(array $rewards, int $streakCount): int
{
    if (empty($rewards)) return 0;
    $index = min(max($streakCount, 1), count($rewards)) - 1;
    return (int)($rewards[$index] ?? 0);
}

$rewardsSchedule = v2_load_daily_rewards_status($conn);
$today = date('Y-m-d');
$yesterday = date('Y-m-d', strtotime('-1 day'));

$streakCount = 0;
$lastClaimDate = null;

$streakStmt = $conn->prepare('SELECT streak_count, last_claim_date FROM user_streaks WHERE user_id = ? LIMIT 1');
if ($streakStmt) {
    $streakStmt->bind_param('i', $userId);
    $streakStmt->execute();
    $streakRes = $streakStmt->get_result();
    $streakRow = $streakRes ? $streakRes->fetch_assoc() : null;
    $streakStmt->close();
    if ($streakRow) {
        $streakCount = (int)($streakRow['streak_count'] ?? 0);
        $lastClaimDate = $streakRow['last_claim_date'] ? (string)$streakRow['last_claim_date'] : null;
    }
}

$todayClaimed = false;
$claimStmt = $conn->prepare("
    SELECT 1
    FROM user_daily_claims
    WHERE user_id = ?
      AND claim_type = 'LOGIN'
      AND claim_date = CURDATE()
    LIMIT 1
");
if ($claimStmt) {
    $claimStmt->bind_param('i', $userId);
    $claimStmt->execute();
    $claimRes = $claimStmt->get_result();
    $todayClaimed = (bool)($claimRes && $claimRes->fetch_assoc());
    $claimStmt->close();
}

$streakCountNext = 1;
if ($todayClaimed) {
    $streakCountNext = $streakCount + 1;
} elseif ($lastClaimDate === $yesterday) {
    $streakCountNext = $streakCount + 1;
}

$todayRewardStreakRef = $todayClaimed ? max(1, $streakCount) : $streakCountNext;
$todayRewardPoints = v2_reward_for_streak_status($rewardsSchedule, $todayRewardStreakRef);
$nextDayRewardPoints = v2_reward_for_streak_status($rewardsSchedule, $streakCountNext);

$pointsBalance = 0;
$walletStmt = $conn->prepare('SELECT points_balance FROM user_wallet WHERE user_id = ? LIMIT 1');
if ($walletStmt) {
    $walletStmt->bind_param('i', $userId);
    $walletStmt->execute();
    $walletRes = $walletStmt->get_result();
    $walletRow = $walletRes ? $walletRes->fetch_assoc() : null;
    $walletStmt->close();
    $pointsBalance = (int)($walletRow['points_balance'] ?? 0);
}

$tokenBalances = [
    'PROMPT_UNLOCK' => 0,
    'IMAGE_GEN_PRIORITY' => 0,
];
$tokenStmt = $conn->prepare("
    SELECT token_type, balance
    FROM user_tokens
    WHERE user_id = ?
");
if ($tokenStmt) {
    $tokenStmt->bind_param('i', $userId);
    $tokenStmt->execute();
    $tokenRes = $tokenStmt->get_result();
    while ($row = ($tokenRes ? $tokenRes->fetch_assoc() : null)) {
        $tokenType = (string)($row['token_type'] ?? '');
        if ($tokenType !== '') {
            $tokenBalances[$tokenType] = (int)($row['balance'] ?? 0);
        }
    }
    $tokenStmt->close();
}

json_ok([
    'success' => true,
    'streak_count' => $streakCount,
    'last_claim_date' => $lastClaimDate,
    'today_claimed' => $todayClaimed,
    'today_reward_points' => $todayRewardPoints,
    'next_day_reward_points' => $nextDayRewardPoints,
    'rewards_schedule' => $rewardsSchedule,
    'points_balance' => $pointsBalance,
    'token_balances' => $tokenBalances,
]);
