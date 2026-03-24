<?php
require_once __DIR__ . '/../lib/v2_common.php';
require_once __DIR__ . '/../lib/v2_pack_entitlements.php';
require_once __DIR__ . '/../lib/v2_prompt_access.php';

$baseProto = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') ? 'https' : 'http';
$baseUrl = $baseProto . '://' . ($_SERVER['HTTP_HOST'] ?? 'localhost') . '/';

$limit = isset($_GET['limit']) ? max(1, intval($_GET['limit'])) : 20;
$offset = isset($_GET['offset']) ? max(0, intval($_GET['offset'])) : 0;

$status = $_GET['status'] ?? 'published';
$search = $_GET['q'] ?? null;
$tag = $_GET['tag'] ?? null;
$categoryId = $_GET['category_id'] ?? null;
$categoryName = $_GET['category'] ?? null;
$tierFilter = isset($_GET['tier']) ? strtoupper(trim((string)$_GET['tier'])) : null;
$tierFilter = in_array($tierFilter, ['FREE', 'PREMIUM'], true) ? $tierFilter : null;
$popular = isset($_GET['popular']) && ($_GET['popular'] == '1' || $_GET['popular'] === 'true');
$featured = isset($_GET['featured']) && ($_GET['featured'] == '1' || $_GET['featured'] === 'true');
$includeHidden = isset($_GET['include_hidden']) && ($_GET['include_hidden'] == '1' || $_GET['include_hidden'] === 'true');

$visibilityExpr = v2_prompt_is_visible_expression_sql('p', $conn);
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
    COALESCE(p.likes, 0) AS likes,
    COALESCE(p.favorites, 0) AS favorites,
    COALESCE(p.copies, 0) AS copies,
    COALESCE(p.views, 0) AS views,
    p.is_popular,
    p.is_featured,
    p.tier,
    p.premium_unlock_cost_points,
    p.premium_pack,
    {$isVisibleSelect},
    {$creditEnabledSelect},
    {$rewardEnabledSelect},
    {$tokenEnabledSelect},
    {$subscriberEnabledSelect},
    p.status,
    p.priority,
    p.created_at,
    p.updated_at,
    COALESCE(c.name, 'Uncategorized') AS category_name,
    p.tags
FROM ai_posts p
LEFT JOIN categories c ON c.id = p.category_id
WHERE p.status = ?
";

$params = [$status];
$types = 's';

if (!empty($categoryId)) {
    $sql .= ' AND p.category_id = ?';
    $params[] = intval($categoryId);
    $types .= 'i';
} elseif (!empty($categoryName)) {
    $sql .= ' AND c.name = ?';
    $params[] = $categoryName;
    $types .= 's';
}

if (!empty($tierFilter)) {
    $sql .= ' AND UPPER(COALESCE(p.tier, \'FREE\')) = ?';
    $params[] = $tierFilter;
    $types .= 's';
}

if ($popular) {
    $sql .= ' AND p.is_popular = 1';
}
if ($featured) {
    $sql .= ' AND p.is_featured = 1';
}

if (!empty($tag)) {
    $sql .= " AND p.tags LIKE CONCAT('%', ?, '%')";
    $params[] = $tag;
    $types .= 's';
}

if (!empty($search)) {
    if (ctype_digit((string)$search)) {
        $sql .= ' AND p.id = ?';
        $params[] = intval($search);
        $types .= 'i';
    } else {
        $like = '%' . $search . '%';
        $sql .= " AND (
            p.title LIKE ?
            OR p.short_description LIKE ?
            OR p.prompt_text LIKE ?
            OR p.tags LIKE ?
        )";
        array_push($params, $like, $like, $like, $like);
        $types .= 'ssss';
    }
}

if (!$includeHidden) {
    $sql .= " AND {$visibilityExpr} = 1";
}

$sql .= ' ORDER BY p.priority DESC, p.created_at DESC LIMIT ? OFFSET ?';
$params[] = $limit;
$params[] = $offset;
$types .= 'ii';

$stmt = $conn->prepare($sql);
if (!$stmt) {
    json_err('DB Error: ' . $conn->error, 500);
}

$stmt->bind_param($types, ...$params);
$stmt->execute();
$result = $stmt->get_result();

$rows = [];
$postIds = [];
while ($row = $result->fetch_assoc()) {
    $rows[] = $row;
    $postIds[] = (int)$row['id'];
}
$stmt->close();

$userProfile = v2_prompt_optional_user_profile($conn);
$authUserId = $userProfile['id'] ?? null;
$hasActiveSubscription = (bool)($userProfile['has_active_subscription'] ?? false);

$unlockMap = ($authUserId !== null)
    ? v2_pack_prompt_entitlement_map($conn, (int)$authUserId, $postIds)
    : [];
$packLinksMap = v2_prompt_pack_links_for_posts($conn, $postIds, $authUserId !== null ? (int)$authUserId : null);

$posts = [];
foreach ($rows as $row) {
    $postId = (int)$row['id'];
    $packLinks = $packLinksMap[$postId] ?? [];
    $flags = v2_prompt_resolve_flags_from_row($row, !empty($packLinks));

    if (!$includeHidden && !($flags['is_visible_in_general_feed'] ?? true)) {
        continue;
    }

    $isUnlocked = v2_prompt_is_unlocked(
        $flags,
        isset($unlockMap[$postId]),
        $hasActiveSubscription
    );

    $posts[] = v2_prompt_build_payload(
        $row,
        $flags,
        $isUnlocked,
        $baseUrl,
        $packLinks
    );
}

$countSql = "
SELECT COUNT(*) AS total
FROM ai_posts p
LEFT JOIN categories c ON c.id = p.category_id
WHERE p.status = ?
";
$countParams = [$status];
$countTypes = 's';

if (!empty($categoryId)) {
    $countSql .= ' AND p.category_id = ?';
    $countParams[] = intval($categoryId);
    $countTypes .= 'i';
} elseif (!empty($categoryName)) {
    $countSql .= ' AND c.name = ?';
    $countParams[] = $categoryName;
    $countTypes .= 's';
}

if (!empty($tierFilter)) {
    $countSql .= ' AND UPPER(COALESCE(p.tier, \'FREE\')) = ?';
    $countParams[] = $tierFilter;
    $countTypes .= 's';
}

if ($popular) {
    $countSql .= ' AND p.is_popular = 1';
}
if ($featured) {
    $countSql .= ' AND p.is_featured = 1';
}

if (!empty($tag)) {
    $countSql .= " AND p.tags LIKE CONCAT('%', ?, '%')";
    $countParams[] = $tag;
    $countTypes .= 's';
}

if (!empty($search)) {
    if (ctype_digit((string)$search)) {
        $countSql .= ' AND p.id = ?';
        $countParams[] = intval($search);
        $countTypes .= 'i';
    } else {
        $like = '%' . $search . '%';
        $countSql .= " AND (
            p.title LIKE ?
            OR p.short_description LIKE ?
            OR p.prompt_text LIKE ?
            OR p.tags LIKE ?
        )";
        array_push($countParams, $like, $like, $like, $like);
        $countTypes .= 'ssss';
    }
}

if (!$includeHidden) {
    $countSql .= " AND {$visibilityExpr} = 1";
}

$countStmt = $conn->prepare($countSql);
if (!$countStmt) {
    json_err('DB Error: ' . $conn->error, 500);
}

$countStmt->bind_param($countTypes, ...$countParams);
$countStmt->execute();
$countRes = $countStmt->get_result();
$totalRow = $countRes ? $countRes->fetch_assoc() : ['total' => 0];
$totalCount = (int)($totalRow['total'] ?? 0);
$countStmt->close();

json_ok([
    'success' => true,
    'message' => 'OK',
    'total' => $totalCount,
    'limit' => $limit,
    'offset' => $offset,
    'hasMore' => ($offset + $limit) < $totalCount,
    'data' => $posts,
]);
