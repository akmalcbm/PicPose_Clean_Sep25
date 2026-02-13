<?php
// api/ai_posts/get_categories.php
header('Content-Type: application/json; charset=utf-8');
header("Access-Control-Allow-Origin: *");
header("Access-Control-Allow-Methods: GET");

require '../../config.php';

$VALID_API_KEY = "7a6f3c27a1b6d5e8e4c8a2b3f9e6d1f47c5b8a9d3e7f2c6a4b9e3d1c5f8a7b2c";
if (!isset($_GET['api_key']) || $_GET['api_key'] !== $VALID_API_KEY) {
    http_response_code(403);
    echo json_encode(["success" => false, "message" => "Unauthorized"]);
    exit();
}

// ✅ Optional: enable detailed error output for debugging (remove in production)
ini_set('display_errors', 1);
error_reporting(E_ALL);

$baseProto = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') ? 'https' : 'http';
$BASE_URL = $baseProto . '://' . $_SERVER['HTTP_HOST'] . '/';

function makeImageUrl($path, $BASE_URL) {
    if (empty($path)) return null;
    if (preg_match('#^https?://#i', $path)) return $path;
    return $BASE_URL . ltrim($path, '/');
}

// ✅ Step 1: Fetch all categories
$sql = "SELECT id, name, slug, parent_id, image_path, icon_path 
        FROM categories 
        ORDER BY parent_id ASC, name ASC";
$res = $conn->query($sql);

if (!$res) {
    echo json_encode([
        "success" => false,
        "message" => "Database error fetching categories: " . $conn->error
    ]);
    exit();
}

$rows = $res->fetch_all(MYSQLI_ASSOC);

// ✅ Step 2: Fetch total post counts from ai_posts
$postCounts = [];
$postSql = "SELECT category_id AS cat_id, COUNT(*) AS total 
            FROM ai_posts 
            GROUP BY category_id";
$postRes = $conn->query($postSql);

if ($postRes) {
    while ($p = $postRes->fetch_assoc()) {
        if (!empty($p['cat_id'])) {
            $postCounts[$p['cat_id']] = (int)$p['total'];
        }
    }
}

// ✅ Step 3: Build category map (add post_count + full URLs)
$map = [];
foreach ($rows as $r) {
    $id = $r['id'];
    $r['image_url'] = makeImageUrl($r['image_path'] ?? '', $BASE_URL);
    $r['icon_url'] = makeImageUrl($r['icon_path'] ?? '', $BASE_URL);
    unset($r['image_path'], $r['icon_path']);
    $r['children'] = [];
    $r['post_count'] = isset($postCounts[$id]) ? $postCounts[$id] : 0;
    $map[$id] = $r;
}

// ✅ Step 4: Build hierarchical category tree
$tree = [];
foreach ($map as $id => $node) {
    $parentId = $node['parent_id'];
    if (!empty($parentId) && isset($map[$parentId])) {
        $map[$parentId]['children'][] = &$map[$id];
    } else {
        $tree[] = &$map[$id];
    }
}

// ✅ Step 5: Return as JSON
echo json_encode([
    'success' => true,
    'data' => $tree
], JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT);
