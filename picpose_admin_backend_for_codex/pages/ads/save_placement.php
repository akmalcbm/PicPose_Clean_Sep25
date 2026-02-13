<?php
session_start();
require '../../config.php';

// DEBUG: Log what's being received
error_log("=== SAVE PLACEMENT START ===");
error_log("POST Data: " . print_r($_POST, true));
error_log("Session: " . print_r($_SESSION, true));


// Use the SAME session check as main admin panel
if (!isset($_SESSION['admin']) || empty($_SESSION['admin'])) {
    // Redirect to main admin login if not logged in
    header("Location: " . BASE_URL . "/login.php");
    exit();
}

// CSRF validation
if (!isset($_POST['csrf_token']) || $_POST['csrf_token'] !== ($_SESSION['csrf_token'] ?? '')) {
    error_log("CSRF token mismatch. POST: " . ($_POST['csrf_token'] ?? 'none') . ", SESSION: " . ($_SESSION['csrf_token'] ?? 'none'));
    $_SESSION['error'] = "CSRF token validation failed";
    header("Location: placements.php");
    exit();
}

error_log("CSRF validation passed.");

// Validate and sanitize inputs
$id = isset($_POST['id']) ? (int)$_POST['id'] : 0;
$key_name = trim(cleanInput($_POST['key_name'] ?? ''));
$ad_type = cleanInput($_POST['ad_type'] ?? '');
$screen_hint = trim(cleanInput($_POST['screen_hint'] ?? ''));
$enabled = isset($_POST['enabled']) ? 1 : 0;
$auto_disabled = isset($_POST['auto_disabled']) ? 1 : 0;
$refresh_seconds = isset($_POST['refresh_seconds']) && $_POST['refresh_seconds'] !== '' ? (int)$_POST['refresh_seconds'] : null;
$frequency_override = isset($_POST['frequency_override']) && $_POST['frequency_override'] !== '' ? (int)$_POST['frequency_override'] : null;

error_log("Processed Data - ID: $id, Key: $key_name, Type: $ad_type, Enabled: $enabled");

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
    
    // Log the action
    $admin_id = $_SESSION['admin_id'] ?? 0;
    $action_details = "Placement $action: $key_name ($ad_type)";
    
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