<?php
header("Content-Type: application/json; charset=utf-8");
header("Access-Control-Allow-Origin: *");
header("Access-Control-Allow-Headers: Content-Type, X-API-Key, Authorization");
header("Access-Control-Allow-Methods: POST, OPTIONS");

if ($_SERVER["REQUEST_METHOD"] === "OPTIONS") {
    http_response_code(204);
    exit();
}

require_once "../config.php"; // provides $conn + $pdo

// --------------------------
// UNIVERSAL API KEY VALIDATION
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
    echo json_encode(["status" => "error", "message" => "Invalid API Key"]);
    exit();
}

// --------------------------
// READ JSON BODY
// --------------------------
$data = json_decode(file_get_contents("php://input"), true);

$provider = $data["provider"] ?? "";
$email = $data["email"] ?? "";
$name = $data["name"] ?? "";
$token = $data["token"] ?? "";
$socialId = $data["socialId"] ?? "";
$profilePicture = $data["profilePicture"] ?? "";

if (!$provider || !$email || !$token) {
    echo json_encode(["status" => "error", "message" => "Missing required fields"]);
    exit();
}

if (!$conn) {
    echo json_encode(["status" => "error", "message" => "DB connection failed"]);
    exit();
}

// --------------------------
// CHECK IF USER EXISTS
// --------------------------
$stmt = $conn->prepare("SELECT * FROM users WHERE email=? LIMIT 1");
$stmt->bind_param("s", $email);
$stmt->execute();
$res = $stmt->get_result();
$user = $res->fetch_assoc();
$stmt->close();

if ($user) {
    // UPDATE RECORD
    $stmt = $conn->prepare("
        UPDATE users SET
        provider=?,
        social_id=?,
        display_name=?,
        profile_picture=?,
        updated_at=NOW()
        WHERE email=?
    ");
    $stmt->bind_param("sssss", $provider, $socialId, $name, $profilePicture, $email);
    $stmt->execute();
    $stmt->close();

    $user["provider"] = $provider;
    $user["social_id"] = $socialId;
    $user["display_name"] = $name;
    $user["profile_picture"] = $profilePicture;
} else {
    // CREATE NEW USER
    $stmt = $conn->prepare("
        INSERT INTO users (username, display_name, email, provider, social_id, profile_picture, created_at)
        VALUES (?, ?, ?, ?, ?, ?, NOW())
    ");
    $stmt->bind_param("ssssss", $name, $name, $email, $provider, $socialId, $profilePicture);
    $stmt->execute();
    $newId = $stmt->insert_id;
    $stmt->close();

    $user = [
        "id" => $newId,
        "email" => $email,
        "display_name" => $name,
        "provider" => $provider,
        "social_id" => $socialId,
        "profile_picture" => $profilePicture,
    ];
}

// --------------------------
// CREATE API TOKEN
// --------------------------
$apiToken = bin2hex(random_bytes(32));
$stmt = $conn->prepare("UPDATE users SET api_token=? WHERE email=?");
$stmt->bind_param("ss", $apiToken, $email);
$stmt->execute();
$stmt->close();

echo json_encode([
    "status" => "success",
    "message" => "Social login successful",
    "user" => $user,
    "token" => $apiToken
]);
exit();
