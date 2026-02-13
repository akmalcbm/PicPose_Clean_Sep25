<?php
header('Content-Type: application/json; charset=utf-8');
header("Access-Control-Allow-Origin: *");
header("Access-Control-Allow-Methods: GET");
header("Access-Control-Allow-Headers: Content-Type, Authorization");

require '../../config.php';

// === Validate API Key ===
$VALID_API_KEY = "7a6f3c27a1b6d5e8e4c8a2b3f9e6d1f47c5b8a9d3e7f2c6a4b9e3d1c5f8a7b2c";
if (!isset($_GET['api_key']) || $_GET['api_key'] !== $VALID_API_KEY) {
    http_response_code(403);
    echo json_encode(["success" => false, "message" => "Unauthorized Access", "data" => null]);
    exit();
}

// === Validate ID ===
if (!isset($_GET['id']) || !ctype_digit($_GET['id'])) {
    echo json_encode(["success" => false, "message" => "Invalid Prompt ID", "data" => null]);
    exit();
}

$promptId = intval($_GET['id']);

$baseProto = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') ? 'https' : 'http';
$BASE_URL = $baseProto . '://' . $_SERVER['HTTP_HOST'] . '/';

function makeImageUrl($path, $BASE_URL) {
    if (!$path) return null;
    if (preg_match('#^https?://#i', $path)) return $path;
    return $BASE_URL . ltrim($path, '/');
}

function parseTags($tags) {
    if (empty($tags)) return [];
    $decoded = json_decode($tags, true);
    if (json_last_error() === JSON_ERROR_NONE && is_array($decoded)) {
        return array_values(array_unique(array_filter($decoded)));
    }
    return array_values(array_unique(array_filter(array_map('trim', explode(',', $tags)))));
}

// === SQL ===
$sql = "SELECT 
            p.id, p.title, p.short_description, p.prompt_text,
            p.image_url1, p.image_url2,
            COALESCE(p.likes,0) AS likes,
            COALESCE(p.favorites,0) AS favorites,
            COALESCE(p.copies,0) AS copies,
            COALESCE(p.views,0) AS views,
            p.is_popular, p.is_featured, p.status, p.priority,
            p.created_at, p.updated_at,
            c.name AS category_name,
            p.tags
        FROM ai_posts p
        LEFT JOIN categories c ON c.id = p.category_id
        WHERE p.id = ? AND p.status = 'published'
        LIMIT 1";

$stmt = $conn->prepare($sql);
$stmt->bind_param("i", $promptId);
$stmt->execute();
$result = $stmt->get_result();

if ($row = $result->fetch_assoc()) {

    $data = [
        "id" => (string)$row['id'],
        "title" => $row['title'],
        "shortPrompt" => $row['short_description'],
        "fullPrompt" => $row['prompt_text'],
        "imageUrl" => makeImageUrl($row['image_url1'], $BASE_URL),
        "imageUrl2" => makeImageUrl($row['image_url2'], $BASE_URL),
        "category" => $row['category_name'],
        "tags" => parseTags($row['tags']),
        "likes" => intval($row['likes']),
        "favorites" => intval($row['favorites']),
        "copies" => intval($row['copies']),
        "views" => intval($row['views']),
        "isPopular" => boolval($row['is_popular']),
        "isFeatured" => boolval($row['is_featured']),
        "status" => $row['status'],
        "priority" => intval($row['priority']),
        "createdAt" => $row['created_at'],
        "updatedAt" => $row['updated_at']
    ];

    echo json_encode(["success" => true, "message" => "OK", "data" => $data]);
} else {
    http_response_code(404);
    echo json_encode(["success" => false, "message" => "Prompt Not Found", "data" => null]);
}

$stmt->close();
exit();
