<?php

require_once __DIR__ . '/_common.php';

v2_tool_render_header('V2 Post-Migration Check');

$issues = [];
$requiredColumns = [
    ['ai_posts', 'tier'],
    ['ai_posts', 'premium_unlock_cost_points'],
    ['ai_posts', 'premium_pack'],
];
$requiredTables = [
    'user_wallet',
    'points_ledger',
    'user_prompt_unlocks',
    'user_streaks',
    'user_daily_claims',
    'daily_featured_prompts',
    'referral_codes',
    'referrals',
    'premium_packs',
    'premium_pack_items',
    'user_pack_unlocks',
    'user_progress',
    'xp_ledger',
    'user_tag_scores',
    'ab_experiments',
    'ab_user_assignments',
    'user_tokens',
];
$bestEffortIndexes = [
    ['ai_posts', 'idx_ai_posts_tier'],
    ['ai_posts', 'idx_ai_posts_pack'],
    ['points_ledger', 'ref_type'],
    ['points_ledger', 'user_id'],
    ['referrals', 'referrer_id'],
    ['premium_pack_items', 'PRIMARY'],
    ['ab_user_assignments', 'PRIMARY'],
    ['user_tokens', 'PRIMARY'],
];

foreach ($requiredColumns as [$table, $column]) {
    if (v2_tool_column_exists($conn, $table, $column)) {
        v2_tool_pass('Column ' . $table . '.' . $column, 'Present');
    } else {
        $issues[] = 'Missing column: ' . $table . '.' . $column;
        v2_tool_fail('Column ' . $table . '.' . $column, 'Missing');
    }
}

foreach ($requiredTables as $table) {
    if (v2_tool_table_exists($conn, $table)) {
        v2_tool_pass('Table ' . $table, 'Present');
    } else {
        $issues[] = 'Missing table: ' . $table;
        v2_tool_fail('Table ' . $table, 'Missing');
    }
}

$tierMeta = v2_tool_get_column_meta($conn, 'ai_posts', 'tier');
if ($tierMeta !== null) {
    $defaultTier = strtoupper((string)($tierMeta['column_default'] ?? ''));
    if ($defaultTier === 'FREE') {
        v2_tool_pass('ai_posts.tier default', 'Default is FREE');
    } else {
        $issues[] = 'ai_posts.tier default is not FREE';
        v2_tool_fail('ai_posts.tier default', 'Expected FREE, got ' . ($tierMeta['column_default'] ?? 'NULL'));
    }
}

$costMeta = v2_tool_get_column_meta($conn, 'ai_posts', 'premium_unlock_cost_points');
if ($costMeta !== null) {
    $defaultCost = (string)($costMeta['column_default'] ?? '');
    if ($defaultCost === '0') {
        v2_tool_pass('ai_posts.premium_unlock_cost_points default', 'Default is 0');
    } else {
        $issues[] = 'ai_posts.premium_unlock_cost_points default is not 0';
        v2_tool_fail('ai_posts.premium_unlock_cost_points default', 'Expected 0, got ' . ($costMeta['column_default'] ?? 'NULL'));
    }
}

$invalidTierCount = (int)(v2_tool_fetch_value($conn, "SELECT COUNT(*) FROM ai_posts WHERE tier IS NULL OR tier NOT IN ('FREE','PREMIUM')") ?? 0);
if ($invalidTierCount === 0) {
    v2_tool_pass('ai_posts tier data', 'All rows use FREE or PREMIUM');
} else {
    $issues[] = 'Invalid tier values in ai_posts';
    v2_tool_fail('ai_posts tier data', $invalidTierCount . ' rows have invalid tier values');
}

$nullOrNegativeCostCount = (int)(v2_tool_fetch_value($conn, 'SELECT COUNT(*) FROM ai_posts WHERE premium_unlock_cost_points IS NULL OR premium_unlock_cost_points < 0') ?? 0);
if ($nullOrNegativeCostCount === 0) {
    v2_tool_pass('ai_posts premium cost data', 'No null or negative premium costs');
} else {
    $issues[] = 'Invalid premium unlock costs in ai_posts';
    v2_tool_fail('ai_posts premium cost data', $nullOrNegativeCostCount . ' rows have null or negative cost values');
}

$premiumZeroCostCount = (int)(v2_tool_fetch_value($conn, "SELECT COUNT(*) FROM ai_posts WHERE tier = 'PREMIUM' AND premium_unlock_cost_points <= 0") ?? 0);
if ($premiumZeroCostCount === 0) {
    v2_tool_pass('PREMIUM prompt cost readiness', 'All PREMIUM rows have a positive unlock cost');
} else {
    $issues[] = 'PREMIUM prompts missing positive unlock cost';
    v2_tool_fail('PREMIUM prompt cost readiness', $premiumZeroCostCount . ' PREMIUM rows still have cost <= 0');
}

foreach ($bestEffortIndexes as [$table, $index]) {
    if (v2_tool_index_exists($conn, $table, $index)) {
        v2_tool_pass('Index ' . $table . '.' . $index, 'Present');
    } else {
        v2_tool_skip('Index ' . $table . '.' . $index, 'Not found in information_schema; verify migration/index naming');
    }
}

if (empty($issues)) {
    v2_tool_pass('Summary', 'Post-migration schema checks passed');
    v2_tool_finish(0);
}

v2_tool_fail('Summary', count($issues) . ' post-migration issues detected');
v2_tool_info('Recommendations', 'Fix reported schema/data issues before enabling V2 monetization flows in production');
v2_tool_finish(1);
