<?php
// /api/ai_posts/get_ai_post_by_tag.php
header('Content-Type: application/json; charset=utf-8');
header("Access-Control-Allow-Origin: *");
header("Access-Control-Allow-Methods: GET");
require '../../config.php';

$VALID_API_KEY = "7a6f3c27a1b6d5e8e4c8a2b3f9e6d1f47c5b8a9d3e7f2c6a4b9e3d1c5f8a7b2c";
if (!isset($_GET['api_key']) || $_GET['api_key'] !== $VALID_API_KEY) { http_response_code(403); echo json_encode(["success"=>false,"message"=>"Unauthorized"]); exit(); }

$tag = trim($_GET['tag'] ?? '');
if ($tag === '') { http_response_code(400); echo json_encode(["success"=>false,"message"=>"tag required"]); exit(); }
$limit = max(1,intval($_GET['limit'] ?? 20)); $offset = max(0,intval($_GET['offset'] ?? 0));

$baseProto = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') ? 'https' : 'http';
$BASE_URL = $baseProto . '://' . $_SERVER['HTTP_HOST'] . '/';

function makeImageUrl($p,$BASE){ if (empty($p)) return null; if (preg_match('#^https?://#i',$p)) return $p; return $BASE . ltrim($p,'/'); }
function parseTags($conn,$tagsField){ /* same as previous - copy implementation */ 
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
            $bindArgs = []; $bindArgs[]=&$types;
            for($i=0;$i<count($numericIds);$i++) $bindArgs[]=&$numericIds[$i];
            call_user_func_array([$stmt,'bind_param'],$bindArgs);
            $stmt->execute(); $res = $stmt->get_result();
            while($r = $res->fetch_assoc()) $tags[] = $r['name'];
            $stmt->close();
        }
    }
    $tags = array_merge($tags, $textTags);
    return array_values(array_unique(array_filter(array_map('trim',$tags))));
}

$sql = "SELECT id,title,short_description,image_url1,tags,created_at FROM ai_posts WHERE status='published' AND tags LIKE CONCAT('%',?,'%') ORDER BY priority DESC, created_at DESC LIMIT ? OFFSET ?";
$stmt = $conn->prepare($sql);
if(!$stmt){ http_response_code(500); echo json_encode(['success'=>false,'message'=>'prepare_failed']); exit(); }
$stmt->bind_param('sii', $tag, $limit, $offset);
$stmt->execute(); $res = $stmt->get_result();
$data=[];
while($row = $res->fetch_assoc()){
    $data[] = [
        'id'=>(string)$row['id'],
        'title'=>$row['title'],
        'shortPrompt'=>$row['short_description'] ?? '',
        'imageUrl'=>makeImageUrl($row['image_url1'] ?? '', $BASE_URL),
        'tags'=>parseTags($conn, $row['tags'] ?? ''),
        'createdAt'=>$row['created_at']
    ];
}
$stmt->close();
echo json_encode(['success'=>true,'data'=>$data], JSON_UNESCAPED_UNICODE|JSON_PRETTY_PRINT);
