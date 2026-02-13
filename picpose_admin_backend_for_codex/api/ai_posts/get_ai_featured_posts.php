<?php
header('Content-Type: application/json; charset=utf-8');
header("Access-Control-Allow-Origin: *");
require '../../config.php';

$api_key = $_GET['api_key'] ?? '';
if ($api_key !== '7a6f3c27a1b6d5e8e4c8a2b3f9e6d1f47c5b8a9d3e7f2c6a4b9e3d1c5f8a7b2c') {
    echo json_encode(["success" => false, "message" => "Unauthorized Access"]);
    exit;
}

$limit = intval($_GET['limit'] ?? 20);

$sql = "SELECT * FROM ai_posts 
        WHERE is_featured = 1 AND status = 'published'
        ORDER BY created_at DESC
        LIMIT ?";
$stmt = $conn->prepare($sql);
$stmt->bind_param("i", $limit);
$stmt->execute();
$result = $stmt->get_result();

$posts = [];
while ($row = $result->fetch_assoc()) {
    $posts[] = $row;
}

echo json_encode(["success" => true, "message" => "OK", "data" => $posts], JSON_UNESCAPED_UNICODE);
exit;
?>
