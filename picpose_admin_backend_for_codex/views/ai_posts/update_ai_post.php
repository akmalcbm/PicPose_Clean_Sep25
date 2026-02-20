<?php
// views/ai_posts/update_ai_post.php
session_start();
require '../../config.php';
if (!isset($_SESSION['admin'])) { header('Location: ../../login.php'); exit(); }

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    header('Location: manage_ai_posts.php');
    exit();
}

$post_id = intval($_POST['post_id'] ?? 0);
if ($post_id <= 0) {
    $_SESSION['message'] = 'Invalid AI post id.';
    $_SESSION['message_type'] = 'danger';
    header('Location: manage_ai_posts.php');
    exit();
}

// fetch existing AI post
$stmt = $conn->prepare("SELECT * FROM ai_posts WHERE id = ? LIMIT 1");
$stmt->bind_param("i", $post_id);
$stmt->execute();
$res = $stmt->get_result();
$existing = $res->fetch_assoc();
$stmt->close();
if (!$existing) {
    $_SESSION['message'] = 'AI Prompt not found.';
    $_SESSION['message_type'] = 'danger';
    header('Location: manage_ai_posts.php');
    exit();
}

// upload helper
function upload_image_field($fieldName) {
    if (!isset($_FILES[$fieldName]) || empty($_FILES[$fieldName]['name'])) return null;
    $file = $_FILES[$fieldName];
    if ($file['error'] !== UPLOAD_ERR_OK) return null;

    $finfo = finfo_open(FILEINFO_MIME_TYPE);
    $mime = finfo_file($finfo, $file['tmp_name']);
    finfo_close($finfo);

    $allowed = ['image/jpeg'=>'jpg','image/png'=>'png','image/webp'=>'webp'];
    if (!isset($allowed[$mime])) return null;

    $uploadDirFs = rtrim($_SERVER['DOCUMENT_ROOT'], '/') . '/uploads/ai_posts_pic/';
    if (!is_dir($uploadDirFs)) mkdir($uploadDirFs, 0755, true);

    $ext = $allowed[$mime];
    $filename = time() . '_' . bin2hex(random_bytes(6)) . '.' . $ext;
    $targetRel = 'uploads/ai_posts_pic/' . $filename;
    $targetFs  = $uploadDirFs . $filename;

    if (!move_uploaded_file($file['tmp_name'], $targetFs)) return null;
    return $targetRel;
}

// sanitize inputs
$title = trim($_POST['title'] ?? '');
$category_id = intval($_POST['category_id'] ?? 0);
$short_description = trim($_POST['short_description'] ?? '');
$prompt_text_raw = $_POST['prompt_text'] ?? '';
$tags_raw = trim($_POST['tags'] ?? '');
$status = in_array($_POST['status'] ?? 'published',['published','blocked','draft','archived']) ? $_POST['status'] : 'published';
$priority = intval($_POST['priority'] ?? 0);
$is_popular = !empty($_POST['is_popular']) ? 1 : 0;
$is_featured = !empty($_POST['is_featured']) ? 1 : 0;
$external_id = trim($_POST['external_id'] ?? '');

// Normalize tags server-side: accept "#Tag1 #Tag2", "Tag1, Tag2", "Tag1 Tag2"
function normalize_tags_array($raw) {
    $raw = trim((string)$raw);
    if ($raw === '') return [];

    $tags = [];

    // If explicit hashtags present, extract them
    if (preg_match_all('/#([^\s#,]+)/u', $raw, $matches) && !empty($matches[1])) {
        foreach ($matches[1] as $t) $tags[] = $t;
    } else {
        // No explicit hashtags found: try comma-split then whitespace split
        if (strpos($raw, ',') !== false) {
            $parts = array_map('trim', explode(',', $raw));
        } else {
            $parts = preg_split('/\s+/u', $raw, -1, PREG_SPLIT_NO_EMPTY);
        }
        foreach ($parts as $p) {
            $p = preg_replace('/^#/', '', $p); // allow leading '#' if present
            $tags[] = $p;
        }
    }

    // Clean and dedupe (case-insensitive)
    $clean = [];
    $seen = [];
    foreach ($tags as $t) {
        $t = trim((string)$t);
        if ($t === '') continue;
        // remove surrounding punctuation but allow Unicode letters/numbers/underscore/dash inside
        $t = preg_replace('/^[^\p{L}\p{N}_-]+|[^\p{L}\p{N}_-]+$/u', '', $t);
        $t = preg_replace('/\s+/u', ' ', $t);
        $t = trim($t);
        if ($t === '') continue;
        if (mb_strlen($t) > 80) $t = mb_substr($t, 0, 80);
        $key = mb_strtolower($t);
        if (!isset($seen[$key])) {
            $seen[$key] = true;
            $clean[] = $t;
        }
        if (count($clean) >= 50) break;
    }
    return $clean;
}

// process tags into JSON (or NULL if empty)
$tags_json = null;
$tags_array = normalize_tags_array($tags_raw);
if (!empty($tags_array)) {
    $tags_json = json_encode($tags_array, JSON_UNESCAPED_UNICODE);
}

// escape values for SQL
$title_es = $conn->real_escape_string($title);
$short_description_es = $short_description !== '' ? $conn->real_escape_string($short_description) : null;
$prompt_text_es = $conn->real_escape_string($prompt_text_raw);
$status_es = $conn->real_escape_string($status);
$external_id_es = $external_id !== '' ? $conn->real_escape_string($external_id) : null;
$tags_json_es = $tags_json !== null ? $conn->real_escape_string($tags_json) : null;

// handle uploads + cleanup
$replace1 = upload_image_field('image1');
$replace2 = upload_image_field('image2');

$updateParts = [];
if ($title !== '') $updateParts[] = "title = '{$title_es}'";
$updateParts[] = "category_id = {$category_id}";
$updateParts[] = "status = '{$status_es}'";
$updateParts[] = "priority = {$priority}";
$updateParts[] = "is_popular = {$is_popular}";
$updateParts[] = "is_featured = {$is_featured}";
$updateParts[] = "external_id = " . ($external_id_es !== null ? "'{$external_id_es}'" : "NULL");
$updateParts[] = "short_description = " . ($short_description_es !== null ? "'{$short_description_es}'" : "NULL");
$updateParts[] = "prompt_text = '{$prompt_text_es}'";
if ($tags_json_es !== null) {
    $updateParts[] = "tags = '{$tags_json_es}'";
} else {
    // If the form explicitly submitted an empty tags field, you might want to clear tags.
    // Here, if the incoming tags_raw was an empty string we set tags to NULL.
    if ($tags_raw === '') {
        $updateParts[] = "tags = NULL";
    }
}

if ($replace1) {
    if (!empty($existing['image_url1'])) {
        $old = ltrim(parse_url($existing['image_url1'], PHP_URL_PATH) ?? $existing['image_url1'],'/');
        $oldFs = rtrim($_SERVER['DOCUMENT_ROOT'],'/') . '/' . $old;
        if (file_exists($oldFs)) @unlink($oldFs);
    }
    $updateParts[] = "image_url1 = '" . $conn->real_escape_string($replace1) . "'";
}
if ($replace2) {
    if (!empty($existing['image_url2'])) {
        $old = ltrim(parse_url($existing['image_url2'], PHP_URL_PATH) ?? $existing['image_url2'],'/');
        $oldFs = rtrim($_SERVER['DOCUMENT_ROOT'],'/') . '/' . $old;
        if (file_exists($oldFs)) @unlink($oldFs);
    }
    $updateParts[] = "image_url2 = '" . $conn->real_escape_string($replace2) . "'";
}

// run update
if (!empty($updateParts)) {
    $sql = "UPDATE ai_posts SET " . implode(', ', $updateParts) . " WHERE id = {$post_id} LIMIT 1";
    if (!$conn->query($sql)) {
        error_log("update_ai_post failed: " . $conn->error . " SQL: $sql");
        $_SESSION['message'] = 'Error updating AI post.';
        $_SESSION['message_type'] = 'danger';
        header('Location: edit_ai_post.php?id=' . $post_id);
        exit();
    }
}

$_SESSION['message'] = 'AI post updated successfully.';
$_SESSION['message_type'] = 'success';
header('Location: manage_ai_posts.php');
exit();
