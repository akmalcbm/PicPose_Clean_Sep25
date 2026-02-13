<?php
// update_settings.php
session_start();
require 'config.php';

if (!isset($_SESSION['admin'])) {
    header("Location: login.php");
    exit();
}

// CSRF protection
if (!isset($_POST['csrf_token']) || $_POST['csrf_token'] !== $_SESSION['csrf_token']) {
    $_SESSION['message'] = "Invalid CSRF token!";
    $_SESSION['message_type'] = 'error';
    header("Location: settings.php");
    exit();
}

// Get POST data
$admin_name = $_POST['admin_name'] ?? '';
$app_name = $_POST['app_name'] ?? '';
$tagline = $_POST['tagline'] ?? '';
$description = $_POST['description'] ?? '';
$google_play_url = $_POST['google_play_url'] ?? '';
$privacy_policy = $_POST['privacy_policy'] ?? '';
$terms_conditions = $_POST['terms_conditions'] ?? '';
$support_email = $_POST['support_email'] ?? '';
$support_phone = $_POST['support_phone'] ?? '';
$about = $_POST['about'] ?? '';

// Check if settings exist
$check = $conn->query("SELECT * FROM app_settings LIMIT 1");
if ($check->num_rows > 0) {
    // Update existing settings
    $stmt = $conn->prepare("UPDATE app_settings SET 
        admin_name = ?, 
        app_name = ?, 
        tagline = ?, 
        description = ?, 
        google_play_url = ?, 
        privacy_policy = ?, 
        terms_conditions = ?, 
        support_email = ?, 
        support_phone = ?, 
        about = ? 
        WHERE id = 1");
    
    $stmt->bind_param("ssssssssss", 
        $admin_name, 
        $app_name, 
        $tagline, 
        $description, 
        $google_play_url, 
        $privacy_policy, 
        $terms_conditions, 
        $support_email, 
        $support_phone, 
        $about
    );
} else {
    // Insert new settings
    $stmt = $conn->prepare("INSERT INTO app_settings (
        admin_name, 
        app_name, 
        tagline, 
        description, 
        google_play_url, 
        privacy_policy, 
        terms_conditions, 
        support_email, 
        support_phone, 
        about
    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
    
    $stmt->bind_param("ssssssssss", 
        $admin_name, 
        $app_name, 
        $tagline, 
        $description, 
        $google_play_url, 
        $privacy_policy, 
        $terms_conditions, 
        $support_email, 
        $support_phone, 
        $about
    );
}

if ($stmt->execute()) {
    $_SESSION['message'] = "Settings updated successfully!";
    $_SESSION['message_type'] = 'success';
} else {
    $_SESSION['message'] = "Error updating settings: " . $conn->error;
    $_SESSION['message_type'] = 'error';
}

$stmt->close();
$conn->close();

// Regenerate CSRF token for next request
$_SESSION['csrf_token'] = bin2hex(random_bytes(32));

header("Location: settings.php");
exit();
?>