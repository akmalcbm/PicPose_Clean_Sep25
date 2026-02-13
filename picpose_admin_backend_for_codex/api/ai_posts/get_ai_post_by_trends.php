<?php
// /api/ai_posts/get_ai_post_by_trends.php
header('Content-Type: application/json; charset=utf-8');
header("Access-Control-Allow-Origin: *");
require '../../config.php';

// 🔐 Validate API Key
$VALID_API_KEY = "7a6f3c27a1b6d5e8e4c8a2b3f9e6d1f47c5b8a9d3e7f2c6a4b9e3d1c5f8a7b2c";
if (!isset($_GET['api_key']) || $_GET['api_key'] !== $VALID_API_KEY) {
    http_response_code(403);
    echo json_encode(["success" => false, "message" => "Unauthorized"]);
    exit();
}

// 📊 Parameters
$limit  = max(1, intval($_GET['limit'] ?? 20));
$offset = max(0, intval($_GET['offset'] ?? 0));

// 🔗 Base URL for images
$baseProto = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') ? 'https' : 'http';
$BASE_URL = $baseProto . '://' . $_SERVER['HTTP_HOST'] . '/';

function makeImageUrl($path, $base) {
    if (empty($path)) return null;
    if (preg_match('#^https?://#i', $path)) return $path;
    return $base . ltrim($path, '/');
}

// 🧮 TRENDING LOGIC
// Uses weighted score: (likes * 3) + (favorites * 5) + (views * 1)
// Prioritizes recent posts within last 30 days
$sql = "
    SELECT 
        p.id,
        p.title,
        p.short_description,
        p.image_url1,
        p.tags,
        p.likes,
        p.favorites,
        p.views,
        p.created_at,
        c.name AS category_name,
        ((COALESCE(p.likes,0) * 3) + (COALESCE(p.favorites,0) * 5) + (COALESCE(p.views,0) * 1)) 
            AS trending_score
    FROM ai_posts p
    LEFT JOIN categories c ON c.id = p.category_id
    WHERE p.status = 'published'
      AND p.created_at >= DATE_SUB(NOW(), INTERVAL 30 DAY)
    ORDER BY trending_score DESC, p.created_at DESC
    LIMIT ? OFFSET ?;
";

$stmt = $conn->prepare($sql);
if (!$stmt) {
    http_response_code(500);
    echo json_encode(['success' => false, 'message' => 'Database prepare failed']);
    exit();
}
$stmt->bind_param('ii', $limit, $offset);
$stmt->execute();
$result = $stmt->get_result();

$data = [];
while ($row = $result->fetch_assoc()) {
    $data[] = [
        'id'          => (string) $row['id'],
        'title'       => $row['title'],
        'shortPrompt' => $row['short_description'] ?? '',
        'imageUrl'    => makeImageUrl($row['image_url1'] ?? '', $BASE_URL),
        'tags'        => json_decode($row['tags'] ?? '[]', true) ?: [],
        'likes'       => (int) ($row['likes'] ?? 0),
        'favorites'   => (int) ($row['favorites'] ?? 0),
        'views'       => (int) ($row['views'] ?? 0),
        'category'    => $row['category_name'] ?? '',
        'trending_score' => (int) $row['trending_score'],
        'created_at'  => $row['created_at']
    ];
}

$stmt->close();

// 🟢 Response
echo json_encode([
    'success' => true,
    'message' => 'Trending posts ranked by engagement score',
    'count' => count($data),
    'data' => $data
], JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT);
exit();
?>
