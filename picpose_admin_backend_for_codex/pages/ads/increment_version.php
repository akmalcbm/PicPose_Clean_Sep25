<?php
session_start();
require '../../config.php';

// Use the SAME session check as main admin panel
if (!isset($_SESSION['admin']) || empty($_SESSION['admin'])) {
    header("Location: " . BASE_URL . "/login.php");
    exit();
}

// CSRF validation
if (!isset($_POST['csrf_token']) || $_POST['csrf_token'] !== ($_SESSION['csrf_token'] ?? '')) {
    $_SESSION['error'] = "CSRF token validation failed";
    header("Location: global_settings.php");
    exit();
}

// Increment config version
try {
    $update_stmt = $conn->prepare("
        UPDATE ads_global_settings 
        SET config_version = config_version + 1,
            updated_at = NOW()
        WHERE id = 1
    ");
    
    $update_stmt->execute();
    $update_stmt->close();
    
    $_SESSION['success'] = "Configuration version incremented! Apps will reload config on next check.";
    
} catch (Exception $e) {
    error_log("Increment Version Error: " . $e->getMessage());
    $_SESSION['error'] = "Failed to increment version. Please try again.";
}

// Return to global settings page
header("Location: global_settings.php");
exit();
?>