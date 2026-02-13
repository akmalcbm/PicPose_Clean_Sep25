<?php
// /api/ai_posts/increment_like.php

header('Content-Type: application/json; charset=utf-8');
header("Access-Control-Allow-Origin: *");
header("Access-Control-Allow-Methods: POST");

require '../../config.php';

/* -------------------------
   API KEY VALIDATION
------------------------- */
$VALID_API_KEY = "7a6f3c27a1b6d5e8e4c8a2b3f9e6d1f47c5b8a9d3e7f2c6a4b9e3d1c5f8a7b2c";
$provided = $_GET['api_key'] ?? $_POST['api_key'] ?? null;

if (!$provided || $provided !== $VALID_API_KEY) {
    http_response_code(403);
    echo json_encode([
        "success" => false,
        "message" => "Unauthorized"
    ]);
    exit();
}

/* -------------------------
   INPUT VALIDATION
------------------------- */
$id = intval($_POST['id'] ?? $_GET['id'] ?? 0);
if ($id <= 0) {
    http_response_code(400);
    echo json_encode([
        "success" => false,
        "message" => "Missing or invalid post ID"
    ]);
    exit();
}

/* -------------------------
   ATOMIC INCREMENT
------------------------- */
$updateStmt = $conn->prepare(
    "UPDATE ai_posts 
     SET likes = COALESCE(likes, 0) + 1 
     WHERE id = ?"
);
$updateStmt->bind_param('i', $id);

if (!$updateStmt->execute()) {
    http_response_code(500);
    echo json_encode([
        "success" => false,
        "message" => "Failed to increment like",
        "sql_error" => $updateStmt->error
    ]);
    $updateStmt->close();
    exit();
}
$updateStmt->close();

/* -------------------------
   FETCH UPDATED COUNT
------------------------- */
$selectStmt = $conn->prepare(
    "SELECT COALESCE(likes, 0) AS likes FROM ai_posts WHERE id = ?"
);
$selectStmt->bind_param('i', $id);
$selectStmt->execute();
$result = $selectStmt->get_result();

if (!$row = $result->fetch_assoc()) {
    http_response_code(404);
    echo json_encode([
        "success" => false,
        "message" => "Post not found after update"
    ]);
    $selectStmt->close();
    exit();
}

$selectStmt->close();

/* -------------------------
   FINAL RESPONSE
------------------------- */
echo json_encode([
    "success" => true,
    "likes"   => (int)$row['likes']
], JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT);
