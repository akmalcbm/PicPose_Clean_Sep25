<?php
session_start();
require 'config.php';

if (!isset($_SESSION['admin'])) {
    header("Location: login.php");
    exit();
}

if ($_SERVER["REQUEST_METHOD"] !== "POST") {
    header("Location: users.php");
    exit();
}

$id             = intval($_POST['id'] ?? 0);
$username       = trim($_POST['username'] ?? "");
$display_name   = trim($_POST['display_name'] ?? "");
$email          = trim($_POST['email'] ?? "");
$bio            = trim($_POST['bio'] ?? "");
$account_type   = trim($_POST['account_type'] ?? "normal");

// Validate required values
if ($id <= 0 || empty($username)) {
    die("Invalid request.");
}

// Fetch user first
$stmt = $conn->prepare("SELECT provider, email, profile_pic FROM users WHERE id = ?");
$stmt->bind_param("i", $id);
$stmt->execute();
$user = $stmt->get_result()->fetch_assoc();
$stmt->close();

if (!$user) {
    $_SESSION['error'] = "User not found!";
    header("Location: users.php");
    exit();
}

$provider = $user['provider'];
$old_profile_pic = $user['profile_pic'];  // only local pic

/**
 * ----------------------------------------
 * 1️⃣ EMAIL UPDATE RULES
 * ----------------------------------------
 * Email can be updated ONLY IF provider = "email"
 */
$finalEmail = $user['email'];  // default
if ($provider === "email") {
    if (!filter_var($email, FILTER_VALIDATE_EMAIL)) {
        $_SESSION['error'] = "Invalid email format!";
        header("Location: edit_user.php?id=" . $id);
        exit();
    }
    $finalEmail = $email;
}


/**
 * ----------------------------------------
 * 2️⃣ HANDLE PROFILE PICTURE UPLOAD
 * ----------------------------------------
 */
$newProfilePicPath = null;

if (!empty($_FILES['profile_pic']['name'])) {

    $uploadDir = "uploads/users_profilepic/";

    // Ensure directory exists
    if (!file_exists($uploadDir)) {
        mkdir($uploadDir, 0775, true);
    }

    $ext = strtolower(pathinfo($_FILES["profile_pic"]["name"], PATHINFO_EXTENSION));
    $allowed = ["jpg", "jpeg", "png", "webp"];

    if (!in_array($ext, $allowed)) {
        $_SESSION['error'] = "Invalid image type. Only JPG / PNG / WEBP allowed.";
        header("Location: edit_user.php?id=" . $id);
        exit();
    }

    $newFileName = "user_" . $id . "_" . time() . "." . $ext;
    $targetFile = $uploadDir . $newFileName;

    if (move_uploaded_file($_FILES["profile_pic"]["tmp_name"], $targetFile)) {

        // Delete old profile pic if it exists and is not default
        if (!empty($old_profile_pic) 
            && file_exists($old_profile_pic) 
            && strpos($old_profile_pic, "default") === false) {
            unlink($old_profile_pic);
        }

        $newProfilePicPath = $targetFile;
    } else {
        $_SESSION['error'] = "Failed to upload profile picture.";
        header("Location: edit_user.php?id=" . $id);
        exit();
    }
}


/**
 * ----------------------------------------
 * 3️⃣ UPDATE USER IN DATABASE
 * ----------------------------------------
 */
$fields = [];
$params = [];
$types  = "";

// Username
$fields[] = "username = ?";
$params[] = $username;
$types   .= "s";

// Display name
$fields[] = "display_name = ?";
$params[] = $display_name;
$types   .= "s";

// Email (email provider only)
$fields[] = "email = ?";
$params[] = $finalEmail;
$types   .= "s";

// Bio
$fields[] = "bio = ?";
$params[] = $bio;
$types   .= "s";

// Account Type
$fields[] = "account_type = ?";
$params[] = $account_type;
$types   .= "s";

// Profile pic (local only)
if ($newProfilePicPath !== null) {
    $fields[] = "profile_pic = ?";
    $params[] = $newProfilePicPath;
    $types   .= "s";
}

$fields[] = "updated_at = NOW()";

// WHERE ID
$params[] = $id;
$types   .= "i";

$sql = "UPDATE users SET " . implode(", ", $fields) . " WHERE id = ?";

$stmt = $conn->prepare($sql);
$stmt->bind_param($types, ...$params);

if ($stmt->execute()) {
    $_SESSION['success'] = "User updated successfully!";
} else {
    $_SESSION['error'] = "Update failed: " . $stmt->error;
}

$stmt->close();
header("Location: edit_user.php?id=" . $id);
exit();
?>
