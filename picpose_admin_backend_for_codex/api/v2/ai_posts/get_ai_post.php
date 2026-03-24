<?php
require_once __DIR__ . '/../lib/v2_common.php';
require_once __DIR__ . '/../lib/v2_pack_entitlements.php';
require_once __DIR__ . '/../lib/v2_personalization.php';
require_once __DIR__ . '/../lib/v2_prompt_access.php';

if (!isset($_GET['id']) || !ctype_digit((string)$_GET['id'])) {
    json_err('Invalid Prompt ID', 400);
}

$promptId = intval($_GET['id']);
$baseProto = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') ? 'https' : 'http';
$baseUrl = $baseProto . '://' . ($_SERVER['HTTP_HOST'] ?? 'localhost') . '/';
$isVisibleSelect = v2_prompt_select_column_expr($conn, 'p', 'is_visible_in_general_feed');
$creditEnabledSelect = v2_prompt_select_column_expr($conn, 'p', 'credit_unlock_enabled');
$rewardEnabledSelect = v2_prompt_select_column_expr($conn, 'p', 'reward_unlock_enabled');
$tokenEnabledSelect = v2_prompt_select_column_expr($conn, 'p', 'token_unlock_enabled');
$subscriberEnabledSelect = v2_prompt_select_column_expr($conn, 'p', 'subscriber_unlock_enabled');

$sql = "SELECT
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
        LIMIT 1";

$stmt = $conn->prepare($sql);
if (!$stmt) {
    json_err('DB Error: ' . $conn->error, 500);
}

$stmt->bind_param('i', $promptId);
$stmt->execute();
$result = $stmt->get_result();
$row = $result ? $result->fetch_assoc() : null;
$stmt->close();

if (!$row) {
    json_err('Prompt Not Found', 404);
}

$userProfile = v2_prompt_optional_user_profile($conn);
$authUserId = $userProfile['id'] ?? null;
$hasActiveSubscription = (bool)($userProfile['has_active_subscription'] ?? false);

$entitlementMap = ($authUserId !== null)
    ? v2_pack_prompt_entitlement_map($conn, (int)$authUserId, [$promptId])
    : [];
$packLinksMap = v2_prompt_pack_links_for_posts($conn, [$promptId], $authUserId !== null ? (int)$authUserId : null);
$packLinks = $packLinksMap[$promptId] ?? [];

$flags = v2_prompt_resolve_flags_from_row($row, !empty($packLinks));
$isUnlocked = v2_prompt_is_unlocked(
    $flags,
    isset($entitlementMap[$promptId]),
    $hasActiveSubscription
);

$data = v2_prompt_build_payload(
    $row,
    $flags,
    $isUnlocked,
    $baseUrl,
    $packLinks
);

if ($authUserId !== null) {
    try {
        $signalTags = $data['tags'] ?? [];
        $categoryTag = v2_personalization_normalize_tag($row['category_name'] ?? null);
        if ($categoryTag !== null) {
            $signalTags[] = $categoryTag;
        }
        update_user_tag_scores($conn, (int)$authUserId, $signalTags, 1);
    } catch (Throwable $e) {
        error_log('get_ai_post personalization update failed: ' . $e->getMessage());
    }
}

json_ok([
    'success' => true,
    'message' => 'OK',
    'data' => $data,
]);
