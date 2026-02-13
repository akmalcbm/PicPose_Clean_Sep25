<?php
// /api/ai_posts/delete_ai_post.php
header('Content-Type: application/json; charset=utf-8');
header("Access-Control-Allow-Origin: *");
header("Access-Control-Allow-Methods: GET, DELETE");
require '../../config.php';

$VALID_API_KEY = "7a6f3c27a1b6d5e8e4c8a2b3f9e6d1f47c5b8a9d3e7f2c6a4b9e3d1c5f8a7b2c";
if (!isset($_GET['api_key']) || $_GET['api_key'] !== $VALID_API_KEY) { http_response_code(403); echo json_encode(["success"=>false,"message"=>"Unauthorized"]); exit(); }

$id = intval($_GET['id'] ?? 0);
if ($id <= 0) { http_response_code(400); echo json_encode(['success'=>false,'message'=>'missing id']); exit(); }

$stmt = $conn->prepare("DELETE FROM ai_posts WHERE id = ? LIMIT 1");
if(!$stmt){ http_response_code(500); echo json_encode(['success'=>false,'message'=>'prepare_failed']); exit(); }
$stmt->bind_param('i', $id);
if($stmt->execute()){
    echo json_encode(['success'=>true,'deleted_rows'=>$stmt->affected_rows], JSON_UNESCAPED_UNICODE|JSON_PRETTY_PRINT);
} else {
    http_response_code(500);
    echo json_encode(['success'=>false,'message'=>'delete_failed','sql_error'=>$stmt->error]);
}
$stmt->close();
