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
// DELETE ACCOUNT (IN-APP)
// --------------------------
if ($method === "POST" && $action === "delete_account") {
    $userId = intval($input["user_id"] ?? 0);
    $email = trim((string)($input["email"] ?? ""));
    $reason = trim((string)($input["reason"] ?? "user_requested_in_app"));

    if ($userId <= 0 || $email === "") {
        echo json_encode(["status" => "error", "message" => "user_id and email are required"]);
        exit();
    }

    if (!filter_var($email, FILTER_VALIDATE_EMAIL)) {
        echo json_encode(["status" => "error", "message" => "Invalid email"]);
        exit();
    }

    $conn->begin_transaction();
    try {
        // Verify user ownership by user_id + email pair
        $findStmt = $conn->prepare("SELECT id, profile_pic FROM users WHERE id = ? AND email = ? LIMIT 1");
        if (!$findStmt) throw new Exception("Failed to prepare user lookup");
        $findStmt->bind_param("is", $userId, $email);
        $findStmt->execute();
        $user = $findStmt->get_result()->fetch_assoc();
        $findStmt->close();

        if (!$user) {
            throw new Exception("Account not found for provided details");
        }

        // Cleanup related rows that may contain user-linked data
        $cleanupQueries = [
            "DELETE FROM social_logins WHERE user_id = ?",
            "DELETE FROM device_tokens WHERE user_id = ?",
            "DELETE FROM comments WHERE user_id = ?",
            "DELETE FROM notification_clicks WHERE user_id = ?"
        ];

        foreach ($cleanupQueries as $query) {
            $stmt = $conn->prepare($query);
            if (!$stmt) {
                continue; // table may be absent in some deployments
            }
            $stmt->bind_param("i", $userId);
            $stmt->execute();
            $stmt->close();
        }

        // Remove support queries linked by email (PII cleanup)
        $supportStmt = $conn->prepare("DELETE FROM support_queries WHERE email = ?");
        if ($supportStmt) {
            $supportStmt->bind_param("s", $email);
            $supportStmt->execute();
            $supportStmt->close();
        }

        // Delete account row
        $delStmt = $conn->prepare("DELETE FROM users WHERE id = ? LIMIT 1");
        if (!$delStmt) throw new Exception("Failed to prepare account deletion");
        $delStmt->bind_param("i", $userId);
        $delStmt->execute();
        $affected = $delStmt->affected_rows;
        $delStmt->close();

        if ($affected <= 0) {
            throw new Exception("Failed to delete account");
        }

        // Best-effort local profile image cleanup
        $profilePic = trim((string)($user["profile_pic"] ?? ""));
        if ($profilePic !== "" && strpos($profilePic, "default.png") === false) {
            $safePath = ltrim($profilePic, "/");
            $absolute = realpath(__DIR__ . "/../" . $safePath);
            $uploadsRoot = realpath(__DIR__ . "/../uploads");
            if ($absolute && $uploadsRoot && strpos($absolute, $uploadsRoot) === 0 && is_file($absolute)) {
                @unlink($absolute);
            }
        }

        $conn->commit();

        echo json_encode([
            "status" => "success",
            "message" => "Account deleted successfully",
            "deleted" => [
                "user_id" => $userId,
                "email" => $email,
                "reason" => $reason
            ],
            "retention_notice" => "Some security/compliance logs may be retained for up to 90 days."
        ]);
        exit();
    } catch (Throwable $e) {
        $conn->rollback();
        echo json_encode([
            "status" => "error",
            "message" => $e->getMessage()
        ]);
        exit();
    }
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
