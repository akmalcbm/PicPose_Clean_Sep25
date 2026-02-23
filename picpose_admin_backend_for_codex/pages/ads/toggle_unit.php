<?php
session_start();
require '../../config.php';

if (!isset($_SESSION['admin']) || empty($_SESSION['admin'])) {
    header("Location: " . BASE_URL . "/login.php");
    exit();
}

if (!isset($_POST['csrf_token']) || $_POST['csrf_token'] !== ($_SESSION['csrf_token'] ?? '')) {
    $_SESSION['error'] = 'CSRF token validation failed';
    header('Location: units.php');
    exit();
}

$id = (int)($_POST['id'] ?? 0);
if ($id <= 0) {
    $_SESSION['error'] = 'Invalid unit id';
    header('Location: units.php');
    exit();
}

try {
    $conn->begin_transaction();

    $stmt = $conn->prepare('UPDATE ad_network_units SET enabled = IF(enabled = 1, 0, 1), updated_at = NOW() WHERE id = ?');
    $stmt->bind_param('i', $id);
    if (!$stmt->execute()) {
        throw new RuntimeException('Failed to toggle unit status');
    }
    $stmt->close();

    $conn->query("UPDATE ads_global_settings SET config_version = config_version + 1, updated_at = NOW() WHERE id = 1");
    $conn->commit();

    $_SESSION['success'] = 'Ad unit status updated.';
} catch (Throwable $e) {
    $conn->rollback();
    $_SESSION['error'] = $e->getMessage();
}

header('Location: units.php');
exit();
