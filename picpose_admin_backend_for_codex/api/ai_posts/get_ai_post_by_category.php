<?php
// /api/ai_posts/get_ai_post_by_category.php
header('Content-Type: application/json; charset=utf-8');
header("Access-Control-Allow-Origin: *");
header("Access-Control-Allow-Methods: GET");

require '../../config.php';

$VALID_API_KEY = "7a6f3c27a1b6d5e8e4c8a2b3f9e6d1f47c5b8a9d3e7f2c6a4b9e3d1c5f8a7b2c";
if (!isset($_GET['api_key']) || $_GET['api_key'] !== $VALID_API_KEY) {
    http_response_code(403); echo json_encode(["success"=>false,"message"=>"Unauthorized","data"=>[]]); exit();
}

$category = trim($_GET['category'] ?? '');
$category_id = isset($_GET['category_id']) ? intval($_GET['category_id']) : 0;
$limit = max(1,intval($_GET['limit'] ?? 20));
$offset = max(0,intval($_GET['offset'] ?? 0));
$status = $_GET['status'] ?? 'published';

if ($category === '' && $category_id === 0) {
    http_response_code(400); echo json_encode(["success"=>false,"message"=>"category or category_id required"]); exit();
}

$baseProto = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') ? 'https' : 'http';
$BASE_URL = $baseProto . '://' . $_SERVER['HTTP_HOST'] . '/';

function makeImageUrl($path,$BASE_URL){ if (empty($path)) return null; if (preg_match('#^https?://#i',$path)) return $path; return $BASE_URL . ltrim($path,'/'); }

function parseTags($conn,$tagsField){
    $tags=[]; if (empty($tagsField)) return $tags;
    $decoded = json_decode($tagsField,true);
    if (json_last_error()===JSON_ERROR_NONE && is_array($decoded)){ foreach($decoded as $t){ $t=trim($t); if($t!=='') $tags[]=$t;} return array_values(array_unique($tags)); }
    $parts = array_map('trim', explode(',', $tagsField));
    $textTags=[]; $numericIds=[];
    foreach($parts as $p){ if($p==='') continue; if(is_numeric($p)) $numericIds[]=(int)$p; else $textTags[]=$p; }
    if(!empty($numericIds)){
        $placeholders = implode(',', array_fill(0,count($numericIds),'?'));
        $types = str_repeat('i', count($numericIds));
        $sql = "SELECT name FROM categories WHERE id IN ($placeholders)";
        $stmt = $conn->prepare($sql);
        if($stmt){
            $bindArgs = []; $bindArgs[] = &$types;
            for($i=0;$i<count($numericIds);$i++) $bindArgs[] = &$numericIds[$i];
            call_user_func_array([$stmt,'bind_param'],$bindArgs);
            $stmt->execute(); $res = $stmt->get_result();
            while($r = $res->fetch_assoc()) $tags[] = $r['name'];
            $stmt->close();
        }
    }
    $tags = array_merge($tags, $textTags);
    return array_values(array_unique(array_filter(array_map('trim',$tags))));
}

// Build query (add category image_path)
$sql = "SELECT p.*, c.name AS category_name, c.image_path AS category_image_path, c.id AS category_id
        FROM ai_posts p
        LEFT JOIN categories c ON c.id = p.category_id
        WHERE p.status = ? ";
$params = [$status]; $types = "s";

if ($category_id > 0) {
    $sql .= " AND p.category_id = ? "; $types .= "i"; $params[] = $category_id;
} else {
    $sql .= " AND c.name = ? "; $types .= "s"; $params[] = $category;
}

$sql .= " ORDER BY p.priority DESC, p.created_at DESC LIMIT ? OFFSET ?"; $types .= "ii";
$params[] = $limit; $params[] = $offset;

$stmt = $conn->prepare($sql);
if(!$stmt){ http_response_code(500); echo json_encode(["success"=>false,"message"=>"prepare_failed"]); exit(); }

// bind params
$bindArgs = []; $bindArgs[] = &$types;
for($i=0;$i<count($params);$i++) $bindArgs[] = &$params[$i];
call_user_func_array([$stmt,'bind_param'], $bindArgs);

$stmt->execute(); $res = $stmt->get_result();
$data=[];
while($row = $res->fetch_assoc()){
    $data[] = [
        'id'=>(string)$row['id'],
        'title'=>$row['title'],
        'shortPrompt'=>$row['short_description'] ?? '',
        'fullPrompt'=>$row['prompt_text'] ?? '',
        'imageUrl'=>makeImageUrl($row['image_url1'] ?? '', $BASE_URL),
        'categoryId'=> isset($row['category_id']) ? (int)$row['category_id'] : null,
        'category'=>$row['category_name'] ?? '',
        'categoryImageUrl'=>makeImageUrl($row['category_image_path'] ?? '', $BASE_URL),
        'tags'=>parseTags($conn, $row['tags'] ?? ''),
        'likes'=>(int)($row['likes'] ?? 0),
        'isPopular'=>!empty($row['is_popular'])
    ];
}
$stmt->close();
echo json_encode(['success'=>true,'data'=>$data], JSON_UNESCAPED_UNICODE|JSON_PRETTY_PRINT);