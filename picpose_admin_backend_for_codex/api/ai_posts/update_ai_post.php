<?php
// /api/ai_posts/update_ai_post.php
header('Content-Type: application/json; charset=utf-8');
header("Access-Control-Allow-Origin: *");
header("Access-Control-Allow-Methods: POST, PUT");
require '../../config.php';

$VALID_API_KEY = "7a6f3c27a1b6d5e8e4c8a2b3f9e6d1f47c5b8a9d3e7f2c6a4b9e3d1c5f8a7b2c";
$provided = $_GET['api_key'] ?? $_POST['api_key'];
if (!$provided || $provided !== $VALID_API_KEY) { http_response_code(403); echo json_encode(["success"=>false,"message"=>"Unauthorized"]); exit(); }

$input = json_decode(file_get_contents('php://input'), true);
if (!$input) $input = $_POST;

$id = intval($input['id'] ?? 0);
if ($id <= 0) { http_response_code(400); echo json_encode(['success'=>false,'message'=>'missing id']); exit(); }

$allowed = ['title','category_id','short_description','prompt_text','image_url1','image_url2','tags','status','priority','is_popular','is_featured','external_id'];
$fields = []; $params = []; $types = '';

foreach($allowed as $f){
    if (array_key_exists($f, $input)){
        $fields[] = "$f = ?";
        $params[] = $input[$f];
        if (in_array($f, ['category_id','priority','is_popular','is_featured'])) $types .= 'i';
        else $types .= 's';
    }
}
if (empty($fields)) { echo json_encode(['success'=>false,'message'=>'no_fields_to_update']); exit(); }

$sql = "UPDATE ai_posts SET " . implode(', ', $fields) . ", updated_at = NOW() WHERE id = ?";
$params[] = $id; $types .= 'i';

$stmt = $conn->prepare($sql);
if(!$stmt){ http_response_code(500); echo json_encode(['success'=>false,'message'=>'prepare_failed','sql_error'=>$conn->error]); exit(); }

// bind dynamic
$bindArgs = []; $bindArgs[] = &$types;
for($i=0;$i<count($params);$i++) $bindArgs[] = &$params[$i];
call_user_func_array([$stmt,'bind_param'],$bindArgs);

if($stmt->execute()){
    echo json_encode(['success'=>true,'affected_rows'=>$stmt->affected_rows], JSON_UNESCAPED_UNICODE|JSON_PRETTY_PRINT);
} else {
    http_response_code(500);
    echo json_encode(['success'=>false,'message'=>'update_failed','sql_error'=>$stmt->error]);
}
$stmt->close();
