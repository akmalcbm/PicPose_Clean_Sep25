<?php
// process_tip.php
session_start();
require 'config.php';
if (!isset($_SESSION['admin'])) { header('Location: login.php'); exit(); }

// simple helper for redirect with message
function redirect_with($url, $msg, $type = 'success') {
    $_SESSION['message'] = $msg;
    $_SESSION['message_type'] = $type;
    header("Location: $url");
    exit;
}

$action = $_POST['action'] ?? '';
$tipText = trim($_POST['tip_text'] ?? '');
$displayOrder = isset($_POST['display_order']) ? intval($_POST['display_order']) : 0;
$isActive = isset($_POST['is_active']) ? (int)$_POST['is_active'] : 0;
$tipId = isset($_POST['tip_id']) ? intval($_POST['tip_id']) : 0;

try {
    if ($action === 'create') {
        if ($tipText === '') throw new Exception('Tip text cannot be empty.');
        $stmt = $conn->prepare("INSERT INTO daily_tips (tip_text, is_active, display_order, created_at) VALUES (?, ?, ?, NOW())");
        $stmt->bind_param("sii", $tipText, $isActive, $displayOrder);
        if (!$stmt->execute()) throw new Exception('Insert failed: ' . $stmt->error);
        $stmt->close();
        redirect_with('views/tips/manage_tips.php', 'Tip created successfully.');
    }

    if ($action === 'update') {
        if ($tipId <= 0) throw new Exception('Invalid tip id.');
        if ($tipText === '') throw new Exception('Tip text cannot be empty.');
        $stmt = $conn->prepare("UPDATE daily_tips SET tip_text = ?, is_active = ?, display_order = ?, updated_at = NOW() WHERE id = ?");
        $stmt->bind_param("siii", $tipText, $isActive, $displayOrder, $tipId);
        if (!$stmt->execute()) throw new Exception('Update failed: ' . $stmt->error);
        $stmt->close();
        redirect_with('views/tips/manage_tips.php', 'Tip updated successfully.');
    }

    if ($action === 'delete') {
        if ($tipId <= 0) throw new Exception('Invalid tip id.');
        $stmt = $conn->prepare("DELETE FROM daily_tips WHERE id = ?");
        $stmt->bind_param("i", $tipId);
        if (!$stmt->execute()) throw new Exception('Delete failed: ' . $stmt->error);
        $stmt->close();
        redirect_with('views/tips/manage_tips.php', 'Tip deleted.');
    }

    throw new Exception('Unknown action');

} catch (Exception $ex) {
    redirect_with('views/tips/manage_tips.php', 'Error: ' . $ex->getMessage(), 'danger');
}
