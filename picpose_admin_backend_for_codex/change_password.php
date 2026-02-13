<?php
session_start();
require 'config.php';

// Ensure logged in
if (!isset($_SESSION['admin']) && empty($_SESSION['admin_id']) && empty($_SESSION['admin_email'])) {
    $_SESSION['message'] = 'Please login first.';
    $_SESSION['message_type'] = 'danger';
    header('Location: login.php');
    exit();
}

// CSRF check
if (!isset($_POST['csrf_token']) || $_POST['csrf_token'] !== ($_SESSION['csrf_token'] ?? '')) {
    $_SESSION['message'] = 'Invalid CSRF token.';
    $_SESSION['message_type'] = 'danger';
    header('Location: settings.php');
    exit();
}

// Inputs
$current = $_POST['current_password'] ?? '';
$new = $_POST['new_password'] ?? '';
$confirm = $_POST['confirm_password'] ?? '';

$errors = [];
if (!$current || !$new || !$confirm) $errors[] = 'All fields are required.';
if ($new !== $confirm) $errors[] = 'New password and confirmation do not match.';
if (strlen($new) < 8) $errors[] = 'New password must be at least 8 characters.';

if (!empty($errors)) {
    $_SESSION['message'] = implode(' ', $errors);
    $_SESSION['message_type'] = 'danger';
    header('Location: settings.php');
    exit();
}

// Determine admin identity robustly
$adminId = null;
$adminUsername = null;
$adminEmail = null;

// Common possibilities:
// - $_SESSION['admin_id'] (int)
// - $_SESSION['admin'] could be username string, or an array like ['id'=>..., 'username'=>..., 'email'=>...]
// - $_SESSION['admin_email'] (string)
if (!empty($_SESSION['admin_id'])) {
    $adminId = (int) $_SESSION['admin_id'];
} 

// If admin session is an array (common when you store whole user row)
if (isset($_SESSION['admin']) && is_array($_SESSION['admin'])) {
    if (!empty($_SESSION['admin']['id'])) $adminId = (int) $_SESSION['admin']['id'];
    if (!empty($_SESSION['admin']['username'])) $adminUsername = $_SESSION['admin']['username'];
    if (!empty($_SESSION['admin']['email'])) $adminEmail = $_SESSION['admin']['email'];
}

// If admin session is a string it might be username or email
if (isset($_SESSION['admin']) && is_string($_SESSION['admin'])) {
    // Heuristic: if contains '@' treat as email else username
    if (strpos($_SESSION['admin'], '@') !== false) {
        $adminEmail = $_SESSION['admin'];
    } else {
        $adminUsername = $_SESSION['admin'];
    }
}

// Also check explicit admin_email session
if (empty($adminEmail) && !empty($_SESSION['admin_email'])) {
    $adminEmail = $_SESSION['admin_email'];
}

// If we still don't have any identity, log session for debugging and fail gracefully
if (empty($adminId) && empty($adminUsername) && empty($adminEmail)) {
    error_log('change_password: could not determine admin identity. Session dump: ' . print_r($_SESSION, true));
    $_SESSION['message'] = 'Admin account not found (session error).';
    $_SESSION['message_type'] = 'danger';
    header('Location: settings.php');
    exit();
}

try {
    // Build SQL and bind dynamically depending on available identifier
    if (!empty($adminId)) {
        $stmt = $conn->prepare("SELECT id, username, password FROM admin_users WHERE id = ? LIMIT 1");
        $stmt->bind_param("i", $adminId);
    } elseif (!empty($adminUsername) && !empty($adminEmail)) {
        // both present: check by id not available, choose username first
        $stmt = $conn->prepare("SELECT id, username, password FROM admin_users WHERE username = ? OR email = ? LIMIT 1");
        $stmt->bind_param("ss", $adminUsername, $adminEmail);
    } elseif (!empty($adminUsername)) {
        $stmt = $conn->prepare("SELECT id, username, password FROM admin_users WHERE username = ? LIMIT 1");
        $stmt->bind_param("s", $adminUsername);
    } else { // only email
        $stmt = $conn->prepare("SELECT id, username, password FROM admin_users WHERE email = ? LIMIT 1");
        $stmt->bind_param("s", $adminEmail);
    }

    if (!$stmt) {
        error_log('change_password: prepare failed - ' . $conn->error);
        $_SESSION['message'] = 'An internal error occurred.';
        $_SESSION['message_type'] = 'danger';
        header('Location: settings.php');
        exit();
    }

    $stmt->execute();
    $res = $stmt->get_result();
    $admin = $res->fetch_assoc();
    $stmt->close();

    if (!$admin) {
        // Helpful debug: log which identifier we tried
        $debugId = json_encode(['adminId'=>$adminId, 'adminUsername'=>$adminUsername, 'adminEmail'=>$adminEmail]);
        error_log('change_password: admin row not found for identifiers: ' . $debugId);
        $_SESSION['message'] = 'Admin account not found.';
        $_SESSION['message_type'] = 'danger';
        header('Location: settings.php');
        exit();
    }

    // Verify current password
    if (!password_verify($current, $admin['password'])) {
        $_SESSION['message'] = 'Current password is incorrect.';
        $_SESSION['message_type'] = 'danger';
        header('Location: settings.php');
        exit();
    }

    // Hash and update
    $newHash = password_hash($new, PASSWORD_DEFAULT);

    // Check for token_version column (optional)
    $hasTokenVersion = false;
    $colCheck = $conn->query("SHOW COLUMNS FROM `admin_users` LIKE 'token_version'");
    if ($colCheck && $colCheck->num_rows > 0) {
        $hasTokenVersion = true;
    }

    if ($hasTokenVersion) {
        $upd = $conn->prepare("UPDATE admin_users SET password = ?, token_version = token_version + 1, password_changed_at = NOW() WHERE id = ?");
        $upd->bind_param("si", $newHash, $admin['id']);
    } else {
        $upd = $conn->prepare("UPDATE admin_users SET password = ? WHERE id = ?");
        $upd->bind_param("si", $newHash, $admin['id']);
    }

    if (!$upd->execute()) {
        error_log('change_password: update execute failed - ' . $conn->error);
        $_SESSION['message'] = 'Database error while updating password.';
        $_SESSION['message_type'] = 'danger';
        header('Location: settings.php');
        exit();
    }
    $upd->close();

    // Regenerate session id
    session_regenerate_id(true);

    // Optional: if you want to force re-login, uncomment below
    // session_unset(); session_destroy(); header('Location: login.php'); exit();

    $_SESSION['message'] = 'Password changed successfully.';
    $_SESSION['message_type'] = 'success';
    header('Location: settings.php');
    exit();

} catch (Exception $e) {
    error_log('change_password exception: ' . $e->getMessage());
    $_SESSION['message'] = 'An internal error occurred. Try again later.';
    $_SESSION['message_type'] = 'danger';
    header('Location: settings.php');
    exit();
}
