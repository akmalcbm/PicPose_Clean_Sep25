<?php

require_once __DIR__ . '/_common.php';

v2_tool_render_header('V2 Master Verification Suite');

$steps = [
    ['name' => 'pre_migration_check.php', 'title' => 'Pre-migration schema check'],
    ['name' => 'post_migration_check.php', 'title' => 'Post-migration schema check'],
    ['name' => 'db_integrity_checks.php', 'title' => 'DB integrity checks'],
    ['name' => 'smoke_test_v2.php', 'title' => 'V2 smoke tests'],
];

$results = [];
$preCode = 1;

foreach ($steps as $step) {
    if ($step['name'] === 'post_migration_check.php' && $preCode !== 0) {
        $results[] = ['name' => $step['title'], 'code' => null, 'skipped' => true, 'blocker' => 'Schema not ready yet'];
        v2_tool_skip($step['title'], 'Skipped because pre-migration check reported missing schema items');
        continue;
    }

    $cmd = escapeshellarg(PHP_BINARY) . ' ' . escapeshellarg(__DIR__ . '/' . $step['name']);
    exec($cmd . ' 2>&1', $output, $exitCode);
    $results[] = ['name' => $step['title'], 'code' => $exitCode, 'skipped' => false, 'output' => $output];

    if ($step['name'] === 'pre_migration_check.php') {
        $preCode = $exitCode;
    }

    if ($exitCode === 0) {
        v2_tool_pass($step['title'], 'Completed successfully');
    } else {
        v2_tool_fail($step['title'], 'Exited with code ' . $exitCode);
    }
}

$blockers = [];
foreach ($results as $result) {
    if (!empty($result['skipped'])) {
        $blockers[] = $result['name'] . ': ' . ($result['blocker'] ?? 'Skipped');
        continue;
    }
    if (($result['code'] ?? 1) !== 0) {
        $blockers[] = $result['name'] . ' failed';
    }
}

if (empty($blockers)) {
    v2_tool_pass('Conclusion', 'READY_FOR_MIGRATION');
    v2_tool_info('Next Steps', 'Apply pending migrations if any, rerun this suite, and keep V1 traffic on unchanged endpoints');
    v2_tool_finish(0);
}

v2_tool_fail('Conclusion', 'NOT_READY');
v2_tool_info('Blockers', implode('; ', $blockers));
v2_tool_info('Next Steps', 'Fix schema/data/test failures, rerun individual checks, then rerun run_all_checks.php');
v2_tool_finish(1);
