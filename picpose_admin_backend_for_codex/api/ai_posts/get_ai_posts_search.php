<?php
// /api/ai_posts/get_ai_posts_search.php
header('Content-Type: application/json; charset=utf-8');
header("Access-Control-Allow-Origin: *");
header("Access-Control-Allow-Methods: GET");
require '../../config.php';

$VALID_API_KEY = "7a6f3c27a1b6d5e8e4c8a2b3f9e6d1f47c5b8a9d3e7f2c6a4b9e3d1c5f8a7b2c";
if (!isset($_GET['api_key']) || $_GET['api_key'] !== $VALID_API_KEY) { http_response_code(403); echo json_encode(["success"=>false,"message"=>"Unauthorized"]); exit(); }

$q = trim($_GET['q'] ?? '');
if ($q === '') { echo json_encode(['success'=>true,'count'=>0,'data'=>[]]); exit(); }

$limit = max(1,intval($_GET['limit'] ?? 50));

$baseProto = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') ? 'https' : 'http';
$BASE_URL = $baseProto . '://' . $_SERVER['HTTP_HOST'] . '/';

$sql = "SELECT id,title,short_description,image_url1,tags FROM ai_posts WHERE status='published' AND (title LIKE CONCAT('%',?,'%') OR prompt_text LIKE CONCAT('%',?,'%') OR short_description LIKE CONCAT('%',?,'%') OR tags LIKE CONCAT('%',?,'%')) ORDER BY priority DESC, created_at DESC LIMIT ?";
$stmt = $conn->prepare($sql);
if(!$stmt){ http_response_code(500); echo json_encode(['success'=>false,'message'=>'prepare_failed']); exit(); }
$like = $q; $stmt->bind_param('ssssi', $like, $like, $like, $like, $limit);
$stmt->execute(); $res = $stmt->get_result();
$data=[];
while($r = $res->fetch_assoc()){
    $r['tags'] = json_decode($r['tags'] ?? '[]', true) ?: [];
    $data[] = [
        'id'=>(string)$r['id'],
        'title'=>$r['title'],
        'shortPrompt'=>$r['short_description'] ?? '',
        'imageUrl'=> (preg_match('#^https?://#i',$r['image_url1'] ?? '') ? $r['image_url1'] : $BASE_URL . ltrim($r['image_url1'] ?? '', '/')),
        'tags'=>$r['tags']
    ];
}
$stmt->close();
echo json_encode(['success'=>true,'count'=>count($data),'data'=>$data], JSON_UNESCAPED_UNICODE|JSON_PRETTY_PRINT);
