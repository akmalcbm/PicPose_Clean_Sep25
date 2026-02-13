<?php
header('Content-Type: application/json; charset=utf-8');
header("Access-Control-Allow-Origin: *");
header("Access-Control-Allow-Methods: GET");
require '../../config.php';

// API key check (same as other endpoints)
$VALID_API_KEY = "7a6f3c27a1b6d5e8e4c8a2b3f9e6d1f47c5b8a9d3e7f2c6a4b9e3d1c5f8a7b2c";
if (!isset($_GET['api_key']) || $_GET['api_key'] !== $VALID_API_KEY) {
    http_response_code(403);
    echo json_encode(["success" => false, "message" => "Unauthorized Access", "data" => []]);
    exit();
}

$id = isset($_GET['id']) ? intval($_GET['id']) : 0;
if (!$id) {
    http_response_code(400);
    echo json_encode(["success" => false, "message" => "missing id", "data" => []]);
    exit();
}

// build absolute base url
$baseProto = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') ? 'https' : 'http';
$BASE_URL = $baseProto . '://' . $_SERVER['HTTP_HOST'] . '/';

$sql = "SELECT p.*, c.name as category_name
        FROM ai_posts p
        LEFT JOIN categories c ON c.id = p.category_id
        WHERE p.id = ? LIMIT 1";
$stmt = $conn->prepare($sql);
if (!$stmt) {
    http_response_code(500);
    echo json_encode(["success" => false, "message" => "prepare_failed", "sql_error" => $conn->error]);
    exit();
}
$stmt->bind_param('i', $id);
$stmt->execute();
$res = $stmt->get_result();
$post = $res->fetch_assoc();
$stmt->close();

if (!$post) {
    http_response_code(404);
    echo json_encode(["success" => false, "message" => "not_found", "data" => []]);
    exit();
}

// parse tags (reuse logic similar to parseTags in previous file)
function parseTagsInline($conn, $tagsField) {
    $tags = [];
    if (empty($tagsField)) return $tags;
    $decoded = json_decode($tagsField, true);
    if (json_last_error() === JSON_ERROR_NONE && is_array($decoded)) {
        foreach ($decoded as $t) { if (($t = trim($t)) !== '') $tags[] = $t; }
        return array_values(array_unique($tags));
    }
    $parts = array_map('trim', explode(',', $tagsField));
    $textTags = []; $numericIds = [];
    foreach ($parts as $p) {
        if ($p === '') continue;
        if (is_numeric($p)) $numericIds[] = (int)$p; else $textTags[] = $p;
    }
    if (!empty($numericIds)) {
        $placeholders = implode(',', array_fill(0, count($numericIds), '?'));
        $types = str_repeat('i', count($numericIds));
        $sql = "SELECT name FROM categories WHERE id IN ($placeholders)";
        $stmt = $conn->prepare($sql);
        if ($stmt) {
            $bindArgs = []; $bindArgs[] = &$types;
            for ($i=0;$i<count($numericIds);$i++) $bindArgs[] = &$numericIds[$i];
            call_user_func_array([$stmt,'bind_param'],$bindArgs);
            $stmt->execute(); $rres = $stmt->get_result();
            while ($r = $rres->fetch_assoc()) $tags[] = $r['name'];
            $stmt->close();
        }
    }
    $tags = array_merge($tags, $textTags);
    return array_values(array_unique(array_filter(array_map('trim', $tags))));
}

$tagsArray = parseTagsInline($conn, $post['tags'] ?? '');

function makeImage($path) {
    global $BASE_URL;
    if (empty($path)) return null;
    if (preg_match('#^https?://#i', $path)) return $path;
    return $BASE_URL . ltrim($path, '/');
}

$response = [
    "success" => true,
    "data" => [
        "id" => (string)$post['id'],
        "title" => $post['title'],
        "shortPrompt" => $post['short_description'] ?? '',
        "fullPrompt" => $post['prompt_text'] ?? '',
        "imageUrl" => makeImage($post['image_url1'] ?? ''),
        "imageUrl2" => makeImage($post['image_url2'] ?? ''),
        "category" => $post['category_name'] ?? '',
        "tags" => $tagsArray,
        "likes" => (int)($post['likes'] ?? 0),
        "favorites" => (int)($post['favorites'] ?? 0),
        "isPopular" => !empty($post['is_popular']),
        "isFeatured" => !empty($post['is_featured']),
        "status" => $post['status'] ?? 'published',
        "createdAt" => $post['created_at'],
        "updatedAt" => $post['updated_at'] ?? null
    ]
];

echo json_encode($response, JSON_UNESCAPED_UNICODE|JSON_PRETTY_PRINT);
exit();
