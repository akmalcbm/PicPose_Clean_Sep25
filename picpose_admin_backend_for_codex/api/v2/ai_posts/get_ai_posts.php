<?php
require_once __DIR__ . '/../lib/v2_common.php';

function v2_make_image_url(?string $path, string $baseUrl): ?string
{
    if (empty($path)) {
        return null;
    }
    if (preg_match('#^https?://#i', $path)) {
        return $path;
    }
    return $baseUrl . ltrim($path, '/');
}

function v2_parse_tags(?string $tagsField): array
{
    if (empty($tagsField)) {
        return [];
    }

    $decoded = json_decode($tagsField, true);
    if (json_last_error() === JSON_ERROR_NONE && is_array($decoded)) {
        return array_values(array_filter(array_unique($decoded)));
    }

    return array_values(array_unique(array_filter(array_map('trim', explode(',', $tagsField)))));
}

function v2_first_words(?string $text, int $words = 15): string
{
    $clean = trim((string)$text);
    if ($clean === '') {
        return '';
    }

    $tokens = preg_split('/\s+/', $clean);
    if (!is_array($tokens)) {
        return '';
    }

    return implode(' ', array_slice($tokens, 0, max(1, $words)));
}

function v2_get_bearer_token_optional(): ?string
{
    $headers = function_exists('getallheaders') ? getallheaders() : [];
    $auth = $headers['Authorization']
        ?? $headers['authorization']
        ?? ($_SERVER['HTTP_AUTHORIZATION'] ?? null)
        ?? ($_SERVER['REDIRECT_HTTP_AUTHORIZATION'] ?? null);

    if (!is_string($auth) || $auth === '') {
        return null;
    }

    if (!preg_match('/^Bearer\s+(.+)$/i', trim($auth), $matches)) {
        return null;
    }

    $token = trim($matches[1]);
    return $token !== '' ? $token : null;
}

function v2_resolve_user_id_from_token(mysqli $conn): ?int
{
    $token = v2_get_bearer_token_optional();
    if ($token === null) {
        return null;
    }

    $stmt = $conn->prepare('SELECT id FROM users WHERE api_token = ? LIMIT 1');
    if (!$stmt) {
        return null;
    }

    $stmt->bind_param('s', $token);
    $stmt->execute();
    $result = $stmt->get_result();
    $row = $result ? $result->fetch_assoc() : null;
    $stmt->close();

    if (!$row) {
        return null;
    }

    return (int)$row['id'];
}

function v2_fetch_unlock_map(mysqli $conn, int $userId, array $postIds): array
{
    if (empty($postIds)) {
        return [];
    }

    $placeholders = implode(',', array_fill(0, count($postIds), '?'));
    $types = 'i' . str_repeat('i', count($postIds));
    $sql = "SELECT post_id FROM user_prompt_unlocks WHERE user_id = ? AND post_id IN ($placeholders)";
    $stmt = $conn->prepare($sql);
    if (!$stmt) {
        return [];
    }

    $params = array_merge([$userId], array_map('intval', $postIds));
    $stmt->bind_param($types, ...$params);
    $stmt->execute();
    $res = $stmt->get_result();

    $map = [];
    if ($res) {
        while ($r = $res->fetch_assoc()) {
            $map[(int)$r['post_id']] = true;
        }
    }
    $stmt->close();

    return $map;
}

$baseProto = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') ? 'https' : 'http';
$BASE_URL = $baseProto . '://' . ($_SERVER['HTTP_HOST'] ?? 'localhost') . '/';

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

$rawPosts = [];
$postIds = [];
while ($row = $result->fetch_assoc()) {
    $rawPosts[] = $row;
    $postIds[] = (int)$row['id'];
}
$stmt->close();

$authUserId = v2_resolve_user_id_from_token($conn);
$hasActiveSubscription = false;
$unlockMap = $authUserId ? v2_fetch_unlock_map($conn, $authUserId, $postIds) : [];

$posts = [];
foreach ($rawPosts as $row) {
    $tier = strtoupper((string)($row['tier'] ?? 'FREE'));
    $isPremium = ($tier === 'PREMIUM');

    $isUnlocked = !$isPremium;
    if ($isPremium && $authUserId) {
        $isUnlocked = isset($unlockMap[(int)$row['id']]) || $hasActiveSubscription;
    }

    $isLocked = !$isUnlocked;
    $promptText = (string)($row['prompt_text'] ?? '');

    $posts[] = [
        'id' => (string)$row['id'],
        'title' => $row['title'],
        'shortPrompt' => $row['short_description'],
        'fullPrompt' => $isLocked ? null : $promptText,
        'imageUrl' => v2_make_image_url($row['image_url1'], $BASE_URL),
        'imageUrl2' => v2_make_image_url($row['image_url2'], $BASE_URL),
        'category' => $row['category_name'],
        'tags' => v2_parse_tags($row['tags']),
        'likes' => (int)$row['likes'],
        'favorites' => (int)$row['favorites'],
        'copies' => (int)$row['copies'],
        'views' => (int)$row['views'],
        'isPopular' => (bool)$row['is_popular'],
        'isFeatured' => (bool)$row['is_featured'],
        'status' => $row['status'],
        'priority' => (int)$row['priority'],
        'createdAt' => $row['created_at'],
        'updatedAt' => $row['updated_at'],
        'tier' => $tier,
        'premiumUnlockCostPoints' => (int)($row['premium_unlock_cost_points'] ?? 0),
        'premiumPack' => $row['premium_pack'],
        'isLocked' => $isLocked,
        'teaserText' => $isLocked ? v2_first_words($promptText, 15) : null,
    ];
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
