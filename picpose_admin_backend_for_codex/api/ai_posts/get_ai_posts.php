<?php
header('Content-Type: application/json; charset=utf-8');
header("Access-Control-Allow-Origin: *");
header("Access-Control-Allow-Methods: GET");
header("Access-Control-Allow-Headers: Content-Type, Authorization");

require '../../config.php';

/* ================= API KEY ================= */
$VALID_API_KEY = "7a6f3c27a1b6d5e8e4c8a2b3f9e6d1f47c5b8a9d3e7f2c6a4b9e3d1c5f8a7b2c";
if (!isset($_GET['api_key']) || $_GET['api_key'] !== $VALID_API_KEY) {
    http_response_code(403);
    echo json_encode([
        "success" => false,
        "message" => "Unauthorized Access",
        "data" => [],
        "total" => 0
    ]);
    exit();
}

/* ================= HELPERS ================= */
$baseProto = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') ? 'https' : 'http';
$BASE_URL = $baseProto . '://' . $_SERVER['HTTP_HOST'] . '/';

function makeImageUrl($path, $BASE_URL) {
    if (empty($path)) return null;
    if (preg_match('#^https?://#i', $path)) return $path;
    return $BASE_URL . ltrim($path, '/');
}

function parseTags($tagsField) {
    if (empty($tagsField)) return [];
    $decoded = json_decode($tagsField, true);
    if (json_last_error() === JSON_ERROR_NONE && is_array($decoded)) {
        return array_values(array_filter(array_unique($decoded)));
    }
    return array_values(array_unique(array_filter(array_map('trim', explode(',', $tagsField)))));
}

function aiPostsHasGeneralVisibility(mysqli $conn): bool
{
    static $checked = null;
    if ($checked !== null) {
        return $checked;
    }

    $sql = "
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'ai_posts'
          AND column_name = 'is_visible_in_general_feed'
        LIMIT 1
    ";
    $res = $conn->query($sql);
    $checked = (bool)($res && $res->fetch_assoc());
    return $checked;
}

function aiPostsGeneralVisibilitySql(mysqli $conn, string $alias = 'p'): string
{
    $legacy = "CASE WHEN EXISTS(SELECT 1 FROM premium_pack_items ppi_vis WHERE ppi_vis.post_id = {$alias}.id) AND UPPER(COALESCE({$alias}.tier, 'FREE')) <> 'PREMIUM' THEN 0 ELSE 1 END";
    if (aiPostsHasGeneralVisibility($conn)) {
        return "COALESCE({$alias}.is_visible_in_general_feed, {$legacy})";
    }
    return $legacy;
}

/* ================= QUERY PARAMS ================= */
$limit  = isset($_GET['limit'])  ? max(1, intval($_GET['limit']))  : 20;
$offset = isset($_GET['offset']) ? max(0, intval($_GET['offset'])) : 0;

$status   = $_GET['status'] ?? 'published';
$search   = $_GET['q'] ?? null;
$tag      = $_GET['tag'] ?? null;

$categoryId   = $_GET['category_id'] ?? null; // ✅ NEW (preferred)
$categoryName = $_GET['category'] ?? null;    // 🔁 backward compatible

$popular  = isset($_GET['popular'])  && ($_GET['popular'] == '1'  || $_GET['popular'] === 'true');
$featured = isset($_GET['featured']) && ($_GET['featured'] == '1' || $_GET['featured'] === 'true');
$generalVisibilitySql = aiPostsGeneralVisibilitySql($conn, 'p');

/* ================= MAIN SQL ================= */
$sql = "
SELECT
    p.id,
    p.title,
    p.short_description,
    p.prompt_text,
    p.image_url1,
    p.image_url2,
    COALESCE(p.likes,0)      AS likes,
    COALESCE(p.favorites,0)  AS favorites,
    COALESCE(p.copies,0)     AS copies,
    COALESCE(p.views,0)      AS views,
    p.is_popular,
    p.is_featured,
    p.status,
    p.priority,
    p.created_at,
    p.updated_at,
    COALESCE(c.name,'Uncategorized') AS category_name,
    p.tags
FROM ai_posts p
LEFT JOIN categories c ON c.id = p.category_id
WHERE p.status = ?
  AND {$generalVisibilitySql} = 1
";

$params = [$status];
$types  = "s";

/* ================= FILTERS ================= */

// Category (ID first)
if (!empty($categoryId)) {
    $sql .= " AND p.category_id = ?";
    $params[] = intval($categoryId);
    $types   .= "i";
} elseif (!empty($categoryName)) {
    $sql .= " AND c.name = ?";
    $params[] = $categoryName;
    $types   .= "s";
}

// Flags
if ($popular)  $sql .= " AND p.is_popular = 1";
if ($featured) $sql .= " AND p.is_featured = 1";

// Tag
if (!empty($tag)) {
    $sql .= " AND p.tags LIKE CONCAT('%',?,'%')";
    $params[] = $tag;
    $types   .= "s";
}

// Search
if (!empty($search)) {
    if (ctype_digit($search)) {
        $sql .= " AND p.id = ?";
        $params[] = intval($search);
        $types   .= "i";
    } else {
        $like = "%$search%";
        $sql .= " AND (
            p.title LIKE ?
            OR p.short_description LIKE ?
            OR p.prompt_text LIKE ?
            OR p.tags LIKE ?
        )";
        array_push($params, $like, $like, $like, $like);
        $types .= "ssss";
    }
}

/* ================= ORDER & PAGINATION ================= */
$sql .= " ORDER BY p.priority DESC, p.created_at DESC LIMIT ? OFFSET ?";
$params[] = $limit;
$params[] = $offset;
$types   .= "ii";

/* ================= EXECUTE ================= */
$stmt = $conn->prepare($sql);
if (!$stmt) {
    http_response_code(500);
    echo json_encode([
        "success" => false,
        "message" => "DB Error: " . $conn->error,
        "data" => [],
        "total" => 0
    ]);
    exit();
}

$stmt->bind_param($types, ...$params);
$stmt->execute();
$result = $stmt->get_result();

/* ================= BUILD RESPONSE ================= */
$posts = [];
while ($row = $result->fetch_assoc()) {
    $posts[] = [
        "id"          => (string)$row['id'],
        "title"       => $row['title'],
        "shortPrompt" => $row['short_description'],
        "fullPrompt"  => $row['prompt_text'],
        "imageUrl"    => makeImageUrl($row['image_url1'], $BASE_URL),
        "imageUrl2"   => makeImageUrl($row['image_url2'], $BASE_URL),
        "category"    => $row['category_name'],
        "tags"        => parseTags($row['tags']),
        "likes"       => (int)$row['likes'],
        "favorites"   => (int)$row['favorites'],
        "copies"      => (int)$row['copies'],
        "views"       => (int)$row['views'],
        "isPopular"   => (bool)$row['is_popular'],
        "isFeatured"  => (bool)$row['is_featured'],
        "status"      => $row['status'],
        "priority"    => (int)$row['priority'],
        "createdAt"   => $row['created_at'],
        "updatedAt"   => $row['updated_at']
    ];
}
$stmt->close();

/* ================= COUNT QUERY (SYNCED) ================= */
$countSql = "
SELECT COUNT(*) AS total
FROM ai_posts p
LEFT JOIN categories c ON c.id = p.category_id
WHERE p.status = ?
  AND {$generalVisibilitySql} = 1
";

$countParams = [$status];
$countTypes  = "s";

if (!empty($categoryId)) {
    $countSql .= " AND p.category_id = ?";
    $countParams[] = intval($categoryId);
    $countTypes   .= "i";
} elseif (!empty($categoryName)) {
    $countSql .= " AND c.name = ?";
    $countParams[] = $categoryName;
    $countTypes   .= "s";
}

if ($popular)  $countSql .= " AND p.is_popular = 1";
if ($featured) $countSql .= " AND p.is_featured = 1";

if (!empty($tag)) {
    $countSql .= " AND p.tags LIKE CONCAT('%',?,'%')";
    $countParams[] = $tag;
    $countTypes   .= "s";
}

if (!empty($search)) {
    if (ctype_digit($search)) {
        $countSql .= " AND p.id = ?";
        $countParams[] = intval($search);
        $countTypes   .= "i";
    } else {
        $like = "%$search%";
        $countSql .= " AND (
            p.title LIKE ?
            OR p.short_description LIKE ?
            OR p.prompt_text LIKE ?
            OR p.tags LIKE ?
        )";
        array_push($countParams, $like, $like, $like, $like);
        $countTypes .= "ssss";
    }
}

$countStmt = $conn->prepare($countSql);
$countStmt->bind_param($countTypes, ...$countParams);
$countStmt->execute();
$countRes   = $countStmt->get_result();
$totalRow  = $countRes->fetch_assoc();
$totalCount = (int)$totalRow['total'];
$countStmt->close();

/* ================= FINAL OUTPUT ================= */
echo json_encode([
    "success" => true,
    "message" => "OK",
    "total"   => $totalCount,
    "limit"   => $limit,
    "offset"  => $offset,
    "hasMore" => ($offset + $limit) < $totalCount,
    "data"    => $posts
], JSON_UNESCAPED_UNICODE);

exit();
