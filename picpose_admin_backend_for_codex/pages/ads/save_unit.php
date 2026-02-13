<?php
session_start();
require '../../config.php';

// Force connection collation
$conn->query("SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci");

// Use the SAME session check as main admin panel
if (!isset($_SESSION['admin']) || empty($_SESSION['admin'])) {
    header("Location: " . BASE_URL . "/login.php");
    exit();
}

// CSRF validation
if (!isset($_POST['csrf_token']) || $_POST['csrf_token'] !== ($_SESSION['csrf_token'] ?? '')) {
    $_SESSION['error'] = "CSRF token validation failed";
    header("Location: placements.php");
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
$id = isset($_POST['id']) ? (int)$_POST['id'] : 0;
$key_name = trim(cleanInput($_POST['key_name'] ?? ''));
$ad_type = cleanInput($_POST['ad_type'] ?? '');
$screen_hint = trim(cleanInput($_POST['screen_hint'] ?? ''));
$enabled = isset($_POST['enabled']) ? 1 : 0;
$auto_disabled = isset($_POST['auto_disabled']) ? 1 : 0;
$refresh_seconds = isset($_POST['refresh_seconds']) && $_POST['refresh_seconds'] !== '' ? (int)$_POST['refresh_seconds'] : null;
$frequency_override = isset($_POST['frequency_override']) && $_POST['frequency_override'] !== '' ? (int)$_POST['frequency_override'] : null;

// Validate required fields
if (empty($key_name) || empty($ad_type)) {
    $_SESSION['error'] = "Placement key and ad type are required";
    header("Location: placements.php");
    exit();
}

// Validate key name format
if (!preg_match('/^[a-z][a-z0-9_]*$/', $key_name)) {
    $_SESSION['error'] = "Placement key must start with lowercase letter and contain only lowercase letters, numbers, and underscores";
    header("Location: placements.php");
    exit();
}

// Validate ad type
$allowed_ad_types = ['banner', 'interstitial', 'native', 'rewarded'];
if (!in_array($ad_type, $allowed_ad_types)) {
    $_SESSION['error'] = "Invalid ad type";
    header("Location: placements.php");
    exit();
}

// Validate refresh seconds
if ($refresh_seconds !== null && ($refresh_seconds < 0 || $refresh_seconds > 3600)) {
    $_SESSION['error'] = "Refresh seconds must be between 0 and 3600";
    header("Location: placements.php");
    exit();
}

// Validate frequency override
if ($frequency_override !== null && ($frequency_override < 0 || $frequency_override > 20)) {
    $_SESSION['error'] = "Frequency override must be between 0 and 20";
    header("Location: placements.php");
    exit();
}

try {
    $conn->begin_transaction();
    
    if ($id > 0) {
        // Update existing placement
        // Check if key name is being changed
        $check_stmt = $conn->prepare("SELECT key_name FROM ad_placements WHERE id = ?");
        $check_stmt->bind_param("i", $id);
        $check_stmt->execute();
        $check_result = $check_stmt->get_result();
        $old_placement = $check_result->fetch_assoc();
        $check_stmt->close();
        
        if ($old_placement['key_name'] !== $key_name) {
            // Key name changed - check if new key already exists
            $duplicate_stmt = $conn->prepare("SELECT id FROM ad_placements WHERE key_name = ? AND id != ?");
            $duplicate_stmt->bind_param("si", $key_name, $id);
            $duplicate_stmt->execute();
            $duplicate_result = $duplicate_stmt->get_result();
            
            if ($duplicate_result->num_rows > 0) {
                throw new Exception("Placement key already exists");
            }
            $duplicate_stmt->close();
        }
        
        // Prepare UPDATE statement with NULL handling
        if ($refresh_seconds !== null && $frequency_override !== null) {
            // Both have values
            $update_stmt = $conn->prepare("
                UPDATE ad_placements 
                SET key_name = ?,
                    ad_type = ?,
                    screen_hint = ?,
                    enabled = ?,
                    auto_disabled = ?,
                    refresh_seconds = ?,
                    frequency_override = ?,
                    updated_at = NOW()
                WHERE id = ?
            ");
            $update_stmt->bind_param(
                "sssiiiiii",
                $key_name,
                $ad_type,
                $screen_hint,
                $enabled,
                $auto_disabled,
                $refresh_seconds,
                $frequency_override,
                $id
            );
        } elseif ($refresh_seconds !== null) {
            // Only refresh_seconds has value, frequency_override is NULL
            $update_stmt = $conn->prepare("
                UPDATE ad_placements 
                SET key_name = ?,
                    ad_type = ?,
                    screen_hint = ?,
                    enabled = ?,
                    auto_disabled = ?,
                    refresh_seconds = ?,
                    frequency_override = NULL,
                    updated_at = NOW()
                WHERE id = ?
            ");
            $update_stmt->bind_param(
                "sssiiiii",
                $key_name,
                $ad_type,
                $screen_hint,
                $enabled,
                $auto_disabled,
                $refresh_seconds,
                $id
            );
        } elseif ($frequency_override !== null) {
            // Only frequency_override has value, refresh_seconds is NULL
            $update_stmt = $conn->prepare("
                UPDATE ad_placements 
                SET key_name = ?,
                    ad_type = ?,
                    screen_hint = ?,
                    enabled = ?,
                    auto_disabled = ?,
                    refresh_seconds = NULL,
                    frequency_override = ?,
                    updated_at = NOW()
                WHERE id = ?
            ");
            $update_stmt->bind_param(
                "sssiiiii",
                $key_name,
                $ad_type,
                $screen_hint,
                $enabled,
                $auto_disabled,
                $frequency_override,
                $id
            );
        } else {
            // Both are NULL
            $update_stmt = $conn->prepare("
                UPDATE ad_placements 
                SET key_name = ?,
                    ad_type = ?,
                    screen_hint = ?,
                    enabled = ?,
                    auto_disabled = ?,
                    refresh_seconds = NULL,
                    frequency_override = NULL,
                    updated_at = NOW()
                WHERE id = ?
            ");
            $update_stmt->bind_param(
                "sssiiii",
                $key_name,
                $ad_type,
                $screen_hint,
                $enabled,
                $auto_disabled,
                $id
            );
        }
        
        $action = "updated";
    } else {
        // Insert new placement
        // Check if key already exists
        $check_stmt = $conn->prepare("SELECT id FROM ad_placements WHERE key_name = ?");
        $check_stmt->bind_param("s", $key_name);
        $check_stmt->execute();
        $check_result = $check_stmt->get_result();
        
        if ($check_result->num_rows > 0) {
            throw new Exception("Placement key already exists");
        }
        $check_stmt->close();
        
        // Prepare INSERT statement with NULL handling
        if ($refresh_seconds !== null && $frequency_override !== null) {
            // Both have values
            $insert_stmt = $conn->prepare("
                INSERT INTO ad_placements 
                    (key_name, ad_type, screen_hint, enabled, auto_disabled, refresh_seconds, frequency_override)
                VALUES (?, ?, ?, ?, ?, ?, ?)
            ");
            $insert_stmt->bind_param(
                "sssiiii",
                $key_name,
                $ad_type,
                $screen_hint,
                $enabled,
                $auto_disabled,
                $refresh_seconds,
                $frequency_override
            );
        } elseif ($refresh_seconds !== null) {
            // Only refresh_seconds has value
            $insert_stmt = $conn->prepare("
                INSERT INTO ad_placements 
                    (key_name, ad_type, screen_hint, enabled, auto_disabled, refresh_seconds, frequency_override)
                VALUES (?, ?, ?, ?, ?, ?, NULL)
            ");
            $insert_stmt->bind_param(
                "sssiii",
                $key_name,
                $ad_type,
                $screen_hint,
                $enabled,
                $auto_disabled,
                $refresh_seconds
            );
        } elseif ($frequency_override !== null) {
            // Only frequency_override has value
            $insert_stmt = $conn->prepare("
                INSERT INTO ad_placements 
                    (key_name, ad_type, screen_hint, enabled, auto_disabled, refresh_seconds, frequency_override)
                VALUES (?, ?, ?, ?, ?, NULL, ?)
            ");
            $insert_stmt->bind_param(
                "sssiii",
                $key_name,
                $ad_type,
                $screen_hint,
                $enabled,
                $auto_disabled,
                $frequency_override
            );
        } else {
            // Both are NULL
            $insert_stmt = $conn->prepare("
                INSERT INTO ad_placements 
                    (key_name, ad_type, screen_hint, enabled, auto_disabled, refresh_seconds, frequency_override)
                VALUES (?, ?, ?, ?, ?, NULL, NULL)
            ");
            $insert_stmt->bind_param(
                "sssii",
                $key_name,
                $ad_type,
                $screen_hint,
                $enabled,
                $auto_disabled
            );
        }
        
        $action = "created";
    }
    
    // Execute the query
    if ($id > 0) {
        if (!$update_stmt->execute()) {
            throw new Exception("Failed to update placement: " . $update_stmt->error);
        }
        $update_stmt->close();
        $placement_id = $id;
    } else {
        if (!$insert_stmt->execute()) {
            throw new Exception("Failed to create placement: " . $insert_stmt->error);
        }
        $placement_id = $insert_stmt->insert_id;
        $insert_stmt->close();
    }
    
    // Increment config version
    $conn->query("UPDATE ads_global_settings SET config_version = config_version + 1 WHERE id = 1");
    
    // Log the action - ONLY IF TABLE EXISTS
    try {
        $admin_id = $_SESSION['admin_id'] ?? 0;
        $action_details = "Placement $action: $key_name ($ad_type)";
        
        // Check if admin_logs table exists
        $check_table = $conn->query("SHOW TABLES LIKE 'admin_logs'");
        if ($check_table->num_rows > 0) {
            $log_stmt = $conn->prepare("
                INSERT INTO admin_logs (admin_id, action, details, ip_address, user_agent)
                VALUES (?, 'placement_$action', ?, ?, ?)
            ");
            $log_stmt->bind_param(
                "isss",
                $admin_id,
                $action_details,
                $_SERVER['REMOTE_ADDR'],
                $_SERVER['HTTP_USER_AGENT'] ?? ''
            );
            $log_stmt->execute();
            $log_stmt->close();
        }
    } catch (Exception $e) {
        // Silently ignore log errors
        error_log("Logging error (non-critical): " . $e->getMessage());
    }
    
    $conn->commit();
    
    $_SESSION['success'] = "Placement " . ($id > 0 ? "updated" : "created") . " successfully!";
    
} catch (Exception $e) {
    $conn->rollback();
    error_log("Save Placement Error: " . $e->getMessage());
    $_SESSION['error'] = $e->getMessage();
}

// Redirect back
header("Location: placements.php");
exit();
?>