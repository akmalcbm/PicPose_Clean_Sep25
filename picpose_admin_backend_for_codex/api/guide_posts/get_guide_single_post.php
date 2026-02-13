<?php
header('Content-Type: application/json; charset=utf-8');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET');

require_once __DIR__ . '/common_api.php';
require_once __DIR__ . '/../../config.php';

$VALID_API_KEY = "7a6f3c27a1b6d5e8e4c8a2b3f9e6d1f47c5b8a9d3e7f2c6a4b9e3d1c5f8a7b2c";
if (!isset($_GET['api_key']) || $_GET['api_key'] !== $VALID_API_KEY) {
    send_json(["success" => false, "message" => "Unauthorized Access", "data" => []], 403);
}

$id = isset($_GET['id']) ? intval($_GET['id']) : 0;
if ($id <= 0) {
    send_json(["success" => false, "message" => "missing id", "data" => []], 400);
}
$deviceId = trim((string)($_GET['device_id'] ?? $_POST['device_id'] ?? ''));

$baseProto = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') ? 'https' : 'http';
$BASE_URL = $baseProto . '://' . ($_SERVER['HTTP_HOST'] ?? '') . '/';

function makeImageUrl($path) {
    global $BASE_URL;
    if ($path === null) return null;
    $p = trim((string)$path);
    if ($p === '') return null;
    if (preg_match('#^https?://#i', $p)) return $p;
    return $BASE_URL . ltrim($p, '/');
}

function to_iso_datetime($value) {
    $v = trim((string)$value);
    if ($v === '') return null;
    $ts = strtotime($v);
    if ($ts === false) return null;
    return gmdate('c', $ts);
}

function normalizeJsonList($field) {
    if (empty($field)) return [];
    $decoded = @json_decode($field, true);
    if (json_last_error() === JSON_ERROR_NONE && is_array($decoded)) {
        return array_values(array_filter(array_map(function($v){ return trim((string)$v); }, $decoded)));
    }
    $parts = array_map('trim', preg_split('/[,;|]+/', (string)$field));
    return array_values(array_filter($parts));
}

function estimate_read_time_minutes($textOrHtml) {
    $plain = trim(strip_tags((string)$textOrHtml));
    if ($plain === '') return 1;
    $count = str_word_count($plain);
    return max(1, (int)ceil($count / 200));
}

function parseTagsInline($conn, $tagsField) {
    $tags = [];
    if (empty($tagsField)) return $tags;

    $decoded = @json_decode($tagsField, true);
    if (json_last_error() === JSON_ERROR_NONE && is_array($decoded)) {
        foreach ($decoded as $t) {
            $v = trim((string)$t);
            if ($v !== '') $tags[] = $v;
        }
        return array_values(array_unique($tags));
    }

    $parts = array_map('trim', preg_split('/[,;|]+/', (string)$tagsField));
    $numericIds = [];
    $textTags = [];
    foreach ($parts as $p) {
        if ($p === '') continue;
        if (is_numeric($p)) $numericIds[] = (int)$p;
        else $textTags[] = ltrim($p, '#');
    }

    if (!empty($numericIds)) {
        $placeholders = implode(',', array_fill(0, count($numericIds), '?'));
        $types = str_repeat('i', count($numericIds));
        $sql = "SELECT name FROM categories WHERE id IN ($placeholders)";
        $stmt = $conn->prepare($sql);
        if ($stmt) {
            $bindArgs = [];
            $bindArgs[] = &$types;
            for ($i = 0; $i < count($numericIds); $i++) $bindArgs[] = &$numericIds[$i];
            call_user_func_array([$stmt, 'bind_param'], $bindArgs);
            if ($stmt->execute()) {
                $res = $stmt->get_result();
                while ($r = $res->fetch_assoc()) {
                    $name = trim((string)($r['name'] ?? ''));
                    if ($name !== '') $tags[] = $name;
                }
            }
            $stmt->close();
        }
    }

    $tags = array_merge($tags, $textTags);
    return array_values(array_unique(array_filter(array_map('trim', $tags))));
}

function detectVideoProvider($url) {
    $u = strtolower(trim((string)$url));
    if ($u === '') return 'mp4';
    if (strpos($u, 'youtube.com') !== false || strpos($u, 'youtu.be') !== false) return 'youtube';
    if (strpos($u, 'vimeo.com') !== false) return 'vimeo';
    return 'mp4';
}

function normalizeExistingBlocks($blocks) {
    $allowed = ['hero','h1','h2','h3','p','image','video','callout','ol','ul','divider'];
    $out = [];
    if (!is_array($blocks)) return $out;

    foreach ($blocks as $b) {
        if (!is_array($b)) continue;
        $type = strtolower(trim((string)($b['type'] ?? '')));
        if (!in_array($type, $allowed, true)) continue;

        $entry = ['type' => $type];

        if (in_array($type, ['h1','h2','h3','p'], true)) {
            $text = trim((string)($b['text'] ?? ''));
            if ($text === '') continue;
            $entry['text'] = $text;
        } elseif ($type === 'hero' || $type === 'image') {
            $url = trim((string)($b['url'] ?? ($b['image'] ?? '')));
            if ($url === '') continue;
            if ($type === 'hero') $entry['image'] = makeImageUrl($url);
            else $entry['url'] = makeImageUrl($url);
            $caption = trim((string)($b['caption'] ?? ''));
            $alt = trim((string)($b['alt'] ?? ''));
            if ($caption !== '') $entry['caption'] = $caption;
            if ($alt !== '') $entry['alt'] = $alt;
        } elseif ($type === 'video') {
            $url = trim((string)($b['url'] ?? ''));
            if ($url === '') continue;
            $entry['url'] = makeImageUrl($url);
            $provider = strtolower(trim((string)($b['provider'] ?? '')));
            if (!in_array($provider, ['mp4','youtube','vimeo'], true)) $provider = detectVideoProvider($url);
            $entry['provider'] = $provider;
            $caption = trim((string)($b['caption'] ?? ''));
            if ($caption !== '') $entry['caption'] = $caption;
        } elseif ($type === 'callout') {
            $text = trim((string)($b['text'] ?? ''));
            if ($text === '') continue;
            $title = trim((string)($b['title'] ?? 'Expert advice'));
            $entry['title'] = $title !== '' ? $title : 'Expert advice';
            $entry['text'] = $text;
        } elseif ($type === 'ol' || $type === 'ul') {
            $items = $b['items'] ?? [];
            if (!is_array($items)) $items = preg_split('/\r\n|\r|\n/', (string)$items);
            $clean = [];
            foreach ($items as $it) {
                $v = trim((string)$it);
                if ($v !== '') $clean[] = $v;
            }
            if (empty($clean)) continue;
            $entry['items'] = array_values($clean);
        }

        $out[] = $entry;
    }

    return $out;
}

function contentFallbackBlocks($content, $heroUrl, $images, $videos) {
    $blocks = [];

    if (!empty($heroUrl)) {
        $blocks[] = ['type' => 'hero', 'image' => $heroUrl];
    }

    $remainingImages = [];
    foreach ($images as $img) {
        if ($heroUrl && $img === $heroUrl) continue;
        $remainingImages[] = $img;
    }

    $plain = trim((string)html_entity_decode(strip_tags((string)$content), ENT_QUOTES | ENT_HTML5));
    $paragraphs = [];
    if ($plain !== '') {
        $parts = preg_split('/\R\s*\R+/', $plain);
        foreach ($parts as $segment) {
            $s = trim((string)$segment);
            if ($s !== '') $paragraphs[] = $s;
        }
    }

    $paragraphCount = 0;
    $nextImg = 0;
    foreach ($paragraphs as $para) {
        if (preg_match('/^(#{1,3})\s*(.+)$/u', $para, $m)) {
            $level = strlen($m[1]);
            $blocks[] = ['type' => 'h' . $level, 'text' => trim($m[2])];
            continue;
        }

        if (preg_match('/^(.+):$/u', $para, $m) && mb_strlen($para) <= 120) {
            $blocks[] = ['type' => 'h2', 'text' => trim($m[1])];
            continue;
        }

        if (preg_match('/^Expert\s+advice\s*:\s*(.+)$/iu', $para, $m)) {
            $blocks[] = ['type' => 'callout', 'title' => 'Expert advice', 'text' => trim($m[1])];
            continue;
        }

        $lines = preg_split('/\R+/', $para);
        $ol = [];
        $ul = [];
        $isOl = true;
        $isUl = true;
        foreach ($lines as $line) {
            $line = trim($line);
            if ($line === '') continue;
            if (preg_match('/^\d+\.\s+(.+)$/u', $line, $mOl)) {
                $ol[] = trim($mOl[1]);
            } else {
                $isOl = false;
            }
            if (preg_match('/^[-*]\s+(.+)$/u', $line, $mUl)) {
                $ul[] = trim($mUl[1]);
            } else {
                $isUl = false;
            }
        }

        if ($isOl && !empty($ol)) {
            $blocks[] = ['type' => 'ol', 'items' => array_values($ol)];
            continue;
        }
        if ($isUl && !empty($ul)) {
            $blocks[] = ['type' => 'ul', 'items' => array_values($ul)];
            continue;
        }

        $blocks[] = ['type' => 'p', 'text' => $para];
        $paragraphCount++;

        if ($paragraphCount % 2 === 0 && isset($remainingImages[$nextImg])) {
            $blocks[] = ['type' => 'image', 'url' => $remainingImages[$nextImg]];
            $nextImg++;
        }
    }

    while (isset($remainingImages[$nextImg])) {
        $blocks[] = ['type' => 'image', 'url' => $remainingImages[$nextImg]];
        $nextImg++;
    }

    foreach ($videos as $video) {
        $blocks[] = ['type' => 'video', 'url' => $video, 'provider' => detectVideoProvider($video)];
    }

    return $blocks;
}

$sql = "SELECT g.*, c.name AS category_name
        FROM guide_posts g
        LEFT JOIN categories c ON c.id = g.category_id
        WHERE g.id = ?
        LIMIT 1";

$stmt = $conn->prepare($sql);
if (!$stmt) {
    send_json(["success" => false, "message" => "prepare_failed", "data" => []], 500);
}
$stmt->bind_param('i', $id);
if (!$stmt->execute()) {
    $stmt->close();
    send_json(["success" => false, "message" => "execute_failed", "data" => []], 500);
}

$res = $stmt->get_result();
$post = $res ? $res->fetch_assoc() : null;
$stmt->close();

if (!$post) {
    send_json(["success" => false, "message" => "not_found", "data" => []], 404);
}

$tagsArray = parseTagsInline($conn, $post['tags'] ?? '');
$imagesList = normalizeJsonList($post['images'] ?? null);
$videosList = normalizeJsonList($post['videos'] ?? null);

$imagesListFull = array_values(array_filter(array_map(function($p){ return makeImageUrl($p); }, $imagesList)));
$videosListFull = array_values(array_filter(array_map(function($p){ return makeImageUrl($p); }, $videosList)));
$videosDetailed = array_map(function($url) {
    return [
        'url' => $url,
        'provider' => detectVideoProvider($url)
    ];
}, $videosListFull);
$heroImage = makeImageUrl($post['image_url1'] ?? '');

$contentBlocks = [];
if (!empty($post['content_blocks'])) {
    $decodedBlocks = @json_decode($post['content_blocks'], true);
    if (json_last_error() === JSON_ERROR_NONE && is_array($decodedBlocks)) {
        $contentBlocks = normalizeExistingBlocks($decodedBlocks);
    }
}
if (empty($contentBlocks)) {
    $contentBlocks = contentFallbackBlocks($post['content'] ?? '', $heroImage, $imagesListFull, $videosListFull);
}

$idString = (string)($post['id'] ?? '');
$description = (string)($post['short_description'] ?? '');
$category = (string)($post['category_name'] ?? '');
$likes = intval($post['likes'] ?? 0);
$views = intval($post['views'] ?? 0);
$favorites = intval($post['favorites'] ?? 0);
$isFeatured = !empty($post['is_featured']);
$isPopular = !empty($post['is_popular']);
$statusValue = (string)($post['status'] ?? 'published');
$createdAt = to_iso_datetime($post['created_at'] ?? null);
$updatedAt = to_iso_datetime($post['updated_at'] ?? null);
$shareUrl = null;
if (!empty($post['external_id'])) {
    $shareUrl = $BASE_URL . 'guide/' . rawurlencode((string)$post['external_id']);
}
$author = null;
if (!empty($post['author'])) $author = (string)$post['author'];
if (!empty($post['author_name'])) $author = (string)$post['author_name'];
$readTimeMinutes = estimate_read_time_minutes($post['content'] ?? '');
$isLikedByUser = false;
if ($deviceId !== '') {
    $likeStmt = $conn->prepare("SELECT 1 FROM guide_likes WHERE guide_id = ? AND device_id = ? LIMIT 1");
    if ($likeStmt) {
        $postIdInt = intval($post['id'] ?? 0);
        $likeStmt->bind_param('is', $postIdInt, $deviceId);
        if ($likeStmt->execute()) {
            $likeRes = $likeStmt->get_result();
            $isLikedByUser = $likeRes && $likeRes->num_rows > 0;
        }
        $likeStmt->close();
    }
}

send_json([
    "success" => true,
    "message" => "ok",
    "data" => [
        "id" => $idString,
        "title" => (string)($post['title'] ?? ''),
        "description" => $description,
        "image" => $heroImage,
        "category" => $category,
        "tags" => $tagsArray,
        "likes" => $likes,
        "likes_total" => $likes,
        "views" => $views,
        "views_total" => $views,
        "is_liked_by_user" => $isLikedByUser,
        "favorites" => $favorites,
        "isFeatured" => $isFeatured,
        "isPopular" => $isPopular,
        "status" => $statusValue,
        "createdAt" => $createdAt,
        "updatedAt" => $updatedAt,
        "readTimeMinutes" => $readTimeMinutes,
        "content" => $post['content'] ?? '',
        "contentBlocks" => $contentBlocks,
        "images" => $imagesListFull,
        "videos" => $videosDetailed,
        "shareUrl" => $shareUrl,
        "author" => $author,
        "priority" => intval($post['priority'] ?? 0),
        // Backward-compatible aliases
        "shortDescription" => $description,
        "imageUrl" => $heroImage,
        "imageUrl2" => makeImageUrl($post['image_url2'] ?? ''),
        "short_description" => $description,
        "image_url1" => $heroImage,
        "category_name" => $category,
        "is_featured" => $isFeatured,
        "is_popular" => $isPopular,
        "videoUrls" => $videosListFull,
        "externalId" => $post['external_id'] ?? null,
        "created_at" => $post['created_at'] ?? null,
        "updated_at" => $post['updated_at'] ?? null
    ]
]);
