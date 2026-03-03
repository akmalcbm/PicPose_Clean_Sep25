<?php
require_once __DIR__ . '/../lib/v2_common.php';

if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') {
    json_err('Method Not Allowed', 405);
}

$raw = file_get_contents('php://input');
$input = json_decode($raw ?? '', true);
if (!is_array($input)) {
    $input = $_POST ?? [];
}

$email = trim((string)($input['email'] ?? ''));
$password = (string)($input['password'] ?? '');

if ($email === '' || $password === '') {
    json_err('Email and password are required', 400);
}

if (!filter_var($email, FILTER_VALIDATE_EMAIL)) {
    json_err('Invalid email', 400);
}

$stmt = $conn->prepare("
    SELECT
        id,
        username,
        display_name,
        email,
        provider,
        profile_pic,
        profile_picture,
        bio,
        account_type,
        created_at,
        updated_at,
        password
    FROM users
    WHERE email = ?
    LIMIT 1
");
if (!$stmt) {
    json_err('Database query preparation failed', 500);
}

$stmt->bind_param('s', $email);
$stmt->execute();
$result = $stmt->get_result();
$user = $result ? $result->fetch_assoc() : null;
$stmt->close();

if (!$user || empty($user['password']) || !password_verify($password, (string)$user['password'])) {
    json_err('Invalid email or password', 401);
}

$apiToken = bin2hex(random_bytes(32));

$updateStmt = $conn->prepare("UPDATE users SET api_token = ?, updated_at = NOW() WHERE id = ? LIMIT 1");
if (!$updateStmt) {
    json_err('Database query preparation failed', 500);
}
$userId = (int)$user['id'];
$updateStmt->bind_param('si', $apiToken, $userId);
if (!$updateStmt->execute()) {
    $updateStmt->close();
    json_err('Failed to update api token', 500);
}
$updateStmt->close();

unset($user['password']);

echo json_encode([
    'status' => 'success',
    'user' => $user,
    'token' => $apiToken,
], JSON_UNESCAPED_UNICODE);
exit();
