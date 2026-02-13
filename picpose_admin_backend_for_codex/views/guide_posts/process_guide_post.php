<?php
// views/guide_posts/process_guide_post.php
session_start();
require '../../config.php';

// Toggle for verbose debugging (set false in production)
const DEBUG_MODE = false;

if (!isset($_SESSION['admin'])) {
    header('Location: ../../login.php');
    exit();
}

if (!isset($conn) || !$conn) {
    $_SESSION['message'] = 'Database connection error.';
    $_SESSION['message_type'] = 'danger';
    header('Location: manage_guides.php');
    exit();
}

function dbg($m) { if (DEBUG_MODE) error_log('[process_guide_post] ' . $m); }
function safe_filename($name) { return preg_replace('/[^A-Za-z0-9\-\_\.]/', '_', $name); }

$uploadWebBase = 'uploads/guide_posts_pic/';
$uploadDir = __DIR__ . '/../../' . $uploadWebBase;
if (!is_dir($uploadDir)) @mkdir($uploadDir, 0755, true);

// Grab action
$action = trim($_POST['action'] ?? $_GET['action'] ?? '');
dbg("Action: $action");

// quick csrf check for POST flows
$posted_csrf = $_POST['csrf_token'] ?? '';
if ($_SERVER['REQUEST_METHOD'] === 'POST' && !empty($_SESSION['csrf_token']) && empty($posted_csrf)) {
    dbg("Missing CSRF token in POST");
}

// ---------- DELETE ----------
if ($action === 'delete') {
    $id = intval($_POST['id'] ?? 0);
    if (!$id) {
        $_SESSION['message'] = 'Invalid id for delete.';
        $_SESSION['message_type'] = 'danger';
        header('Location: manage_guides.php'); exit();
    }
    if (empty($_SESSION['csrf_token']) || empty($posted_csrf) || !hash_equals($_SESSION['csrf_token'], $posted_csrf)) {
        $_SESSION['message'] = 'Invalid CSRF token for delete.';
        $_SESSION['message_type'] = 'danger';
        header('Location: manage_guides.php'); exit();
    }

    $stmt = $conn->prepare("DELETE FROM `guide_posts` WHERE id = ? LIMIT 1");
    if (!$stmt) { dbg("Delete prepare failed: " . $conn->error); $_SESSION['message']='DB error.'; $_SESSION['message_type']='danger'; header('Location: manage_guides.php'); exit(); }
    $stmt->bind_param('i', $id);
    if (!$stmt->execute()) { dbg("Delete execute failed: " . $stmt->error); $_SESSION['message']='Failed to delete.'; $_SESSION['message_type']='danger'; }
    else { $_SESSION['message']='Guide deleted.'; $_SESSION['message_type']='success'; }
    $stmt->close();
    header('Location: manage_guides.php'); exit();
}

// ---------- CREATE or UPDATE ----------
if ($action !== 'create' && $action !== 'update') {
    $_SESSION['message'] = 'Invalid action.';
    $_SESSION['message_type'] = 'danger';
    header('Location: manage_guides.php');
    exit();
}

// CSRF validation for create/update
if (empty($_SESSION['csrf_token']) || empty($posted_csrf) || !hash_equals($_SESSION['csrf_token'], $posted_csrf)) {
    $_SESSION['message'] = 'Invalid CSRF token.';
    $_SESSION['message_type'] = 'danger';
    header('Location: manage_guides.php'); exit();
}

// Collect inputs
$id = intval($_POST['id'] ?? 0); // for update
$title = trim($_POST['title'] ?? '');
$category_id = intval($_POST['category_id'] ?? 0);
$short_description = trim($_POST['short_description'] ?? '');
$status = in_array($_POST['status'] ?? '', ['published','draft','archived']) ? $_POST['status'] : 'published';
$priority = intval($_POST['priority'] ?? 0);
$is_popular = isset($_POST['is_popular']) && $_POST['is_popular'] == '1' ? 1 : 0;
$is_featured = isset($_POST['is_featured']) && $_POST['is_featured'] == '1' ? 1 : 0;
$external_id = trim($_POST['external_id'] ?? '');
$content_blocks_json_input = trim($_POST['content_blocks_json'] ?? '');

// Content: prefer hidden field populated by JS, fallback to textarea, fallback scanning
$content = $_POST['content_hidden'] ?? null;
if ($content === null) $content = $_POST['content'] ?? null;
if (($content === null || $content === '') && !empty($_POST)) {
    foreach ($_POST as $k => $v) {
        if (in_array($k, ['csrf_token','action','title','category_id','short_description','status','priority','is_popular','is_featured','external_id','tags','id','existing_images','existing_videos','remove_images','remove_videos'])) continue;
        if (is_string($v) && (strpos($v, '<p') !== false || strpos($v, '<div') !== false || strlen($v) > 200)) { $content = $v; dbg("Fallback content from $k"); break; }
    }
}
if ($content === null) $content = '';

// ----------------- TAGS NORMALIZATION -----------------
// Normalize tags server-side: accept hashtag-style "#Tag1 #Tag2", comma-separated "Tag1, Tag2",
// space-separated "Tag1 Tag2", or JSON array input.
// Returns array of cleaned tag strings (preserve casing as first-seen, dedupe case-insensitively)
function normalize_tags_array($raw) {
    $raw = trim((string)$raw);
    if ($raw === '') return [];

    // If input is JSON array, accept it directly
    $try = json_decode($raw, true);
    if (json_last_error() === JSON_ERROR_NONE && is_array($try)) {
        $arr = array_values($try);
    } else {
        $arr = null;
    }

    $tags = [];
    if (is_array($arr)) {
        foreach ($arr as $it) {
            if (!is_string($it)) continue;
            $tags[] = $it;
        }
    } else {
        // extract hashtags first
        if (preg_match_all('/#([^\s#,]+)/u', $raw, $matches) && !empty($matches[1])) {
            foreach ($matches[1] as $t) $tags[] = $t;
        } else {
            // Otherwise split by comma if present, else by whitespace
            if (strpos($raw, ',') !== false) {
                $parts = array_map('trim', explode(',', $raw));
            } else {
                $parts = preg_split('/\s+/u', $raw, -1, PREG_SPLIT_NO_EMPTY);
            }
            foreach ($parts as $p) {
                $p = preg_replace('/^#/', '', $p); // allow leading '#'
                $tags[] = $p;
            }
        }
    }

    // Clean tags: trim surrounding punctuation, collapse internal whitespace, enforce limits
    $clean = [];
    $seen = [];
    foreach ($tags as $t) {
        $t = trim((string)$t);
        if ($t === '') continue;
        // remove surrounding characters that are not letters/numbers/underscore/dash (unicode aware)
        $t = preg_replace('/^[^\p{L}\p{N}_-]+|[^\p{L}\p{N}_-]+$/u', '', $t);
        // collapse whitespace inside
        $t = preg_replace('/\s+/u', ' ', $t);
        $t = trim($t);
        if ($t === '') continue;
        // enforce maximum per-tag length
        if (mb_strlen($t) > 80) $t = mb_substr($t, 0, 80);
        $key = mb_strtolower($t);
        if (!isset($seen[$key])) {
            $seen[$key] = true;
            $clean[] = $t;
        }
        if (count($clean) >= 50) break; // safety cap
    }
    return $clean;
}

// Acquire raw tags input and normalize
$tags_input = trim($_POST['tags'] ?? '');
$tags_array = [];
if ($tags_input !== '') {
    $tags_array = normalize_tags_array($tags_input);
}
$tags_json = empty($tags_array) ? null : json_encode(array_values($tags_array), JSON_UNESCAPED_UNICODE);
// -----------------------------------------------------

// Helper to process uploaded files (returns web path or null)
function handle_single_upload($fileField, $uploadDir, $uploadWebBase) {
    if (empty($_FILES[$fileField]) || !is_uploaded_file($_FILES[$fileField]['tmp_name'])) return null;
    $f = $_FILES[$fileField];
    if ($f['error'] !== UPLOAD_ERR_OK) { return null; }
    $ext = pathinfo($f['name'], PATHINFO_EXTENSION);
    $fname = time() . '_' . uniqid() . '_' . preg_replace('/[^A-Za-z0-9\-\_\.]/','_', pathinfo($f['name'], PATHINFO_FILENAME)) . '.' . $ext;
    $dest = $uploadDir . $fname;
    if (move_uploaded_file($f['tmp_name'], $dest)) return $uploadWebBase . $fname;
    return null;
}
function handle_multiple_uploads($fieldName, $uploadDir, $uploadWebBase) {
    $out = [];
    if (empty($_FILES[$fieldName]) || !is_array($_FILES[$fieldName]['tmp_name'])) return $out;
    foreach ($_FILES[$fieldName]['tmp_name'] as $i => $tmp) {
        if (!is_uploaded_file($tmp)) continue;
        $err = $_FILES[$fieldName]['error'][$i] ?? 1;
        if ($err !== UPLOAD_ERR_OK) continue;
        $orig = $_FILES[$fieldName]['name'][$i] ?? 'file';
        $ext = pathinfo($orig, PATHINFO_EXTENSION);
        $fname = time() . '_' . uniqid() . '_' . preg_replace('/[^A-Za-z0-9\-\_\.]/','_', pathinfo($orig, PATHINFO_FILENAME)) . '.' . $ext;
        $dest = $uploadDir . $fname;
        if (move_uploaded_file($tmp, $dest)) $out[] = $uploadWebBase . $fname;
        else dbg("move failed for $orig");
    }
    return $out;
}

function detect_video_provider($url) {
    $u = strtolower(trim((string)$url));
    if ($u === '') return 'mp4';
    if (strpos($u, 'youtube.com') !== false || strpos($u, 'youtu.be') !== false) return 'youtube';
    if (strpos($u, 'vimeo.com') !== false) return 'vimeo';
    return 'mp4';
}

function normalize_content_blocks($blocks, $uploadedBlockImages = []) {
    if (!is_array($blocks)) return [];
    $allowedTypes = ['hero','h1','h2','h3','p','image','video','callout','ol','ul','divider'];
    $out = [];

    foreach ($blocks as $b) {
        if (!is_array($b)) continue;
        $type = strtolower(trim((string)($b['type'] ?? '')));
        if (!in_array($type, $allowedTypes, true)) continue;

        $entry = ['type' => $type];

        if (in_array($type, ['h1','h2','h3','p'], true)) {
            $text = trim((string)($b['text'] ?? ''));
            if ($text === '') continue;
            $entry['text'] = $text;
        } elseif ($type === 'hero' || $type === 'image') {
            $token = trim((string)($b['uploadToken'] ?? ''));
            $url = trim((string)($b['url'] ?? ($b['image'] ?? '')));
            if ($token !== '' && !empty($uploadedBlockImages[$token])) {
                $url = $uploadedBlockImages[$token];
            }
            if ($url === '') continue;
            if ($type === 'hero') {
                $entry['image'] = $url;
            } else {
                $entry['url'] = $url;
            }
            $caption = trim((string)($b['caption'] ?? ''));
            $alt = trim((string)($b['alt'] ?? ''));
            if ($caption !== '') $entry['caption'] = $caption;
            if ($alt !== '') $entry['alt'] = $alt;
        } elseif ($type === 'video') {
            $url = trim((string)($b['url'] ?? ''));
            if ($url === '') continue;
            $entry['url'] = $url;
            $provider = strtolower(trim((string)($b['provider'] ?? '')));
            if ($provider === '') $provider = detect_video_provider($url);
            if (!in_array($provider, ['mp4','youtube','vimeo'], true)) $provider = 'mp4';
            $entry['provider'] = $provider;
            $caption = trim((string)($b['caption'] ?? ''));
            if ($caption !== '') $entry['caption'] = $caption;
        } elseif ($type === 'callout') {
            $title = trim((string)($b['title'] ?? 'Expert advice'));
            $text = trim((string)($b['text'] ?? ''));
            if ($text === '') continue;
            $entry['title'] = $title !== '' ? $title : 'Expert advice';
            $entry['text'] = $text;
        } elseif ($type === 'ol' || $type === 'ul') {
            $items = $b['items'] ?? [];
            if (!is_array($items)) {
                $items = preg_split('/\r\n|\r|\n/', (string)$items);
            }
            $cleanItems = [];
            foreach ($items as $it) {
                $v = trim((string)$it);
                if ($v !== '') $cleanItems[] = $v;
            }
            if (empty($cleanItems)) continue;
            $entry['items'] = array_values($cleanItems);
        }

        $out[] = $entry;
    }

    return $out;
}

function handle_block_image_uploads($uploadDir, $uploadWebBase) {
    $map = [];
    foreach ($_FILES as $field => $file) {
        if (strpos((string)$field, 'block_image_upload_') !== 0) continue;
        if (empty($file['tmp_name']) || !is_uploaded_file($file['tmp_name'])) continue;
        if (($file['error'] ?? UPLOAD_ERR_NO_FILE) !== UPLOAD_ERR_OK) continue;

        $token = substr((string)$field, strlen('block_image_upload_'));
        $orig = (string)($file['name'] ?? 'image');
        $ext = pathinfo($orig, PATHINFO_EXTENSION);
        $base = pathinfo($orig, PATHINFO_FILENAME);
        $fname = time() . '_' . uniqid() . '_' . safe_filename($base);
        if ($ext !== '') $fname .= '.' . $ext;
        $dest = $uploadDir . $fname;
        if (move_uploaded_file($file['tmp_name'], $dest)) {
            $map[$token] = $uploadWebBase . $fname;
        }
    }
    return $map;
}

// For update: fetch existing row to know current image_url1/images/videos
$existing_row = null;
if ($action === 'update') {
    if ($id <= 0) { $_SESSION['message']='Invalid id for update.'; $_SESSION['message_type']='danger'; header('Location: manage_guides.php'); exit(); }
    $stmt = $conn->prepare("SELECT * FROM `guide_posts` WHERE id = ? LIMIT 1");
    if ($stmt) {
        $stmt->bind_param('i', $id);
        $stmt->execute();
        $res = $stmt->get_result();
        $existing_row = $res ? $res->fetch_assoc() : null;
        $stmt->close();
    }
    if (!$existing_row) { $_SESSION['message']='Guide not found for update.'; $_SESSION['message_type']='danger'; header('Location: manage_guides.php'); exit(); }
}

// --- handle primary image replacement ---
$image_url1_new = handle_single_upload('image1', $uploadDir, $uploadWebBase);

// --- handle multiple new images/videos ---
$new_images = handle_multiple_uploads('guide_images', $uploadDir, $uploadWebBase);
$new_videos = handle_multiple_uploads('guide_videos', $uploadDir, $uploadWebBase);
$uploaded_block_images = handle_block_image_uploads($uploadDir, $uploadWebBase);

$content_blocks_json = null;
if ($content_blocks_json_input !== '') {
    $decoded_blocks = json_decode($content_blocks_json_input, true);
    if (json_last_error() === JSON_ERROR_NONE && is_array($decoded_blocks)) {
        $normalized_blocks = normalize_content_blocks($decoded_blocks, $uploaded_block_images);
        if (!empty($normalized_blocks)) {
            $content_blocks_json = json_encode($normalized_blocks, JSON_UNESCAPED_UNICODE);
        } else {
            $content_blocks_json = json_encode([], JSON_UNESCAPED_UNICODE);
        }
    }
}

// For UPDATE: build final images list combining existing_images[] (from form), removing remove_images[], appending new_images
$final_images = [];
$keep_existing_images = $_POST['existing_images'] ?? []; // values are file paths relative (we used ltrim in edit form)
if (!is_array($keep_existing_images)) $keep_existing_images = [$keep_existing_images];
$remove_images = $_POST['remove_images'] ?? [];
if (!is_array($remove_images)) $remove_images = [$remove_images];

// Normalize: ensure leading slash for compare
$normalized_keep = array_map(function($v){ return '/' . ltrim($v, "/"); }, array_values(array_filter($keep_existing_images)));
$normalized_remove = array_map(function($v){ return '/' . ltrim($v, "/"); }, array_values(array_filter($remove_images)));

foreach ($normalized_keep as $p) {
    if (!in_array($p, $normalized_remove)) $final_images[] = $p;
}
// append new uploads
foreach ($new_images as $ni) $final_images[] = $ni;
$final_images = array_values(array_unique(array_filter($final_images)));
$images_json_final = empty($final_images) ? null : json_encode($final_images, JSON_UNESCAPED_UNICODE);

// Videos similar
$final_videos = [];
$keep_existing_videos = $_POST['existing_videos'] ?? [];
if (!is_array($keep_existing_videos)) $keep_existing_videos = [$keep_existing_videos];
$remove_videos = $_POST['remove_videos'] ?? [];
if (!is_array($remove_videos)) $remove_videos = [$remove_videos];
$normalized_keep_v = array_map(function($v){ return '/' . ltrim($v, "/"); }, array_values(array_filter($keep_existing_videos)));
$normalized_remove_v = array_map(function($v){ return '/' . ltrim($v, "/"); }, array_values(array_filter($remove_videos)));

foreach ($normalized_keep_v as $p) {
    if (!in_array($p, $normalized_remove_v)) $final_videos[] = $p;
}
foreach ($new_videos as $nv) $final_videos[] = $nv;
$final_videos = array_values(array_unique(array_filter($final_videos)));
$videos_json_final = empty($final_videos) ? null : json_encode($final_videos, JSON_UNESCAPED_UNICODE);

// For update: decide image_url1 to persist
if ($action === 'update') {
    $current_image_url1 = $existing_row['image_url1'] ?? null;
    $image_url1_to_save = $image_url1_new ?? $current_image_url1;
} else {
    $image_url1_to_save = $image_url1_new ?? null;
}

$now = date('Y-m-d H:i:s');

// Columns list (based on your table screenshot)
$columns = [
    'title','category_id','short_description','content','tags',
    'image_url1','images','videos','content_blocks','status','priority','is_popular','is_featured','external_id'
];

if ($action === 'create') {
    $cols_with_ts = array_merge($columns, ['created_at','updated_at']);
    $placeholders = implode(',', array_fill(0, count($cols_with_ts), '?'));
    $sql = "INSERT INTO `guide_posts` (`" . implode('`,`', $cols_with_ts) . "`) VALUES ($placeholders)";
    $stmt = $conn->prepare($sql);
    if (!$stmt) { dbg("Insert prepare failed: " . $conn->error); $_SESSION['message']='DB error'; $_SESSION['message_type']='danger'; header('Location: manage_guides.php'); exit(); }

    $params = [
        $title,
        $category_id,
        $short_description,
        $content,
        $tags_json,
        $image_url1_to_save,
        $images_json_final,
        $videos_json_final,
        $content_blocks_json,
        $status,
        $priority,
        $is_popular,
        $is_featured,
        $external_id,
        $now,
        $now
    ];

    // build types
    $types = '';
    foreach ($params as $idx => $p) {
        if (in_array($idx, [1,10,11,12])) $types .= 'i'; else $types .= 's';
    }
    $bind = []; $bind[] = $types;
    foreach ($params as $i => $v) $bind[] = &$params[$i];
    call_user_func_array([$stmt, 'bind_param'], $bind);

    if (!$stmt->execute()) { dbg("Insert failed: " . $stmt->error); $_SESSION['message']='Failed to save guide.'; $_SESSION['message_type']='danger'; $stmt->close(); header('Location: manage_guides.php'); exit(); }
    $insert_id = $stmt->insert_id;
    $stmt->close();
    $_SESSION['message']='Guide saved successfully.'; $_SESSION['message_type']='success';
    header('Location: manage_guides.php'); exit();
}

// UPDATE path
if ($action === 'update') {
    // Prepare update SQL setting columns (not touching created_at)
    $setParts = [];
    foreach ($columns as $c) $setParts[] = "`$c` = ?";
    $setParts[] = "`updated_at` = ?";
    $sql = "UPDATE `guide_posts` SET " . implode(', ', $setParts) . " WHERE id = ? LIMIT 1";
    $stmt = $conn->prepare($sql);
    if (!$stmt) { dbg("Update prepare failed: " . $conn->error); $_SESSION['message']='DB error'; $_SESSION['message_type']='danger'; header('Location: manage_guides.php'); exit(); }

    $params = [
        $title,
        $category_id,
        $short_description,
        $content,
        $tags_json,
        $image_url1_to_save,
        $images_json_final,
        $videos_json_final,
        $content_blocks_json,
        $status,
        $priority,
        $is_popular,
        $is_featured,
        $external_id,
        $now,
        $id // WHERE id = ?
    ];

    // build types string with explicit integer positions: category_id (1), priority (10), is_popular (11), is_featured (12), and id (15)
    $types = '';
    foreach ($params as $idx => $p) {
        if (in_array($idx, [1,10,11,12,15])) $types .= 'i'; else $types .= 's';
    }

    $bind = []; $bind[] = $types;
    foreach ($params as $i => $v) $bind[] = &$params[$i];
    call_user_func_array([$stmt, 'bind_param'], $bind);

    if (!$stmt->execute()) {
        dbg("Update failed: " . $stmt->error);
        $_SESSION['message'] = 'Failed to update guide: ' . htmlspecialchars($stmt->error);
        $_SESSION['message_type'] = 'danger';
        $stmt->close();
        header('Location: manage_guides.php');
        exit();
    }
    $stmt->close();
    $_SESSION['message'] = 'Guide updated successfully.';
    $_SESSION['message_type'] = 'success';
    header('Location: manage_guides.php');
    exit();
}

// Fallback
header('Location: manage_guides.php');
exit();
