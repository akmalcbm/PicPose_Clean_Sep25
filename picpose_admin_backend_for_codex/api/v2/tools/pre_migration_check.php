<?php

require_once __DIR__ . '/_common.php';

v2_tool_render_header('V2 Pre-Migration Check');

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

$missing = [];

v2_tool_info('Database', 'Checking V2 schema prerequisites against current database');

foreach ($requiredColumns as [$table, $column]) {
    if (v2_tool_column_exists($conn, $table, $column)) {
        v2_tool_pass('Column ' . $table . '.' . $column, 'Present');
    } else {
        $missing[] = 'Missing column: ' . $table . '.' . $column;
        v2_tool_fail('Column ' . $table . '.' . $column, 'Missing');
    }
}

foreach ($requiredTables as $table) {
    if (v2_tool_table_exists($conn, $table)) {
        v2_tool_pass('Table ' . $table, 'Present');
    } else {
        $missing[] = 'Missing table: ' . $table;
        v2_tool_fail('Table ' . $table, 'Missing');
    }
}

if (empty($missing)) {
    v2_tool_pass('Summary', 'All required V2 tables and columns are present');
    v2_tool_finish(0);
}

v2_tool_fail('Summary', count($missing) . ' required schema items are missing');
v2_tool_info('Missing Items', implode('; ', $missing));
v2_tool_finish(1);
