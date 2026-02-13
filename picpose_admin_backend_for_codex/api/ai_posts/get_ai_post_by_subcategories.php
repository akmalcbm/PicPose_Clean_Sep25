<?php
// /api/ai_posts/get_ai_post_by_subcategories.php
header('Content-Type: application/json; charset=utf-8');
header("Access-Control-Allow-Origin: *");
header("Access-Control-Allow-Methods: GET");
require '../../config.php';

$VALID_API_KEY = "7a6f3c27a1b6d5e8e4c8a2b3f9e6d1f47c5b8a9d3e7f2c6a4b9e3d1c5f8a7b2c";
if (!isset($_GET['api_key']) || $_GET['api_key'] !== $VALID_API_KEY) { http_response_code(403); echo json_encode(["success"=>false,"message"=>"Unauthorized"]); exit(); }

$parent_id = intval($_GET['parent_id'] ?? 0);
if ($parent_id <= 0) { http_response_code(400); echo json_encode(['success'=>false,'message'=>'parent_id required']); exit(); }

$limit = max(1,intval($_GET['limit'] ?? 50)); $offset = max(0,intval($_GET['offset'] ?? 0));

$baseProto = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') ? 'https' : 'http';
$BASE_URL = $baseProto . '://' . $_SERVER['HTTP_HOST'] . '/';

function makeImageUrl($path,$BASE_URL){ if (empty($path)) return null; if (preg_match('#^https?://#i',$path)) return $path; return $BASE_URL . ltrim($path,'/'); }

// get child categories
$cats = [$parent_id];
$stmt = $conn->prepare("SELECT id FROM categories WHERE parent_id = ?");
$stmt->bind_param('i', $parent_id);
$stmt->execute(); $res = $stmt->get_result();
while($r = $res->fetch_assoc()) $cats[] = (int)$r['id'];
$stmt->close();

// build placeholders
$placeholders = implode(',', array_fill(0, count($cats), '?'));
$types = str_repeat('i', count($cats)) . 'ii';
$params = array_merge($cats, [$limit, $offset]);

// Join with categories to expose category image
$sql = "SELECT p.id, p.title, p.short_description, p.image_url1, p.tags, p.category_id, p.created_at,
               c.name AS category_name, c.image_path AS category_image_path
        FROM ai_posts p
        LEFT JOIN categories c ON c.id = p.category_id
        WHERE p.category_id IN ($placeholders) AND p.status='published'
        ORDER BY p.priority DESC, p.created_at DESC LIMIT ? OFFSET ?";
$stmt = $conn->prepare($sql);
if(!$stmt){ http_response_code(500); echo json_encode(['success'=>false,'message'=>'prepare_failed']); exit(); }

// bind dynamically
$bindArgs = []; $bindArgs[] = &$types;
for($i=0;$i<count($params);$i++) $bindArgs[] = &$params[$i];
call_user_func_array([$stmt,'bind_param'],$bindArgs);

$stmt->execute(); $res = $stmt->get_result();
$data = [];
while($row = $res->fetch_assoc()){
    $tags = json_decode($row['tags'] ?? '[]', true) ?: [];
    $data[] = [
        'id'=>(string)$row['id'],
        'title'=>$row['title'],
        'shortPrompt'=>$row['short_description'] ?? '',
        'imageUrl'=> makeImageUrl($row['image_url1'] ?? '', $BASE_URL),
        'tags'=>$tags,
        'categoryId'=>(int)$row['category_id'],
        'category'=>$row['category_name'] ?? '',
        'categoryImageUrl'=>makeImageUrl($row['category_image_path'] ?? '', $BASE_URL)
    ];
}
$stmt->close();
echo json_encode(['success'=>true,'parent_id'=>$parent_id,'categories'=>$cats,'data'=>$data], JSON_UNESCAPED_UNICODE|JSON_PRETTY_PRINT);