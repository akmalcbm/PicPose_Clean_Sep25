<?php
header('Content-Type: application/json; charset=utf-8');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: POST, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type, Authorization, X-API-Key');

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(204);
    exit;
}

require_once __DIR__ . '/../config.php';

$API_KEY = "7a6f3c27a1b6d5e8e4c8a2b3f9e6d1f47c5b8a9d3e7f2c6a4b9e3d1c5f8a7b2c";

// ----- API KEY VALIDATION -----
$headers = getallheaders();
$norm = [];
foreach ($headers as $k => $v) $norm[strtolower($k)] = $v;

$key =
    $norm["x-api-key"]
    ?? $_POST["api_key"]
    ?? $_GET["api_key"]
    ?? null;

if ($key !== $API_KEY) {
    echo json_encode(["status" => "error", "message" => "Invalid API Key"]);
    exit;
}

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    echo json_encode(["status" => "error", "message" => "Invalid method"]);
    exit;
}

if (!$conn) {
    echo json_encode(["status" => "error", "message" => "DB connection failed"]);
    exit;
}

// ----- INPUT -----
$userId = intval($_POST['user_id'] ?? 0);
$name = trim($_POST['name'] ?? '');
$bio = trim($_POST['bio'] ?? '');
$accountType = trim($_POST['account_type'] ?? 'normal');

if ($userId <= 0) {
    echo json_encode(["status" => "error", "message" => "Missing user ID"]);
    exit;
}
if ($name === '') {
    echo json_encode(["status" => "error", "message" => "Name cannot be empty"]);
    exit;
}

// ---------- PROFILE PICTURE UPLOAD ----------
$profilePicturePath = null;

if (!empty($_FILES['profile_picture']['name']) && $_FILES['profile_picture']['error'] === UPLOAD_ERR_OK) {
    $uploadDir = __DIR__ . '/../uploads/users_profilepic/';
    if (!file_exists($uploadDir)) mkdir($uploadDir, 0777, true);

    $ext = strtolower(pathinfo($_FILES['profile_picture']['name'], PATHINFO_EXTENSION));
    $allowed = ['jpg', 'jpeg', 'png', 'webp'];

    if (!in_array($ext, $allowed)) {
        echo json_encode(["status" => "error", "message" => "Invalid file type"]);
        exit;
    }

    $newName = "user_" . $userId . "_" . time() . "." . $ext;
    $fullPath = $uploadDir . $newName;
    $dbPath = "uploads/users_profilepic/" . $newName;

    if (move_uploaded_file($_FILES['profile_picture']['tmp_name'], $fullPath)) {
        $profilePicturePath = $dbPath;
    }
}

// ---------- UPDATE USER ----------
try {
    $fields = [];
    $params = [];
    $types = "";

    // Use display_name instead of username
    $fields[] = "display_name=?";
    $params[] = $name;
    $types .= "s";

    // Bio
    $fields[] = "bio=?";
    $params[] = $bio;
    $types .= "s";

    // Account type
    $fields[] = "account_type=?";
    $params[] = $accountType;
    $types .= "s";

    // Profile picture (Google users compatible)
    if ($profilePicturePath !== null) {
        $fields[] = "profile_picture=?";
        $params[] = $profilePicturePath;
        $types .= "s";
    }

    // Always update timestamp
    $fields[] = "updated_at = NOW()";

    $query = "UPDATE users SET " . implode(", ", $fields) . " WHERE id=?";
    $params[] = $userId;
    $types .= "i";

    $stmt = $conn->prepare($query);
    $stmt->bind_param($types, ...$params);
    $stmt->execute();
    $stmt->close();

    // Fetch updated user
    $res = $conn->query("SELECT id, email, display_name, profile_picture, bio, account_type FROM users WHERE id=$userId");
    $user = $res->fetch_assoc();

    echo json_encode([
        "status" => "success",
        "message" => "Profile updated successfully",
        "user" => $user
    ]);

} catch (Exception $e) {
    echo json_encode([
        "status" => "error",
        "message" => "Server error: " . $e->getMessage()
    ]);
}
