<?php
require_once __DIR__ . '/../lib/v2_progress.php';
require_once __DIR__ . '/../lib/v2_personalization.php';

if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') {
    json_err('Method Not Allowed', 405);
}

$raw = file_get_contents('php://input');
$payload = json_decode($raw ?: '', true);
if (!is_array($payload)) {
    $payload = $_POST ?? [];
}

$postId = (int)($payload['post_id'] ?? $payload['id'] ?? $_GET['id'] ?? 0);
if ($postId <= 0) {
    json_err('Missing or invalid post ID', 400);
}

$updateStmt = $conn->prepare("
    UPDATE ai_posts
    SET copies = COALESCE(copies, 0) + 1
    WHERE id = ?
");
if (!$updateStmt) {
    json_err('Database query preparation failed', 500);
}
$updateStmt->bind_param('i', $postId);
if (!$updateStmt->execute()) {
    $err = $updateStmt->error;
    $updateStmt->close();
    json_err('Failed to increment copy count: ' . $err, 500);
}
$updateStmt->close();

$selectStmt = $conn->prepare('SELECT COALESCE(copies, 0) AS copies FROM ai_posts WHERE id = ? LIMIT 1');
if (!$selectStmt) {
    json_err('Database query preparation failed', 500);
}
$selectStmt->bind_param('i', $postId);
$selectStmt->execute();
$res = $selectStmt->get_result();
$row = $res ? $res->fetch_assoc() : null;
$selectStmt->close();

if (!$row) {
    json_err('Post not found after update', 404);
}

$userId = v2_progress_optional_user_id($conn);
if ($userId) {
    $copyRef = trim((string)($payload['copy_ref_id'] ?? ''));
    if ($copyRef === '') {
        $copyRef = $postId . ':' . date('YmdHis');
    }
    $signalTags = v2_personalization_load_post_signals($conn, $postId);
    $conn->begin_transaction();
    try {
        award_xp($conn, $userId, 'COPY_PROMPT', 2, 'copy_prompt_xp', $copyRef);
        update_user_tag_scores($conn, $userId, $signalTags, 3);
        $conn->commit();
    } catch (Throwable $e) {
        $conn->rollback();
        error_log('increment_copy xp award failed: ' . $e->getMessage());
    }
}

json_ok([
    'success' => true,
    'copies' => (int)$row['copies'],
]);
