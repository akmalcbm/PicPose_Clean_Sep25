<?php
require_once __DIR__ . '/../lib/v2_pack_entitlements.php';
require_once __DIR__ . '/../lib/v2_prompt_access.php';

if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'GET') {
    json_err('Method Not Allowed', 405);
}

$packId = (int)($_GET['id'] ?? 0);
if ($packId <= 0) {
    json_err('Invalid pack id', 400);
}

$userProfile = v2_prompt_optional_user_profile($conn);
$userId = $userProfile['id'] ?? v2_pack_optional_user_id($conn);
$hasActiveSubscription = (bool)($userProfile['has_active_subscription'] ?? false);

$packStmt = $conn->prepare(" 
    SELECT
        pp.id,
        pp.name,
        pp.description,
        (
            SELECT COALESCE(NULLIF(p.image_url1, ''), NULLIF(p.image_url2, ''))
            FROM premium_pack_items ppi2
            INNER JOIN ai_posts p ON p.id = ppi2.post_id
            WHERE ppi2.pack_id = pp.id
              AND p.status = 'published'
            ORDER BY p.priority DESC, p.created_at DESC
            LIMIT 1
        ) AS thumbnail_path,
        pp.price_points,
        pp.is_active,
        pp.created_at,
        COUNT(ppi.post_id) AS item_count
    FROM premium_packs pp
    LEFT JOIN premium_pack_items ppi ON ppi.pack_id = pp.id
    WHERE pp.id = ?
    GROUP BY pp.id, pp.name, pp.description, pp.price_points, pp.is_active, pp.created_at
    LIMIT 1
");
if (!$packStmt) {
    json_err('Database query preparation failed', 500);
}
$packStmt->bind_param('i', $packId);
$packStmt->execute();
$packRes = $packStmt->get_result();
$pack = $packRes ? $packRes->fetch_assoc() : null;
$packStmt->close();

if (!$pack) {
    json_err('Pack not found', 404);
}

$ownsPack = ($userId !== null) ? v2_pack_user_owns_pack($conn, (int)$userId, $packId) : false;
$baseProto = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') ? 'https' : 'http';
$baseUrl = $baseProto . '://' . ($_SERVER['HTTP_HOST'] ?? 'localhost') . '/';

$isVisibleSelect = v2_prompt_select_column_expr($conn, 'p', 'is_visible_in_general_feed');
$creditEnabledSelect = v2_prompt_select_column_expr($conn, 'p', 'credit_unlock_enabled');
$rewardEnabledSelect = v2_prompt_select_column_expr($conn, 'p', 'reward_unlock_enabled');
$tokenEnabledSelect = v2_prompt_select_column_expr($conn, 'p', 'token_unlock_enabled');
$subscriberEnabledSelect = v2_prompt_select_column_expr($conn, 'p', 'subscriber_unlock_enabled');

$sql = " 
    SELECT
        p.id,
        p.title,
        p.short_description,
        p.prompt_text,
        p.image_url1,
        p.image_url2,
        COALESCE(p.likes,0) AS likes,
        COALESCE(p.favorites,0) AS favorites,
        COALESCE(p.copies,0) AS copies,
        COALESCE(p.views,0) AS views,
        p.is_popular,
        p.is_featured,
        p.status,
        p.priority,
        p.created_at,
        p.updated_at,
        p.tier,
        p.premium_unlock_cost_points,
        p.premium_pack,
        {$isVisibleSelect},
        {$creditEnabledSelect},
        {$rewardEnabledSelect},
        {$tokenEnabledSelect},
        {$subscriberEnabledSelect},
        p.tags,
        c.name AS category_name
    FROM premium_pack_items ppi
    INNER JOIN ai_posts p ON p.id = ppi.post_id
    LEFT JOIN categories c ON c.id = p.category_id
    WHERE ppi.pack_id = ?
      AND p.status = 'published'
    ORDER BY p.priority DESC, p.created_at DESC
";
$stmt = $conn->prepare($sql);
if (!$stmt) {
    json_err('Database query preparation failed', 500);
}
$stmt->bind_param('i', $packId);
$stmt->execute();
$res = $stmt->get_result();

$rows = [];
$postIds = [];
while ($row = ($res ? $res->fetch_assoc() : null)) {
    $rows[] = $row;
    $postIds[] = (int)($row['id'] ?? 0);
}
$stmt->close();

$postIds = array_values(array_unique(array_filter($postIds, static fn (int $id): bool => $id > 0)));
$unlockMap = ($userId !== null) ? v2_pack_prompt_entitlement_map($conn, (int)$userId, $postIds) : [];
$packLinksMap = v2_prompt_pack_links_for_posts($conn, $postIds, $userId !== null ? (int)$userId : null);

$items = [];
foreach ($rows as $row) {
    $postId = (int)($row['id'] ?? 0);
    if ($postId <= 0) {
        continue;
    }

    $packLinks = $packLinksMap[$postId] ?? [];
    $flags = v2_prompt_resolve_flags_from_row($row, true);

    $isUnlocked = v2_prompt_is_unlocked(
        $flags,
        isset($unlockMap[$postId]),
        $hasActiveSubscription
    );

    // If the user owns this pack, all contained prompts are unlocked.
    if ($ownsPack) {
        $isUnlocked = true;
    }

    $items[] = v2_prompt_build_payload(
        $row,
        $flags,
        $isUnlocked,
        $baseUrl,
        $packLinks
    );
}

json_ok([
    'success' => true,
    'pack' => [
        'id' => (int)$pack['id'],
        'name' => $pack['name'],
        'description' => $pack['description'],
        'thumbnailUrl' => v2_prompt_make_image_url($pack['thumbnail_path'] ?? null, $baseUrl),
        'pricePoints' => (int)$pack['price_points'],
        'itemCount' => (int)$pack['item_count'],
        'isActive' => (bool)$pack['is_active'],
        'createdAt' => $pack['created_at'],
        'ownsPack' => $ownsPack,
    ],
    'items' => $items,
]);
