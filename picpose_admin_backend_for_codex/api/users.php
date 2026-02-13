<?php
/**
 * PicPose API — users.php
 * Handles: login, register, get profile
 */

header("Content-Type: application/json; charset=utf-8");
header("Access-Control-Allow-Origin: *");
header("Access-Control-Allow-Methods: GET, POST, OPTIONS");
header("Access-Control-Allow-Headers: Content-Type, X-API-Key, Authorization");

if ($_SERVER["REQUEST_METHOD"] === "OPTIONS") {
    http_response_code(204);
    exit();
}

require_once __DIR__ . "/../config.php"; // provides $conn (MySQLi)

// --------------------------
// UNIVERSAL API KEY HANDLING
// --------------------------
$API_KEY = "7a6f3c27a1b6d5e8e4c8a2b3f9e6d1f47c5b8a9d3e7f2c6a4b9e3d1c5f8a7b2c";

$headers = getallheaders();
$norm = [];
foreach ($headers as $k => $v) {
    $norm[strtolower($k)] = $v;
}

$key =
    $norm["x-api-key"]
    ?? $norm["x_api_key"]
    ?? ($_SERVER["HTTP_X_API_KEY"] ?? null)
    ?? ($_GET["api_key"] ?? null)
    ?? ($_POST["api_key"] ?? null);

if ($key !== $API_KEY) {
    echo json_encode([
        "status" => "error",
        "message" => "Invalid API Key"
    ]);
    exit();
}

// --------------------------
// DATABASE VALIDATION
// --------------------------
if (!$conn || $conn->connect_errno) {
    echo json_encode(["status" => "error", "message" => "Database connection failed"]);
    exit();
}

// --------------------------
// PARSE REQUEST
// --------------------------
$method = $_SERVER["REQUEST_METHOD"];
$action = $_GET["action"] ?? $_POST["action"] ?? null;

$raw = file_get_contents("php://input");
$input = json_decode($raw, true);
if (!is_array($input)) $input = $_POST;

// --------------------------
// REGISTER
// --------------------------
if ($method === "POST" && $action === "register") {
    $name = trim($input["name"] ?? "");
    $email = trim($input["email"] ?? "");
    $password = trim($input["password"] ?? "");

    if (!$name || !$email || !$password) {
        echo json_encode(["status" => "error", "message" => "All fields are required"]);
        exit();
    }

    if (!filter_var($email, FILTER_VALIDATE_EMAIL)) {
        echo json_encode(["status" => "error", "message" => "Invalid email"]);
        exit();
    }

    // Check if email exists
    $stmt = $conn->prepare("SELECT id FROM users WHERE email=?");
    $stmt->bind_param("s", $email);
    $stmt->execute();
    $res = $stmt->get_result();
    if ($res->num_rows > 0) {
        echo json_encode(["status" => "error", "message" => "Email already exists"]);
        exit();
    }
    $stmt->close();

    $hashed = password_hash($password, PASSWORD_DEFAULT);
    $defaultPic = "uploads/users_profilepic/default.png";

    // Insert user
    $stmt = $conn->prepare("
        INSERT INTO users (username, display_name, email, password, provider, profile_pic, created_at)
        VALUES (?, ?, ?, ?, 'email', ?, NOW())
    ");

    $stmt->bind_param("sssss", $name, $name, $email, $hashed, $defaultPic);
    $stmt->execute();

    $newId = $stmt->insert_id;
    $stmt->close();

    echo json_encode([
        "status" => "success",
        "message" => "Registration successful",
        "user" => [
            "id" => $newId,
            "email" => $email,
            "display_name" => $name,
            "profile_pic" => $defaultPic
        ]
    ]);
    exit();
}

// --------------------------
// LOGIN
// --------------------------
if ($method === "POST" && $action === "login") {
    $email = trim($input["email"] ?? "");
    $password = trim($input["password"] ?? "");

    if (!$email || !$password) {
        echo json_encode(["status" => "error", "message" => "Email & password required"]);
        exit();
    }

    $stmt = $conn->prepare("SELECT * FROM users WHERE email=? LIMIT 1");
    $stmt->bind_param("s", $email);
    $stmt->execute();
    $result = $stmt->get_result();
    $user = $result->fetch_assoc();
    $stmt->close();

    if (!$user) {
        echo json_encode(["status" => "error", "message" => "Invalid email or password"]);
        exit();
    }

    if (!password_verify($password, $user["password"])) {
        echo json_encode(["status" => "error", "message" => "Invalid email or password"]);
        exit();
    }

    unset($user["password"]);

    echo json_encode([
        "status" => "success",
        "message" => "Login successful",
        "user" => $user
    ]);
    exit();
}

// --------------------------
// GET USER PROFILE
// --------------------------
if ($method === "GET" && isset($_GET["id"])) {
    $id = intval($_GET["id"]);
    if ($id <= 0) {
        echo json_encode(["status" => "error", "message" => "Invalid user ID"]);
        exit();
    }

    $stmt = $conn->prepare("SELECT * FROM users WHERE id=? LIMIT 1");
    $stmt->bind_param("i", $id);
    $stmt->execute();
    $res = $stmt->get_result();
    $user = $res->fetch_assoc();
    $stmt->close();

    if (!$user) {
        echo json_encode(["status" => "error", "message" => "User not found"]);
        exit();
    }

    unset($user["password"]);

    echo json_encode(["status" => "success", "user" => $user]);
    exit();
}

echo json_encode(["status" => "error", "message" => "Invalid action"]);
exit();
