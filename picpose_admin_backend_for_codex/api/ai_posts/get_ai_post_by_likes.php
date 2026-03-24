<?php
// File: /api/ai_posts/get_ai_post_by_likes.php

header('Content-Type: application/json; charset=utf-8');
header("Access-Control-Allow-Origin: *");
header("Access-Control-Allow-Methods: GET");

require '../../config.php';

// 🔐 API KEY VALIDATION
$VALID_API_KEY = "7a6f3c27a1b6d5e8e4c8a2b3f9e6d1f47c5b8a9d3e7f2c6a4b9e3d1c5f8a7b2c";
if (!isset($_GET['api_key']) || $_GET['api_key'] !== $VALID_API_KEY) {
    http_response_code(403);
    echo json_encode(["success" => false, "message" => "Unauthorized"]);
    exit();
}

// 🔹 Pagination Parameters
$limit  = max(1, intval($_GET['limit'] ?? 20));
$offset = max(0, intval($_GET['offset'] ?? 0));

// 🔹 Construct base URL for images
$baseProto = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') ? 'https' : 'http';
$BASE_URL = $baseProto . '://' . $_SERVER['HTTP_HOST'] . '/';

function makeImageUrl($path, $base) {
    if (empty($path)) return null;
    if (preg_match('#^https?://#i', $path)) return $path;
    return $base . ltrim($path, '/');
}

function ai_posts_visibility_sql(mysqli $conn, string $alias = 'ai_posts'): string {
    static $hasColumn = null;
    if ($hasColumn === null) {
        $res = $conn->query("
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'ai_posts'
              AND column_name = 'is_visible_in_general_feed'
            LIMIT 1
        ");
        $hasColumn = (bool)($res && $res->fetch_assoc());
    }

    $legacyExpr = "CASE WHEN EXISTS(SELECT 1 FROM premium_pack_items ppi_vis WHERE ppi_vis.post_id = {$alias}.id) AND UPPER(COALESCE({$alias}.tier, 'FREE')) <> 'PREMIUM' THEN 0 ELSE 1 END";
    if ($hasColumn) {
        return "COALESCE({$alias}.is_visible_in_general_feed, {$legacyExpr})";
    }
    return $legacyExpr;
}

$visibilitySql = ai_posts_visibility_sql($conn, 'ai_posts');

// 🔹 Fetch posts sorted by likes DESC
$sql = "
    SELECT id, title, short_description, image_url1, tags, likes, favorites, created_at 
    FROM ai_posts
    WHERE status = 'published'
      AND {$visibilitySql} = 1
    ORDER BY likes DESC, favorites DESC, created_at DESC
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
    $tags = json_decode($row['tags'] ?? '[]', true) ?: [];
    $data[] = [
        'id'         => (string) $row['id'],
        'title'      => $row['title'],
        'shortPrompt'=> $row['short_description'] ?? '',
        'imageUrl'   => makeImageUrl($row['image_url1'] ?? '', $BASE_URL),
        'tags'       => $tags,
        'likes'      => (int) ($row['likes'] ?? 0),
        'favorites'  => (int) ($row['favorites'] ?? 0),
        'created_at' => $row['created_at']
    ];
}

$stmt->close();
echo json_encode([
    'success' => true,
    'type' => 'most_liked',
    'count' => count($data),
    'data' => $data
], JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT);
