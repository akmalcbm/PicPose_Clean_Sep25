<?php
require_once __DIR__ . '/../lib/v2_common.php';
require_once __DIR__ . '/../lib/v2_prompt_access.php';
require_once __DIR__ . '/../../../app/helpers/potd_helper.php';

header('Cache-Control: private, max-age=60, stale-while-revalidate=120');

$today = v2_prompt_current_db_date($conn);
$potd = potd_resolve_effective_prompt_offer($conn, $today);

if (!$potd || (int)($potd['post_id'] ?? 0) <= 0) {
    json_ok([
        'success' => true,
        'day_date' => $today,
        'post' => null,
        'potd_mode' => null,
        'potd_unlock_cost_points' => 0,
        'source' => 'EMPTY',
        'entry_id' => null,
        'title_override' => null,
        'subtitle_override' => null,
        'badge_text' => null,
        'effective_start_date' => null,
        'effective_end_date' => null,
    ]);
}

$isVisibleSelect = v2_prompt_select_column_expr($conn, 'p', 'is_visible_in_general_feed');
$creditEnabledSelect = v2_prompt_select_column_expr($conn, 'p', 'credit_unlock_enabled');
$rewardEnabledSelect = v2_prompt_select_column_expr($conn, 'p', 'reward_unlock_enabled');
$tokenEnabledSelect = v2_prompt_select_column_expr($conn, 'p', 'token_unlock_enabled');
$subscriberEnabledSelect = v2_prompt_select_column_expr($conn, 'p', 'subscriber_unlock_enabled');

$stmt = $conn->prepare(" 
    SELECT
        p.id, p.title, p.short_description, p.prompt_text,
        p.image_url1, p.image_url2,
        COALESCE(p.likes,0) AS likes,
        COALESCE(p.favorites,0) AS favorites,
        COALESCE(p.copies,0) AS copies,
        COALESCE(p.views,0) AS views,
        p.is_popular, p.is_featured, p.status, p.priority,
        p.created_at, p.updated_at,
        p.tier, p.premium_unlock_cost_points, p.premium_pack,
        {$isVisibleSelect},
        {$creditEnabledSelect},
        {$rewardEnabledSelect},
        {$tokenEnabledSelect},
        {$subscriberEnabledSelect},
        c.name AS category_name,
        p.tags
    FROM ai_posts p
    LEFT JOIN categories c ON c.id = p.category_id
    WHERE p.id = ? AND p.status = 'published'
    LIMIT 1
");
if (!$stmt) {
    json_err('Database query preparation failed', 500);
}
$postId = (int)$potd['post_id'];
$stmt->bind_param('i', $postId);
$stmt->execute();
$res = $stmt->get_result();
$row = $res ? $res->fetch_assoc() : null;
$stmt->close();

if (!$row) {
    json_ok([
        'success' => true,
        'day_date' => $today,
        'post' => null,
        'potd_mode' => null,
        'potd_unlock_cost_points' => 0,
        'source' => 'EMPTY',
        'entry_id' => null,
        'title_override' => null,
        'subtitle_override' => null,
        'badge_text' => null,
        'effective_start_date' => null,
        'effective_end_date' => null,
    ]);
}

$userProfile = v2_prompt_optional_user_profile($conn);
$userId = $userProfile['id'] ?? null;
$hasActiveSubscription = (bool)($userProfile['has_active_subscription'] ?? false);

$packLinksMap = v2_prompt_pack_links_for_posts($conn, [$postId], $userId !== null ? (int)$userId : null);
$packLinks = $packLinksMap[$postId] ?? [];
$flags = v2_prompt_resolve_flags_from_row($row, !empty($packLinks));
$costMeta = v2_prompt_apply_effective_credit_cost(
    $conn,
    $postId,
    $flags,
    $userId !== null ? (int)$userId : null,
    $potd
);
$mode = strtoupper((string)($costMeta['potd_mode'] ?? 'NORMAL'));

$unlockMap = [];
if ($userId !== null) {
    $unlockMap = v2_pack_prompt_entitlement_map($conn, (int)$userId, [$postId]);
}

$isUnlocked = v2_prompt_is_unlocked(
    $flags,
    isset($unlockMap[$postId]),
    $hasActiveSubscription
);
if ($mode === 'FREE') {
    $isUnlocked = true;
}

$baseProto = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') ? 'https' : 'http';
$baseUrl = $baseProto . '://' . ($_SERVER['HTTP_HOST'] ?? 'localhost') . '/';
$post = v2_prompt_build_payload($row, $flags, $isUnlocked, $baseUrl, $packLinks);

$titleOverride = trim((string)($potd['title_override'] ?? ''));
$subtitleOverride = trim((string)($potd['subtitle_override'] ?? ''));
$badgeText = trim((string)($potd['badge_text'] ?? ''));

if ($titleOverride !== '') {
    $post['title'] = $titleOverride;
}
if ($subtitleOverride !== '') {
    $post['teaserText'] = $subtitleOverride;
}

json_ok([
    'success' => true,
    'day_date' => (string)($potd['day_date'] ?? $today),
    'post' => $post,
    'potd_mode' => $mode,
    'potd_unlock_cost_points' => (int)($post['premiumUnlockCostPoints'] ?? 0),
    'source' => (string)($potd['source'] ?? 'UNKNOWN'),
    'entry_id' => isset($potd['entry_id']) ? (int)$potd['entry_id'] : null,
    'title_override' => $titleOverride !== '' ? $titleOverride : null,
    'subtitle_override' => $subtitleOverride !== '' ? $subtitleOverride : null,
    'badge_text' => $badgeText !== '' ? $badgeText : null,
    'effective_start_date' => $potd['effective_start_date'] ?? null,
    'effective_end_date' => $potd['effective_end_date'] ?? null,
    'display_title' => (string)($post['title'] ?? ''),
    'display_subtitle' => $subtitleOverride !== '' ? $subtitleOverride : null,
]);
