<?php
require_once __DIR__ . '/../lib/v2_progress.php';

if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'GET') {
    json_err('Method Not Allowed', 405);
}

$user = require_user($conn);
$userId = (int)$user['id'];

$progressStmt = $conn->prepare('SELECT xp, level FROM user_progress WHERE user_id = ? LIMIT 1');
if (!$progressStmt) {
    json_err('Database query preparation failed', 500);
}
$progressStmt->bind_param('i', $userId);
$progressStmt->execute();
$progressRes = $progressStmt->get_result();
$progressRow = $progressRes ? $progressRes->fetch_assoc() : null;
$progressStmt->close();

$xp = (int)($progressRow['xp'] ?? 0);
$level = (int)($progressRow['level'] ?? 1);
$nextLevelXp = v2_level_threshold_xp($level + 1);

$walletStmt = $conn->prepare('SELECT points_balance FROM user_wallet WHERE user_id = ? LIMIT 1');
if (!$walletStmt) {
    json_err('Database query preparation failed', 500);
}
$walletStmt->bind_param('i', $userId);
$walletStmt->execute();
$walletRes = $walletStmt->get_result();
$walletRow = $walletRes ? $walletRes->fetch_assoc() : null;
$walletStmt->close();
$pointsBalance = (int)($walletRow['points_balance'] ?? 0);

$eventsStmt = $conn->prepare("
    SELECT event_type, xp_delta, created_at, ref_type, ref_id
    FROM xp_ledger
    WHERE user_id = ?
    ORDER BY id DESC
    LIMIT 20
");
if (!$eventsStmt) {
    json_err('Database query preparation failed', 500);
}
$eventsStmt->bind_param('i', $userId);
$eventsStmt->execute();
$eventsRes = $eventsStmt->get_result();

$recentEvents = [];
while ($row = ($eventsRes ? $eventsRes->fetch_assoc() : null)) {
    $recentEvents[] = [
        'eventType' => $row['event_type'],
        'xpDelta' => (int)$row['xp_delta'],
        'createdAt' => $row['created_at'],
        'refType' => $row['ref_type'],
        'refId' => $row['ref_id'],
    ];
}
$eventsStmt->close();

json_ok([
    'success' => true,
    'xp' => $xp,
    'level' => $level,
    'next_level_xp' => $nextLevelXp,
    'points_reward_next' => 50,
    'points_balance' => $pointsBalance,
    'recent_events' => $recentEvents,
]);
