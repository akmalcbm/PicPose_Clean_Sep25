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

// Define cleanInput function if it doesn't exist
if (!function_exists('cleanInput')) {
    function cleanInput($data) {
        if (empty($data)) return '';
        $data = trim($data);
        $data = stripslashes($data);
        $data = htmlspecialchars($data, ENT_QUOTES, 'UTF-8');
        return $data;
    }
}

// Validate and sanitize inputs
$ads_enabled = isset($_POST['ads_enabled']) ? 1 : 0;
$environment = cleanInput($_POST['environment'] ?? '');
$cmp_required = isset($_POST['cmp_required']) ? 1 : 0;
$default_frequency_per_hour = (int)($_POST['default_frequency_per_hour'] ?? 3);

// Validate environment
$allowed_environments = ['development', 'staging', 'production'];
if (!in_array($environment, $allowed_environments, true)) {
    $environment = 'development';
}

// Validate frequency
if ($default_frequency_per_hour < 0 || $default_frequency_per_hour > 20) {
    $default_frequency_per_hour = 3;
}

try {
    $conn->begin_transaction();

    // Update settings and increment version
    $update_stmt = $conn->prepare("
        UPDATE ads_global_settings 
        SET ads_enabled = ?,
            environment = ?,
            cmp_required = ?,
            default_frequency_per_hour = ?,
            config_version = config_version + 1,
            updated_at = NOW()
        WHERE id = 1
    ");

    if (!$update_stmt) {
        throw new Exception("Prepare failed (update): " . $conn->error);
    }

    $update_stmt->bind_param(
        "isii",
        $ads_enabled,
        $environment,
        $cmp_required,
        $default_frequency_per_hour
    );

    if (!$update_stmt->execute()) {
        throw new Exception("Failed to update settings: " . $update_stmt->error);
    }
    $update_stmt->close();

    // Log the action
    $admin_id = (int)($_SESSION['admin_id'] ?? 0);
    $action_details = "Updated global ads settings";

    // IMPORTANT: bind_param requires variables (passed by reference), not expressions like $_SERVER[...] or ?? ''
    $ip_address = (string)($_SERVER['REMOTE_ADDR'] ?? '');
    $user_agent = (string)($_SERVER['HTTP_USER_AGENT'] ?? '');

    $log_stmt = $conn->prepare("
        INSERT INTO admin_logs (admin_id, action, details, ip_address, user_agent)
        VALUES (?, 'global_settings_update', ?, ?, ?)
    ");

    if (!$log_stmt) {
        throw new Exception("Prepare failed (log): " . $conn->error);
    }

    $log_stmt->bind_param(
        "isss",
        $admin_id,
        $action_details,
        $ip_address,
        $user_agent
    );

    if (!$log_stmt->execute()) {
        throw new Exception("Failed to insert admin log: " . $log_stmt->error);
    }
    $log_stmt->close();

    $conn->commit();

    $_SESSION['success'] = "Global settings updated successfully!";

    // Invalidate cache if needed
    if (function_exists('opcache_reset')) {
        @opcache_reset();
    }

} catch (Exception $e) {
    if ($conn && $conn->errno === 0) {
        // If transaction started, rollback safely
        @$conn->rollback();
    }
    error_log("Save Global Settings Error: " . $e->getMessage());
    $_SESSION['error'] = "Failed to update settings. Please try again.";
}

header("Location: global_settings.php");
exit();
?>
