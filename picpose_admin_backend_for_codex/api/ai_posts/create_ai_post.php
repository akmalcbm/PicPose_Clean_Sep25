<?php
// /api/ai_posts/create_ai_post.php
header('Content-Type: application/json; charset=utf-8');
header("Access-Control-Allow-Origin: *");
header("Access-Control-Allow-Methods: POST");
require '../../config.php';

// TODO: in production, use stronger admin auth (JWT / separate admin API key)
$VALID_API_KEY = "7a6f3c27a1b6d5e8e4c8a2b3f9e6d1f47c5b8a9d3e7f2c6a4b9e3d1c5f8a7b2c";
$provided = $_GET['api_key'] ?? $_POST['api_key'] ?? null;
if (!$provided || $provided !== $VALID_API_KEY) { http_response_code(403); echo json_encode(["success"=>false,"message"=>"Unauthorized"]); exit(); }

$input = json_decode(file_get_contents('php://input'), true);
if (!$input) $input = $_POST;

$required = ['title','category_id','prompt_text','image_url1'];
foreach($required as $f){ if(empty($input[$f])){ http_response_code(400); echo json_encode(['success'=>false,'message'=>"missing_$f"]); exit(); } }

$title = $input['title'];
$category_id = intval($input['category_id']);
$short_description = $input['short_description'] ?? null;
$prompt_text = $input['prompt_text'];
$image_url1 = $input['image_url1'];
$image_url2 = $input['image_url2'] ?? null;
$tags = isset($input['tags']) ? json_encode($input['tags']) : ($input['tags'] ?? null); // allow array or string
$status = $input['status'] ?? 'published';
$priority = intval($input['priority'] ?? 0);
$is_popular = !empty($input['is_popular']) ? 1 : 0;
$is_featured = !empty($input['is_featured']) ? 1 : 0;

$sql = "INSERT INTO ai_posts (title, category_id, short_description, prompt_text, image_url1, image_url2, tags, status, priority, is_popular, is_featured, created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?, NOW())";
$stmt = $conn->prepare($sql);
if(!$stmt){ http_response_code(500); echo json_encode(['success'=>false,'message'=>'prepare_failed','sql_error'=>$conn->error]); exit(); }

// types: s i s s s s s s i i i -> build concrete types
$types = 'sisssssiiii';
$stmt->bind_param($types, $title, $category_id, $short_description, $prompt_text, $image_url1, $image_url2, $tags, $status, $priority, $is_popular, $is_featured);

if($stmt->execute()){
    echo json_encode(['success'=>true,'insert_id'=>$conn->insert_id], JSON_UNESCAPED_UNICODE|JSON_PRETTY_PRINT);
} else {
    http_response_code(500);
    echo json_encode(['success'=>false,'message'=>'insert_failed','sql_error'=>$stmt->error]);
}
$stmt->close();
