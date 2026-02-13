<?php
// views/ai_posts/delete_ai_post.php
session_start();
require '../../config.php';
if (!isset($_SESSION['admin'])) { header('Location: ../../login.php'); exit(); }

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    $_SESSION['message'] = 'Invalid request.';
    $_SESSION['message_type'] = 'danger';
    header('Location: manage_ai_posts.php'); exit();
}

$token = $_POST['csrf_token'] ?? '';
if (empty($token) || empty($_SESSION['csrf_token']) || !hash_equals($_SESSION['csrf_token'], $token)) {
    $_SESSION['message'] = 'Invalid CSRF token.';
    $_SESSION['message_type'] = 'danger';
    header('Location: manage_ai_posts.php'); exit();
}

$id = intval($_POST['id'] ?? 0);
if ($id <= 0) {
    $_SESSION['message'] = 'Invalid id.';
    $_SESSION['message_type'] = 'danger';
    header('Location: manage_ai_posts.php'); exit();
}

// helper: delete stored file (accepts full URL or relative path)
function delete_fs_file($storedPath) {
    if (empty($storedPath)) return false;
    if (preg_match('#^https?://#', $storedPath)) {
        $p = parse_url($storedPath);
        $storedPath = $p['path'] ?? $storedPath;
    }
    $rel = ltrim($storedPath, '/');
    $fs = rtrim($_SERVER['DOCUMENT_ROOT'], '/') . '/' . $rel;
    if (file_exists($fs) && is_file($fs)) {
        try { @unlink($fs); return true; } catch (Throwable $e) { error_log("delete_fs_file unlink error: ".$e->getMessage()); }
    }
    return false;
}

try {
    // fetch images for cleanup
    $stmt = $conn->prepare("SELECT image_url1, image_url2 FROM ai_posts WHERE id = ? LIMIT 1");
    if ($stmt === false) {
        error_log("delete_ai_post prepare failed: " . $conn->error);
        $_SESSION['message'] = 'Server error.';
        $_SESSION['message_type'] = 'danger';
        header('Location: manage_ai_posts.php'); exit();
    }
    $stmt->bind_param('i', $id);
    $stmt->execute();
    $res = $stmt->get_result();
    $row = $res ? $res->fetch_assoc() : null;
    $stmt->close();

    if (!$row) {
        $_SESSION['message'] = 'AI Prompt not found.';
        $_SESSION['message_type'] = 'warning';
        header('Location: manage_ai_posts.php'); exit();
    }

    // delete files if present
    foreach (['image_url1','image_url2'] as $col) {
        if (!empty($row[$col])) {
            delete_fs_file($row[$col]);
        }
    }

    // If your schema keeps tag relationships in a separate table (e.g. ai_post_tags),
    // attempt to remove those associations as well. This operation is optional and
    // will be attempted only if the table exists. Any errors will be logged but
    // won't stop the main delete operation.
    try {
        $tblCheck = $conn->query("SHOW TABLES LIKE 'ai_post_tags'");
        if ($tblCheck && $tblCheck->num_rows > 0) {
            $delTags = $conn->prepare("DELETE FROM ai_post_tags WHERE ai_post_id = ?");
            if ($delTags) {
                $delTags->bind_param('i', $id);
                $delTags->execute();
                $delTags->close();
            } else {
                error_log("delete_ai_post: failed to prepare DELETE FROM ai_post_tags: " . $conn->error);
            }
        }
    } catch (Throwable $e) {
        // non-fatal: log and continue
        error_log("delete_ai_post: error deleting tag associations: " . $e->getMessage());
    }

    // delete DB row
    $del = $conn->prepare("DELETE FROM ai_posts WHERE id = ? LIMIT 1");
    if ($del === false) {
        error_log("delete_ai_post delete prepare failed: " . $conn->error);
        $_SESSION['message'] = 'Server error.';
        $_SESSION['message_type'] = 'danger';
        header('Location: manage_ai_posts.php'); exit();
    }
    $del->bind_param('i', $id);
    $ok = $del->execute();
    $del->close();

    if ($ok) {
        $_SESSION['message'] = 'AI Prompt deleted successfully.';
        $_SESSION['message_type'] = 'success';
    } else {
        error_log("delete_ai_post execute failed: " . $conn->error);
        $_SESSION['message'] = 'Error deleting AI Prompt.';
        $_SESSION['message_type'] = 'danger';
    }

    header('Location: manage_ai_posts.php');
    exit();

} catch (Throwable $ex) {
    error_log("delete_ai_post exception: " . $ex->getMessage());
    $_SESSION['message'] = 'Unexpected server error. Check logs.';
    $_SESSION['message_type'] = 'danger';
    header('Location: manage_ai_posts.php');
    exit();
}