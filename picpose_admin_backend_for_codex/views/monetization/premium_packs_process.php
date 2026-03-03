<?php
session_start();
require '../../config.php';

if (!isset($_SESSION['admin'])) {
    http_response_code(403);
    die('Access denied.');
}

function packs_redirect(string $url, string $message, string $type = 'info'): void
{
    $_SESSION['message'] = $message;
    $_SESSION['message_type'] = $type;
    header('Location: ' . $url);
    exit();
}

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    packs_redirect('premium_packs.php', 'Invalid request method.', 'danger');
}

$csrfPost = $_POST['csrf_token'] ?? '';
if (empty($_SESSION['csrf_token']) || empty($csrfPost) || !hash_equals((string)$_SESSION['csrf_token'], (string)$csrfPost)) {
    packs_redirect('premium_packs.php', 'Invalid CSRF token.', 'danger');
}

$action = strtolower(trim((string)($_POST['action'] ?? '')));
$packId = (int)($_POST['pack_id'] ?? 0);

if ($action === 'delete') {
    if ($packId <= 0) {
        packs_redirect('premium_packs.php', 'Invalid pack id.', 'danger');
    }

    $stmt = $conn->prepare('DELETE FROM premium_packs WHERE id = ? LIMIT 1');
    if (!$stmt) {
        packs_redirect('premium_packs.php', 'Database error while deleting pack.', 'danger');
    }
    $stmt->bind_param('i', $packId);
    $stmt->execute();
    $affected = (int)$stmt->affected_rows;
    $stmt->close();

    if ($affected > 0) {
        packs_redirect('premium_packs.php', 'Premium pack deleted successfully.', 'success');
    }
    packs_redirect('premium_packs.php', 'Premium pack not found.', 'warning');
}

if (!in_array($action, ['create', 'update'], true)) {
    packs_redirect('premium_packs.php', 'Unknown action.', 'danger');
}

$name = trim((string)($_POST['name'] ?? ''));
$description = trim((string)($_POST['description'] ?? ''));
$pricePoints = (int)($_POST['price_points'] ?? 0);
$isActive = !empty($_POST['is_active']) ? 1 : 0;
$postIdsRaw = $_POST['post_ids'] ?? [];
if (!is_array($postIdsRaw)) {
    $postIdsRaw = [$postIdsRaw];
}

if ($name === '') {
    $redirect = $action === 'update' ? 'premium_packs.php?edit=' . $packId : 'premium_packs.php';
    packs_redirect($redirect, 'Pack name is required.', 'warning');
}
if ($pricePoints < 0) {
    $redirect = $action === 'update' ? 'premium_packs.php?edit=' . $packId : 'premium_packs.php';
    packs_redirect($redirect, 'Price points cannot be negative.', 'warning');
}
if (mb_strlen($name) > 80) {
    $name = mb_substr($name, 0, 80);
}

$postIds = [];
foreach ($postIdsRaw as $postIdRaw) {
    $postId = (int)$postIdRaw;
    if ($postId > 0) {
        $postIds[] = $postId;
    }
}
$postIds = array_values(array_unique($postIds));

$validPostIds = [];
if (!empty($postIds)) {
    $placeholders = implode(',', array_fill(0, count($postIds), '?'));
    $types = str_repeat('i', count($postIds));
    $sql = "SELECT id FROM ai_posts WHERE status = 'published' AND id IN ($placeholders)";
    $stmt = $conn->prepare($sql);
    if (!$stmt) {
        $redirect = $action === 'update' ? 'premium_packs.php?edit=' . $packId : 'premium_packs.php';
        packs_redirect($redirect, 'Database error while validating prompts.', 'danger');
    }
    $stmt->bind_param($types, ...$postIds);
    $stmt->execute();
    $res = $stmt->get_result();
    while ($row = ($res ? $res->fetch_assoc() : null)) {
        $validPostIds[] = (int)$row['id'];
    }
    $stmt->close();
}

$conn->begin_transaction();
try {
    if ($action === 'create') {
        $stmt = $conn->prepare('INSERT INTO premium_packs (name, description, price_points, is_active) VALUES (?, ?, ?, ?)');
        if (!$stmt) {
            throw new RuntimeException('Failed to prepare pack insert');
        }
        $stmt->bind_param('ssii', $name, $description, $pricePoints, $isActive);
        if (!$stmt->execute()) {
            throw new RuntimeException('Failed to create pack');
        }
        $packId = (int)$stmt->insert_id;
        $stmt->close();
    } else {
        if ($packId <= 0) {
            throw new RuntimeException('Invalid pack id');
        }
        $stmt = $conn->prepare('UPDATE premium_packs SET name = ?, description = ?, price_points = ?, is_active = ? WHERE id = ? LIMIT 1');
        if (!$stmt) {
            throw new RuntimeException('Failed to prepare pack update');
        }
        $stmt->bind_param('ssiii', $name, $description, $pricePoints, $isActive, $packId);
        if (!$stmt->execute()) {
            throw new RuntimeException('Failed to update pack');
        }
        $stmt->close();

        $deleteStmt = $conn->prepare('DELETE FROM premium_pack_items WHERE pack_id = ?');
        if (!$deleteStmt) {
            throw new RuntimeException('Failed to prepare item reset');
        }
        $deleteStmt->bind_param('i', $packId);
        if (!$deleteStmt->execute()) {
            throw new RuntimeException('Failed to reset pack items');
        }
        $deleteStmt->close();
    }

    if (!empty($validPostIds)) {
        $insertStmt = $conn->prepare('INSERT INTO premium_pack_items (pack_id, post_id) VALUES (?, ?)');
        if (!$insertStmt) {
            throw new RuntimeException('Failed to prepare pack item insert');
        }
        foreach ($validPostIds as $postId) {
            $insertStmt->bind_param('ii', $packId, $postId);
            if (!$insertStmt->execute()) {
                throw new RuntimeException('Failed to insert pack item');
            }
        }
        $insertStmt->close();
    }

    $conn->commit();
} catch (Throwable $e) {
    $conn->rollback();
    error_log('premium_packs_process error: ' . $e->getMessage());
    $redirect = $action === 'update' ? 'premium_packs.php?edit=' . $packId : 'premium_packs.php';
    packs_redirect($redirect, 'Failed to save premium pack.', 'danger');
}

$redirect = 'premium_packs.php?edit=' . $packId;
$message = $action === 'create' ? 'Premium pack created successfully.' : 'Premium pack updated successfully.';
packs_redirect($redirect, $message, 'success');
