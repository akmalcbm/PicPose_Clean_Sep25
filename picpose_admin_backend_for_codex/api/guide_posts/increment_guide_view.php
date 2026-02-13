<?php
header('Content-Type: application/json; charset=utf-8');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET, POST');

require_once __DIR__ . '/../../config.php';

$VALID_API_KEY = "7a6f3c27a1b6d5e8e4c8a2b3f9e6d1f47c5b8a9d3e7f2c6a4b9e3d1c5f8a7b2c";
$provided = $_GET['api_key'] ?? $_POST['api_key'] ?? '';
if (!hash_equals($VALID_API_KEY, (string)$provided)) {
    http_response_code(403);
    echo json_encode(["success" => false, "message" => "Unauthorized"]);
    exit();
}

$id = intval($_POST['id'] ?? $_GET['id'] ?? 0);
if ($id <= 0) {
    http_response_code(400);
    echo json_encode(["success" => false, "message" => "Missing or invalid guide ID"]);
    exit();
}

$updateStmt = $conn->prepare("
    UPDATE guide_posts
    SET views = COALESCE(views, 0) + 1
    WHERE id = ?
");
if (!$updateStmt) {
    http_response_code(500);
    echo json_encode(["success" => false, "message" => "Prepare failed"]);
    exit();
}
$updateStmt->bind_param('i', $id);
if (!$updateStmt->execute() || $updateStmt->affected_rows < 1) {
    http_response_code(404);
    echo json_encode(["success" => false, "message" => "Guide not found"]);
    $updateStmt->close();
    exit();
}
$updateStmt->close();

$selectStmt = $conn->prepare("SELECT COALESCE(views, 0) AS views_total FROM guide_posts WHERE id = ? LIMIT 1");
if (!$selectStmt) {
    http_response_code(500);
    echo json_encode(["success" => false, "message" => "Prepare failed"]);
    exit();
}
$selectStmt->bind_param('i', $id);
$selectStmt->execute();
$res = $selectStmt->get_result();
$row = $res ? $res->fetch_assoc() : null;
$selectStmt->close();

if (!$row) {
    http_response_code(404);
    echo json_encode(["success" => false, "message" => "Guide not found"]);
    exit();
}

$viewsTotal = intval($row['views_total'] ?? 0);
echo json_encode([
    "success" => true,
    "data" => [
        "id" => (string)$id,
        "views" => $viewsTotal,
        "views_total" => $viewsTotal
    ]
], JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT);
