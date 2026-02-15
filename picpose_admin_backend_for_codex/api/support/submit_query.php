<?php
/**
 * /api/support/submit_query.php
 * Handles help & support queries submitted from the PicPose Android app.
 * Improved version with enhanced error handling, API key validation,
 * secure database operations, and detailed responses.
 */

declare(strict_types=1);

// ---------------------------------------------
// 🔧 Basic Configuration
// ---------------------------------------------
ini_set('display_errors', '0');           // Hide errors from users
error_reporting(E_ALL);                   // Log all errors internally
header('Content-Type: application/json; charset=utf-8');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: POST, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type, X-API-Key');
header('Cache-Control: no-store');

// ---------------------------------------------
// 🟡 Handle Preflight (OPTIONS)
if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(200);
    exit;
}

// ---------------------------------------------
// 🗝️ Validate API Key (optional but recommended)
$providedKey = $_GET['api_key'] ?? '';
$expectedKey = '7a6f3c27a1b6d5e8e4c8a2b3f9e6d1f47c5b8a9d3e7f2c6a4b9e3d1c5f8a7b2c';
if ($providedKey !== $expectedKey) {
    http_response_code(401);
    echo json_encode([
        "success" => false,
        "message" => "Unauthorized: Invalid API key."
    ]);
    exit;
}

// ---------------------------------------------
// ⚙️ Load Config + Database Connection
require_once __DIR__ . '/../../config.php';
if (!file_exists(__DIR__ . '/../../config.php')) {
    http_response_code(500);
    echo json_encode(["success" => false, "message" => "config.php not found"]);
    exit;
}

// Validate that $pdo exists and is connected
if (!isset($pdo) || !$pdo instanceof PDO) {
    http_response_code(500);
    echo json_encode([
        "success" => false,
        "message" => "Database connection failed. Check config.php."
    ]);
    exit;
}

// ---------------------------------------------
// 📥 Validate Request Method
if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    http_response_code(405);
    echo json_encode([
        "success" => false,
        "message" => "Method Not Allowed. Use POST."
    ]);
    exit;
}

// ---------------------------------------------
// 📦 Parse JSON Input
$input = json_decode(file_get_contents("php://input"), true);

if (!is_array($input)) {
    echo json_encode([
        "success" => false,
        "message" => "Invalid or empty JSON payload."
    ]);
    exit;
}

// ---------------------------------------------
// 🧾 Extract and Sanitize Fields
$name    = trim($input['name'] ?? '');
$email   = trim($input['email'] ?? '');
$phone   = trim($input['phone'] ?? '');
$message = trim($input['message'] ?? '');

// Required fields
if ($name === '' || $email === '' || $message === '') {
    echo json_encode([
        "success" => false,
        "message" => "Name, email, and message are required."
    ]);
    exit;
}

// Validate email format
if (!filter_var($email, FILTER_VALIDATE_EMAIL)) {
    echo json_encode([
        "success" => false,
        "message" => "Invalid email format."
    ]);
    exit;
}

// Optional: Phone number cleanup
if ($phone !== '') {
    $phone = preg_replace('/[^0-9+\-\s]/', '', $phone); // keep numbers and symbols only
}

// ---------------------------------------------
// 💾 Save Query to Database
try {
    $stmt = $pdo->prepare("
        INSERT INTO support_queries (name, email, phone, message, created_at)
        VALUES (:name, :email, :phone, :message, NOW())
    ");

    $stmt->execute([
        ':name'    => $name,
        ':email'   => $email,
        ':phone'   => $phone,
        ':message' => $message
    ]);

    // ---------------------------------------------
    // 📧 Send Admin Notification (optional)
    $adminEmail = "picposeapp@gmail.com"; // Support inbox
    $subject = "📩 New Support Query from $name";
    $body = "You have received a new support message from PicPose App:\n\n"
          . "Name: $name\n"
          . "Email: $email\n"
          . "Phone: $phone\n\n"
          . "Message:\n$message\n\n"
          . "Submitted on: " . date('Y-m-d H:i:s');
    
    // Use safe mail sending
    @mail($adminEmail, $subject, $body, "From: noreply@picpose.iamakmal.in");

    echo json_encode([
        "success" => true,
        "message" => "✅ Your query has been submitted successfully! We'll get back to you soon."
    ]);

} catch (PDOException $e) {
    http_response_code(500);
    echo json_encode([
        "success" => false,
        "message" => "Database error: " . $e->getMessage()
    ]);
} catch (Throwable $e) {
    http_response_code(500);
    echo json_encode([
        "success" => false,
        "message" => "Unexpected server error: " . $e->getMessage()
    ]);
}
