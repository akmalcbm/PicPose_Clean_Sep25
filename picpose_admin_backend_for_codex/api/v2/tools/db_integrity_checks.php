<?php

require_once __DIR__ . '/_common.php';

v2_tool_render_header('V2 DB Integrity Checks');

$criticalIssues = [];

$negativeWallets = (int)(v2_tool_fetch_value($conn, 'SELECT COUNT(*) FROM user_wallet WHERE points_balance < 0') ?? 0);
if ($negativeWallets === 0) {
    v2_tool_pass('Wallet balances', 'No negative balances detected');
} else {
    $criticalIssues[] = 'Negative wallet balances found';
    v2_tool_fail('Wallet balances', $negativeWallets . ' wallet rows have negative balances');
}

$sampleUsersStmt = $conn->prepare("SELECT DISTINCT user_id FROM points_ledger ORDER BY created_at DESC LIMIT 50");
$sampleUsers = [];
if ($sampleUsersStmt && $sampleUsersStmt->execute()) {
    $res = $sampleUsersStmt->get_result();
    while ($row = $res ? $res->fetch_assoc() : null) {
        $sampleUsers[] = (int)$row['user_id'];
    }
    $sampleUsersStmt->close();
}
if (empty($sampleUsers)) {
    $fallbackStmt = $conn->prepare('SELECT user_id FROM user_wallet ORDER BY updated_at DESC LIMIT 50');
    if ($fallbackStmt && $fallbackStmt->execute()) {
        $res = $fallbackStmt->get_result();
        while ($row = $res ? $res->fetch_assoc() : null) {
            $sampleUsers[] = (int)$row['user_id'];
        }
        $fallbackStmt->close();
    }
}

$ledgerMismatches = [];
foreach ($sampleUsers as $userId) {
    $walletBalance = v2_tool_fetch_value($conn, 'SELECT points_balance FROM user_wallet WHERE user_id = ? LIMIT 1', 'i', [$userId]);
    $ledgerBalance = v2_tool_fetch_value($conn, 'SELECT balance_after FROM points_ledger WHERE user_id = ? ORDER BY created_at DESC, id DESC LIMIT 1', 'i', [$userId]);
    if ($walletBalance === null || $ledgerBalance === null) {
        continue;
    }
    if ((string)$walletBalance !== (string)$ledgerBalance) {
        $ledgerMismatches[] = 'user_id=' . $userId . ' wallet=' . $walletBalance . ' ledger=' . $ledgerBalance;
    }
}

if (empty($ledgerMismatches)) {
    v2_tool_pass('Ledger consistency', 'Latest ledger balance matches wallet for sampled users');
} else {
    $criticalIssues[] = 'Wallet and ledger latest balance mismatch';
    v2_tool_fail('Ledger consistency', count($ledgerMismatches) . ' sampled users differ between wallet and latest ledger');
    v2_tool_info('Ledger mismatch sample', implode('; ', array_slice($ledgerMismatches, 0, 10)));
}

$dupUnlocks = (int)(v2_tool_fetch_value($conn, 'SELECT COUNT(*) FROM (SELECT user_id, post_id, COUNT(*) c FROM user_prompt_unlocks GROUP BY user_id, post_id HAVING c > 1) t') ?? 0);
if ($dupUnlocks === 0) {
    v2_tool_pass('user_prompt_unlocks dedupe', 'No duplicate lifetime unlock rows');
} else {
    $criticalIssues[] = 'Duplicate user_prompt_unlocks rows detected';
    v2_tool_fail('user_prompt_unlocks dedupe', $dupUnlocks . ' duplicate unlock groups found');
}

$dupClaims = (int)(v2_tool_fetch_value($conn, 'SELECT COUNT(*) FROM (SELECT user_id, claim_date, claim_type, COUNT(*) c FROM user_daily_claims GROUP BY user_id, claim_date, claim_type HAVING c > 1) t') ?? 0);
if ($dupClaims === 0) {
    v2_tool_pass('user_daily_claims dedupe', 'No duplicate daily claim rows');
} else {
    $criticalIssues[] = 'Duplicate user_daily_claims rows detected';
    v2_tool_fail('user_daily_claims dedupe', $dupClaims . ' duplicate claim groups found');
}

$dupReferees = (int)(v2_tool_fetch_value($conn, 'SELECT COUNT(*) FROM (SELECT referee_id, COUNT(*) c FROM referrals GROUP BY referee_id HAVING c > 1) t') ?? 0);
if ($dupReferees === 0) {
    v2_tool_pass('referrals dedupe', 'No duplicate referee assignments');
} else {
    $criticalIssues[] = 'Duplicate referee assignments detected';
    v2_tool_fail('referrals dedupe', $dupReferees . ' duplicate referee groups found');
}

$todayPotdCount = (int)(v2_tool_fetch_value($conn, 'SELECT COUNT(*) FROM daily_featured_prompts WHERE day_date = CURDATE()') ?? 0);
if ($todayPotdCount > 0) {
    v2_tool_pass('POTD sanity', 'Today\'s featured prompt exists in DB');
} else {
    v2_tool_skip('POTD sanity', 'Today\'s POTD row is missing; get_prompt_of_the_day.php should auto-create it on first call');
}

if (empty($criticalIssues)) {
    v2_tool_pass('Summary', 'No critical DB integrity issues detected');
    v2_tool_info('Recommendations', 'Continue with smoke tests and monitor ledger/write-heavy endpoints after deploy');
    v2_tool_finish(0);
}

v2_tool_fail('Summary', count($criticalIssues) . ' critical DB integrity issues detected');
v2_tool_info('Recommendations', 'Resolve wallet, dedupe, or ledger inconsistencies before production rollout');
v2_tool_finish(1);
