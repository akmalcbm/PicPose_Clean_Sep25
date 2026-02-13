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
$deviceId = trim((string)($_POST['device_id'] ?? $_GET['device_id'] ?? ''));

if ($id <= 0) {
    http_response_code(400);
    echo json_encode(["success" => false, "message" => "Missing or invalid guide ID"]);
    exit();
}
if ($deviceId === '') {
    http_response_code(400);
    echo json_encode(["success" => false, "message" => "Missing device_id"]);
    exit();
}

$deviceId = substr($deviceId, 0, 100);

try {
    $conn->begin_transaction();

    $existsStmt = $conn->prepare("SELECT id FROM guide_posts WHERE id = ? LIMIT 1");
    if (!$existsStmt) throw new Exception("Prepare failed");
    $existsStmt->bind_param('i', $id);
    $existsStmt->execute();
    $existsRes = $existsStmt->get_result();
    $exists = $existsRes && $existsRes->num_rows > 0;
    $existsStmt->close();
    if (!$exists) {
        $conn->rollback();
        http_response_code(404);
        echo json_encode(["success" => false, "message" => "Guide not found"]);
        exit();
    }

    $isLiked = false;
    $checkStmt = $conn->prepare("SELECT id FROM guide_likes WHERE guide_id = ? AND device_id = ? LIMIT 1");
    if (!$checkStmt) throw new Exception("Prepare failed");
    $checkStmt->bind_param('is', $id, $deviceId);
    $checkStmt->execute();
    $checkRes = $checkStmt->get_result();
    $alreadyLiked = $checkRes && $checkRes->num_rows > 0;
    $checkStmt->close();

    if ($alreadyLiked) {
        $deleteStmt = $conn->prepare("DELETE FROM guide_likes WHERE guide_id = ? AND device_id = ?");
        if (!$deleteStmt) throw new Exception("Prepare failed");
        $deleteStmt->bind_param('is', $id, $deviceId);
        if (!$deleteStmt->execute()) throw new Exception("Failed to remove like");
        $deleteStmt->close();

        $decrementStmt = $conn->prepare("
            UPDATE guide_posts
            SET likes = CASE
                WHEN COALESCE(likes, 0) > 0 THEN likes - 1
                ELSE 0
            END
            WHERE id = ?
        ");
        if (!$decrementStmt) throw new Exception("Prepare failed");
        $decrementStmt->bind_param('i', $id);
        if (!$decrementStmt->execute()) throw new Exception("Failed to decrement likes");
        $decrementStmt->close();

        $isLiked = false;
    } else {
        $insertStmt = $conn->prepare("INSERT INTO guide_likes (guide_id, device_id, created_at) VALUES (?, ?, NOW())");
        if (!$insertStmt) throw new Exception("Prepare failed");
        $insertStmt->bind_param('is', $id, $deviceId);
        if (!$insertStmt->execute()) throw new Exception("Failed to add like");
        $insertStmt->close();

        $incrementStmt = $conn->prepare("
            UPDATE guide_posts
            SET likes = COALESCE(likes, 0) + 1
            WHERE id = ?
        ");
        if (!$incrementStmt) throw new Exception("Prepare failed");
        $incrementStmt->bind_param('i', $id);
        if (!$incrementStmt->execute()) throw new Exception("Failed to increment likes");
        $incrementStmt->close();

        $isLiked = true;
    }

    $selectStmt = $conn->prepare("SELECT COALESCE(likes, 0) AS likes_total FROM guide_posts WHERE id = ? LIMIT 1");
    if (!$selectStmt) throw new Exception("Prepare failed");
    $selectStmt->bind_param('i', $id);
    $selectStmt->execute();
    $res = $selectStmt->get_result();
    $row = $res ? $res->fetch_assoc() : null;
    $selectStmt->close();

    if (!$row) throw new Exception("Guide not found");

    $conn->commit();

    $likesTotal = intval($row['likes_total'] ?? 0);
    echo json_encode([
        "success" => true,
        "data" => [
            "id" => (string)$id,
            "likes" => $likesTotal,
            "likes_total" => $likesTotal,
            "liked" => $isLiked,
            "is_liked_by_user" => $isLiked
        ]
    ], JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT);
} catch (Throwable $e) {
    $conn->rollback();
    http_response_code(500);
    echo json_encode([
        "success" => false,
        "message" => "Failed to toggle like",
        "error" => $e->getMessage()
    ], JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT);
}
