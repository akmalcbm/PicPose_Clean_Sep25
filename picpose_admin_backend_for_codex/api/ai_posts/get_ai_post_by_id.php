<?php
// /api/ai_posts/get_ai_post_by_id.php

header('Content-Type: application/json; charset=utf-8');
header("Access-Control-Allow-Origin: *");
header("Access-Control-Allow-Methods: GET");

require '../../config.php';

/* -------------------------
   🔐 API KEY VALIDATION
------------------------- */
$VALID_API_KEY = "7a6f3c27a1b6d5e8e4c8a2b3f9e6d1f47c5b8a9d3e7f2c6a4b9e3d1c5f8a7b2c";

if (!isset($_GET['api_key']) || $_GET['api_key'] !== $VALID_API_KEY) {
    http_response_code(403);
    echo json_encode([
        "success" => false,
        "message" => "Unauthorized",
        "data" => null
    ]);
    exit();
}

/* -------------------------
   🔎 INPUT VALIDATION
------------------------- */
$id = intval($_GET['id'] ?? 0);
$status = $_GET['status'] ?? 'published';

if ($id <= 0) {
    http_response_code(400);
    echo json_encode([
        "success" => false,
        "message" => "Invalid or missing post ID",
        "data" => null
    ]);
    exit();
}

/* -------------------------
   🌐 BASE URL FOR IMAGES
------------------------- */
$baseProto = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') ? 'https' : 'http';
$BASE_URL = $baseProto . '://' . $_SERVER['HTTP_HOST'] . '/';

function makeImageUrl($path, $base) {
    if (empty($path)) return null;
    if (preg_match('#^https?://#i', $path)) return $path;
    return $base . ltrim($path, '/');
}

/* -------------------------
   🏷 TAG PARSER (same as category API)
------------------------- */
function parseTags($conn, $tagsField) {
    $tags = [];
    if (empty($tagsField)) return $tags;

    $decoded = json_decode($tagsField, true);
    if (json_last_error() === JSON_ERROR_NONE && is_array($decoded)) {
        foreach ($decoded as $t) {
            $t = trim($t);
            if ($t !== '') $tags[] = $t;
        }
        return array_values(array_unique($tags));
    }

    $parts = array_map('trim', explode(',', $tagsField));
    $textTags = [];
    $numericIds = [];

    foreach ($parts as $p) {
        if ($p === '') continue;
        if (is_numeric($p)) $numericIds[] = (int)$p;
        else $textTags[] = $p;
    }

    if (!empty($numericIds)) {
        $placeholders = implode(',', array_fill(0, count($numericIds), '?'));
        $types = str_repeat('i', count($numericIds));

        $sql = "SELECT name FROM categories WHERE id IN ($placeholders)";
        $stmt = $conn->prepare($sql);
        if ($stmt) {
            $bindArgs = [];
            $bindArgs[] = &$types;
            for ($i = 0; $i < count($numericIds); $i++) {
                $bindArgs[] = &$numericIds[$i];
            }
            call_user_func_array([$stmt, 'bind_param'], $bindArgs);
            $stmt->execute();
            $res = $stmt->get_result();
            while ($r = $res->fetch_assoc()) {
                $tags[] = $r['name'];
            }
            $stmt->close();
        }
    }

    $tags = array_merge($tags, $textTags);
    return array_values(array_unique(array_filter(array_map('trim', $tags))));
}

/* -------------------------
   🧠 MAIN QUERY
------------------------- */
$sql = "
    SELECT 
        p.*,
        c.id   AS category_id,
        c.name AS category_name,
        c.image_path AS category_image_path
    FROM ai_posts p
    LEFT JOIN categories c ON c.id = p.category_id
    WHERE p.id = ?
      AND p.status = ?
    LIMIT 1
";

$stmt = $conn->prepare($sql);
if (!$stmt) {
    http_response_code(500);
    echo json_encode([
        "success" => false,
        "message" => "Database prepare failed",
        "data" => null
    ]);
    exit();
}

$stmt->bind_param('is', $id, $status);
$stmt->execute();
$result = $stmt->get_result();

if (!$row = $result->fetch_assoc()) {
    http_response_code(404);
    echo json_encode([
        "success" => false,
        "message" => "Post not found",
        "data" => null
    ]);
    $stmt->close();
    exit();
}

/* -------------------------
   📦 FINAL RESPONSE OBJECT
------------------------- */
$data = [
    'id'                => (string)$row['id'],
    'title'             => $row['title'],
    'shortPrompt'       => $row['short_description'] ?? '',
    'fullPrompt'        => $row['prompt_text'] ?? '',
    'imageUrl'          => makeImageUrl($row['image_url1'] ?? '', $BASE_URL),

    'categoryId'        => isset($row['category_id']) ? (int)$row['category_id'] : null,
    'category'          => $row['category_name'] ?? '',
    'categoryImageUrl'  => makeImageUrl($row['category_image_path'] ?? '', $BASE_URL),

    'tags'              => parseTags($conn, $row['tags'] ?? ''),

    'likes'             => (int)($row['likes'] ?? 0),
    'favorites'         => (int)($row['favorites'] ?? 0),
    'views'             => (int)($row['views'] ?? 0),

    'isPopular'         => !empty($row['is_popular']),
    'isFeatured'        => !empty($row['is_featured']),

    'created_at'        => $row['created_at']
];

$stmt->close();

/* -------------------------
   🟢 SUCCESS RESPONSE
------------------------- */
echo json_encode([
    "success" => true,
    "data" => $data
], JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT);
exit();
