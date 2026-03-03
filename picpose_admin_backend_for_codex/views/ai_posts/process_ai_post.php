<?php
// views/ai_posts/process_ai_post.php
session_start();
require '../../config.php';

// Ensure admin
if (!isset($_SESSION['admin'])) {
    http_response_code(403);
    die('Access denied.');
}

// Utility: set session message and redirect
function redirect_with_msg($url, $msg, $type = 'info') {
    $_SESSION['message'] = $msg;
    $_SESSION['message_type'] = $type;
    header('Location: ' . $url);
    exit;
}

// CSRF check
$csrf_post = $_POST['csrf_token'] ?? '';
if (empty($csrf_post) || empty($_SESSION['csrf_token']) || !hash_equals($_SESSION['csrf_token'], $csrf_post)) {
    // possible CSRF, block
    http_response_code(400);
    error_log("process_ai_post: CSRF failed for admin=" . ($_SESSION['admin'] ?? 'unknown'));
    redirect_with_msg('manage_ai_posts.php', 'Invalid CSRF token. Please try again.', 'danger');
}

// helper: safe filename and upload (for ai images)
function upload_image_field($fieldName) {
    if (!isset($_FILES[$fieldName]) || empty($_FILES[$fieldName]['name'])) return null;
    $file = $_FILES[$fieldName];
    if ($file['error'] !== UPLOAD_ERR_OK) return null;

    // validate mime
    $finfo = finfo_open(FILEINFO_MIME_TYPE);
    $mime = finfo_file($finfo, $file['tmp_name']);
    finfo_close($finfo);
    $allowed = [
        'image/jpeg' => 'jpg',
        'image/png'  => 'png',
        'image/webp' => 'webp'
    ];
    if (!isset($allowed[$mime])) {
        return ['error' => 'unsupported_type'];
    }

    // ensure upload dir
    $uploadDir = __DIR__ . '/../../uploads/ai_posts_pic/';
    if (!is_dir($uploadDir)) {
        if (!mkdir($uploadDir, 0755, true) && !is_dir($uploadDir)) {
            return ['error' => 'mkdir_failed'];
        }
    }

    $ext = $allowed[$mime];
    // time + random
    $filename = time() . '_' . bin2hex(random_bytes(6)) . '.' . $ext;
    $targetRel = 'uploads/ai_posts_pic/' . $filename;
    $targetFs  = rtrim($_SERVER['DOCUMENT_ROOT'], '/') . '/' . ltrim($targetRel, '/');

    if (!move_uploaded_file($file['tmp_name'], $targetFs)) {
        return ['error' => 'move_failed'];
    }

    return ['path' => $targetRel];
}

// helper: delete file given stored path (absolute or relative)
function delete_fs_file($storedPath) {
    if (empty($storedPath)) return false;
    // if full URL, extract path
    if (preg_match('#^https?://#', $storedPath)) {
        $parsed = parse_url($storedPath);
        $storedPath = $parsed['path'] ?? $storedPath;
    }
    // remove leading slash
    $rel = ltrim($storedPath, '/');
    $fs = rtrim($_SERVER['DOCUMENT_ROOT'], '/') . '/' . $rel;
    if (file_exists($fs) && is_file($fs)) {
        try { @unlink($fs); } catch(Throwable $e) { error_log("delete_fs_file unlink error: " . $e->getMessage()); }
        return true;
    }
    return false;
}

// sanitize text input helper
function input_trim($k) {
    return isset($_POST[$k]) ? trim($_POST[$k]) : '';
}

// Normalize tags server-side: accept "#Tag1 #Tag2", "Tag1, Tag2", "Tag1 Tag2"
function normalize_tags_array($raw) {
    $raw = trim((string)$raw);
    if ($raw === '') return [];

    $tags = [];

    // If there are explicit hashtags, extract them first
    if (preg_match_all('/#([^\s#,]+)/u', $raw, $matches) && !empty($matches[1])) {
        foreach ($matches[1] as $t) $tags[] = $t;
    } else {
        // No hashtags found, fallback to comma-split or whitespace split
        if (strpos($raw, ',') !== false) {
            $parts = array_map('trim', explode(',', $raw));
        } else {
            // split on whitespace
            $parts = preg_split('/\s+/u', $raw, -1, PREG_SPLIT_NO_EMPTY);
        }
        foreach ($parts as $p) {
            // allow leading '#' in non-hashtag flows (user might mix)
            $p = preg_replace('/^#/', '', $p);
            $tags[] = $p;
        }
    }

    // Clean tags: trim punctuation; allow letters (unicode), numbers, underscores and hyphens inside
    $clean = [];
    $seen = [];
    foreach ($tags as $t) {
        $t = trim((string)$t);
        if ($t === '') continue;
        // remove surrounding punctuation, but keep letters, numbers, underscore, dash
        $t = preg_replace('/^[^\p{L}\p{N}_-]+|[^\p{L}\p{N}_-]+$/u', '', $t);
        // collapse inner whitespace
        $t = preg_replace('/\s+/u', ' ', $t);
        $t = trim($t);
        if ($t === '') continue;
        // enforce max length (e.g., 80 chars)
        if (mb_strlen($t) > 80) $t = mb_substr($t, 0, 80);
        // dedupe case-insensitive, keep first-seen original casing
        $key = mb_strtolower($t);
        if (!isset($seen[$key])) {
            $seen[$key] = true;
            $clean[] = $t;
        }
        // cap total tags to 50 for safety
        if (count($clean) >= 50) break;
    }

    return $clean;
}

// determine action
$action = strtolower(input_trim('action') ?: 'create');

try {
    if ($action === 'publish_single') {
        $id = intval($_POST['id'] ?? 0);
        if ($id <= 0) redirect_with_msg('manage_ai_posts.php?filter=blocked', 'Invalid post id.', 'danger');

        $stmt = $conn->prepare("UPDATE ai_posts SET status='published' WHERE id = ? LIMIT 1");
        if (!$stmt) redirect_with_msg('manage_ai_posts.php?filter=blocked', 'Database error.', 'danger');
        $stmt->bind_param('i', $id);
        $stmt->execute();
        $affected = $stmt->affected_rows;
        $stmt->close();

        if ($affected > 0) {
            redirect_with_msg('manage_ai_posts.php?filter=blocked', 'Blocked prompt republished successfully.', 'success');
        }
        redirect_with_msg('manage_ai_posts.php?filter=blocked', 'Prompt was already published or not found.', 'warning');
    }

    if ($action === 'publish_selected') {
        $idsRaw = $_POST['ids'] ?? '';
        $ids = array_values(array_unique(array_filter(array_map('intval', explode(',', (string)$idsRaw)))));
        if (empty($ids)) {
            redirect_with_msg('manage_ai_posts.php?filter=blocked', 'Select at least one blocked prompt.', 'warning');
        }

        $placeholders = implode(',', array_fill(0, count($ids), '?'));
        $types = str_repeat('i', count($ids));
        $sql = "UPDATE ai_posts SET status='published' WHERE status='blocked' AND id IN ($placeholders)";
        $stmt = $conn->prepare($sql);
        if (!$stmt) redirect_with_msg('manage_ai_posts.php?filter=blocked', 'Database error.', 'danger');
        $stmt->bind_param($types, ...$ids);
        $stmt->execute();
        $affected = $stmt->affected_rows;
        $stmt->close();

        redirect_with_msg('manage_ai_posts.php?filter=blocked', "Republished {$affected} blocked prompt(s).", 'success');
    }

    if ($action === 'bulk_update_tier') {
        $idsRaw = $_POST['ids'] ?? [];
        if (!is_array($idsRaw)) $idsRaw = [$idsRaw];
        $ids = [];
        foreach ($idsRaw as $idRaw) {
            $id = intval($idRaw);
            if ($id > 0) $ids[] = $id;
        }
        $ids = array_values(array_unique($ids));
        if (empty($ids)) {
            redirect_with_msg('manage_ai_posts.php', 'Please select at least one prompt for bulk update.', 'warning');
        }

        $tier = strtoupper(trim((string)($_POST['tier'] ?? '')));
        if (!in_array($tier, ['FREE', 'PREMIUM'], true)) {
            redirect_with_msg('manage_ai_posts.php', 'Invalid bulk tier value.', 'danger');
        }

        $cost = intval($_POST['cost'] ?? 0);
        $pack = trim((string)($_POST['pack'] ?? ''));

        if ($tier === 'FREE') {
            $cost = 0;
            $pack = null;
        } else {
            if ($cost <= 0) $cost = 200;
            if ($pack === '') $pack = null;
            if ($pack !== null && mb_strlen($pack) > 40) {
                $pack = mb_substr($pack, 0, 40);
            }
        }

        $placeholders = implode(',', array_fill(0, count($ids), '?'));

        if ($pack === null) {
            $sql = "UPDATE ai_posts
                    SET tier = ?, premium_unlock_cost_points = ?, premium_pack = NULL
                    WHERE id IN ($placeholders)";
            $types = 'si' . str_repeat('i', count($ids));
            $params = array_merge([$tier, $cost], $ids);
        } else {
            $sql = "UPDATE ai_posts
                    SET tier = ?, premium_unlock_cost_points = ?, premium_pack = ?
                    WHERE id IN ($placeholders)";
            $types = 'sis' . str_repeat('i', count($ids));
            $params = array_merge([$tier, $cost, $pack], $ids);
        }

        $stmt = $conn->prepare($sql);
        if (!$stmt) {
            error_log("process_ai_post bulk_update_tier prepare failed: " . $conn->error);
            redirect_with_msg('manage_ai_posts.php', 'Database error during bulk update.', 'danger');
        }
        $stmt->bind_param($types, ...$params);
        if (!$stmt->execute()) {
            $err = $stmt->error;
            $stmt->close();
            error_log("process_ai_post bulk_update_tier execute failed: " . $err);
            redirect_with_msg('manage_ai_posts.php', 'Failed to apply bulk update.', 'danger');
        }
        $affected = (int)$stmt->affected_rows;
        $stmt->close();

        redirect_with_msg('manage_ai_posts.php', "Bulk tier update applied. Rows affected: {$affected}.", 'success');
    }

    if ($action === 'delete') {
        // deletion via POST (expects id)
        $id = intval($_POST['id'] ?? 0);
        if ($id <= 0) redirect_with_msg('manage_ai_posts.php', 'Invalid post id for deletion.', 'danger');

        // fetch existing images for cleanup from ai_posts table
        $stmt = $conn->prepare("SELECT image_url1, image_url2 FROM ai_posts WHERE id = ? LIMIT 1");
        $stmt->bind_param('i', $id);
        $stmt->execute();
        $res = $stmt->get_result();
        $row = $res ? $res->fetch_assoc() : null;
        $stmt->close();

        if ($row) {
            foreach (['image_url1','image_url2'] as $col) {
                if (!empty($row[$col])) delete_fs_file($row[$col]);
            }
        } else {
            redirect_with_msg('manage_ai_posts.php', 'Post not found.', 'warning');
        }

        // delete DB row
        $del = $conn->prepare("DELETE FROM ai_posts WHERE id = ?");
        $del->bind_param('i', $id);
        $ok = $del->execute();
        $del->close();

        if ($ok) redirect_with_msg('manage_ai_posts.php', 'AI Prompt deleted successfully.', 'success');
        else redirect_with_msg('manage_ai_posts.php', 'Error deleting AI Prompt.', 'danger');
    }

    // For create / update
    // collect common fields
    $title = input_trim('title');
    $category_id = intval($_POST['category_id'] ?? 0);
    $short_description = input_trim('short_description');
    // prompt_text might be HTML from CKEditor
    $prompt_text_raw = $_POST['prompt_text'] ?? '';
    $status = in_array($_POST['status'] ?? 'published', ['published','draft','archived','blocked']) ? $_POST['status'] : 'published';
    $priority = intval($_POST['priority'] ?? 0);
    $is_popular = !empty($_POST['is_popular']) ? 1 : 0;
    $is_featured = !empty($_POST['is_featured']) ? 1 : 0;
    $tier = !empty($_POST['is_premium']) ? 'PREMIUM' : 'FREE';
    $premium_unlock_cost_points = intval($_POST['premium_unlock_cost_points'] ?? 0);
    if ($tier === 'PREMIUM' && $premium_unlock_cost_points <= 0) {
        $premium_unlock_cost_points = 200;
    }
    if ($tier !== 'PREMIUM') {
        $premium_unlock_cost_points = 0;
    }
    $premium_pack = input_trim('premium_pack');
    if ($tier !== 'PREMIUM') {
        $premium_pack = '';
    }
    $external_id = input_trim('external_id');

    // tags: normalize server-side (accept hashtags or comma-separated)
    $tags_raw = input_trim('tags');
    $tags_array = normalize_tags_array($tags_raw);
    $tags_json = '';
    if (!empty($tags_array)) {
        $tags_json = json_encode($tags_array, JSON_UNESCAPED_UNICODE);
    }

    // escape values for SQL (we use real_escape_string where embedding)
    $title_es = $conn->real_escape_string($title);
    $short_description_es = $short_description !== '' ? $conn->real_escape_string($short_description) : null;
    $prompt_text_es = $conn->real_escape_string($prompt_text_raw);
    $status_es = $conn->real_escape_string($status);
    $tier_es = $conn->real_escape_string($tier);
    $premium_pack_es = $premium_pack !== '' ? $conn->real_escape_string($premium_pack) : null;
    $external_id_es = $external_id !== '' ? $conn->real_escape_string($external_id) : null;
    $tags_json_es = $tags_json !== '' ? $conn->real_escape_string($tags_json) : null;

    if ($action === 'create') {
        // validate minimal inputs
        if ($title === '' || $category_id <= 0) {
            redirect_with_msg('add_ai_post.php', 'Title and Category are required.', 'danger');
        }

        // handle uploads
        $uploaded1 = upload_image_field('image1');
        if (is_array($uploaded1) && isset($uploaded1['error'])) {
            redirect_with_msg('add_ai_post.php', 'Primary image upload failed: ' . $uploaded1['error'], 'danger');
        }
        $uploaded2 = upload_image_field('image2');
        if (is_array($uploaded2) && isset($uploaded2['error'])) {
            // optional secondary: just ignore and continue but log
            error_log("process_ai_post: secondary image upload issue: " . $uploaded2['error']);
            $uploaded2 = null;
        }

        // Build insert to ai_posts
        $cols = "title, category_id, short_description, prompt_text, tags, status, priority, is_popular, is_featured, tier, premium_unlock_cost_points, premium_pack, external_id, created_at";
        $vals = "'" . $title_es . "', " .
                intval($category_id) . ", " .
                ($short_description_es !== null ? "'" . $short_description_es . "'" : "NULL") . ", " .
                "'" . $prompt_text_es . "', " .
                ($tags_json_es !== null ? "'" . $tags_json_es . "'" : "NULL") . ", " .
                "'" . $status_es . "', " .
                intval($priority) . ", " .
                intval($is_popular) . ", " .
                intval($is_featured) . ", " .
                "'" . $tier_es . "', " .
                intval($premium_unlock_cost_points) . ", " .
                ($premium_pack_es !== null ? "'" . $premium_pack_es . "'" : "NULL") . ", " .
                ($external_id_es !== null ? "'" . $external_id_es . "'" : "NULL") . ", " .
                "NOW()";

        $res = $conn->query("INSERT INTO ai_posts ({$cols}) VALUES ({$vals})");
        if (!$res) {
            error_log("process_ai_post create insert error: " . $conn->error . " SQL: INSERT INTO ai_posts ({$cols}) VALUES ({$vals})");
            redirect_with_msg('add_ai_post.php', 'Database error while creating AI Prompt.', 'danger');
        }

        $newId = $conn->insert_id;

        // update image columns if files uploaded
        $updateParts = [];
        if (is_array($uploaded1) && isset($uploaded1['path'])) {
            $updateParts[] = "image_url1 = '" . $conn->real_escape_string($uploaded1['path']) . "'";
        }
        if (is_array($uploaded2) && isset($uploaded2['path'])) {
            $updateParts[] = "image_url2 = '" . $conn->real_escape_string($uploaded2['path']) . "'";
        }
        if ($updateParts) {
            $sqlu = "UPDATE ai_posts SET " . implode(', ', $updateParts) . " WHERE id = " . intval($newId);
            if (!$conn->query($sqlu)) {
                error_log("process_ai_post create update images failed: " . $conn->error);
            }
        }

        redirect_with_msg('manage_ai_posts.php', 'AI Prompt created successfully.', 'success');
    }

    if ($action === 'update') {
        $id = intval($_POST['id'] ?? 0);
        if ($id <= 0) redirect_with_msg('manage_ai_posts.php', 'Invalid post id.', 'danger');

        // fetch existing to determine existing images
        $stmt = $conn->prepare("SELECT image_url1, image_url2 FROM ai_posts WHERE id = ? LIMIT 1");
        $stmt->bind_param('i', $id);
        $stmt->execute();
        $res = $stmt->get_result();
        $existing = $res ? $res->fetch_assoc() : null;
        $stmt->close();
        if (!$existing) redirect_with_msg('manage_ai_posts.php', 'Post not found for update.', 'danger');

        // handle uploaded new images (replace)
        $uploaded1 = upload_image_field('image1');
        if (is_array($uploaded1) && isset($uploaded1['error']) && $uploaded1['error'] !== 'unsupported_type') {
            // treat as error for primary image replacement
            error_log("process_ai_post: upload1 error: " . json_encode($uploaded1));
        }
        $uploaded2 = upload_image_field('image2');
        if (is_array($uploaded2) && isset($uploaded2['error'])) {
            error_log("process_ai_post: upload2 error: " . json_encode($uploaded2));
            $uploaded2 = null;
        }

        // handle remove_images[] from form - these are image paths to delete
        $removeImages = $_POST['remove_images'] ?? [];
        if (!is_array($removeImages)) $removeImages = [$removeImages];

        // Build update statement parts
        $parts = [];
        $parts[] = "title = '" . $conn->real_escape_string($title) . "'";
        $parts[] = "category_id = " . intval($category_id);
        $parts[] = "short_description = " . ($short_description_es !== null ? "'" . $short_description_es . "'" : "NULL");
        $parts[] = "prompt_text = '" . $prompt_text_es . "'";
        $parts[] = "status = '" . $status_es . "'";
        $parts[] = "priority = " . intval($priority);
        $parts[] = "is_popular = " . intval($is_popular);
        $parts[] = "is_featured = " . intval($is_featured);
        $parts[] = "tier = '" . $tier_es . "'";
        $parts[] = "premium_unlock_cost_points = " . intval($premium_unlock_cost_points);
        $parts[] = "premium_pack = " . ($premium_pack_es !== null ? "'" . $premium_pack_es . "'" : "NULL");
        $parts[] = "external_id = " . ($external_id_es !== null ? "'" . $external_id_es . "'" : "NULL");

        if ($tags_json_es !== null) {
            $parts[] = "tags = '" . $tags_json_es . "'";
        } else {
            $parts[] = "tags = NULL";
        }

        // If new uploaded image1 -> delete old file and set image_url1
        if (is_array($uploaded1) && isset($uploaded1['path'])) {
            if (!empty($existing['image_url1'])) delete_fs_file($existing['image_url1']);
            $parts[] = "image_url1 = '" . $conn->real_escape_string($uploaded1['path']) . "'";
        }

        // new uploaded image2 -> delete old2, set image_url2
        if (is_array($uploaded2) && isset($uploaded2['path'])) {
            if (!empty($existing['image_url2'])) delete_fs_file($existing['image_url2']);
            $parts[] = "image_url2 = '" . $conn->real_escape_string($uploaded2['path']) . "'";
        }

        // If remove_images[] specified: unset matching columns and delete files
        foreach ($removeImages as $rem) {
            $rem = trim($rem);
            if ($rem === '') continue;
            // compare with each existing column, unset the match
            foreach (['image_url1','image_url2'] as $col) {
                if (!empty($existing[$col]) && rtrim($existing[$col], '/') === rtrim($rem, '/')) {
                    // delete file
                    delete_fs_file($existing[$col]);
                    $parts[] = "{$col} = NULL";
                    // Also set in $existing so we don't delete again
                    $existing[$col] = null;
                }
            }
        }

        // Execute update
        $sql = "UPDATE ai_posts SET " . implode(', ', $parts) . " WHERE id = " . intval($id) . " LIMIT 1";
        if (!$conn->query($sql)) {
            error_log("process_ai_post update error: " . $conn->error . " SQL: " . $sql);
            redirect_with_msg('edit_ai_post.php?id=' . intval($id), 'Database error while updating. Check logs.', 'danger');
        }

        redirect_with_msg('manage_ai_posts.php', 'AI Prompt updated successfully.', 'success');
    }

    // Unknown action
    redirect_with_msg('manage_ai_posts.php', 'Unknown action.', 'danger');

} catch (Throwable $ex) {
    error_log("process_ai_post unexpected error: " . $ex->getMessage());
    redirect_with_msg('manage_ai_posts.php', 'Unexpected server error. Check logs.', 'danger');
}
