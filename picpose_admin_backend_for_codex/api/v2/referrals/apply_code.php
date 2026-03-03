<?php
require_once __DIR__ . '/../lib/v2_auth.php';

if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') {
    json_err('Method Not Allowed', 405);
}

$user = require_user($conn);
$refereeId = (int)$user['id'];

$payload = json_decode(file_get_contents('php://input') ?: '', true);
if (!is_array($payload)) {
    json_err('Invalid JSON body', 400);
}

$code = strtoupper(trim((string)($payload['code'] ?? '')));
if ($code === '' || strlen($code) > 16) {
    json_err('Invalid code', 400);
}

$codeStmt = $conn->prepare('SELECT user_id FROM referral_codes WHERE code = ? LIMIT 1');
if (!$codeStmt) {
    json_err('Database query preparation failed', 500);
}
$codeStmt->bind_param('s', $code);
$codeStmt->execute();
$codeRes = $codeStmt->get_result();
$codeRow = $codeRes ? $codeRes->fetch_assoc() : null;
$codeStmt->close();

if (!$codeRow) {
    json_err('Referral code not found', 404);
}

$referrerId = (int)$codeRow['user_id'];
if ($referrerId === $refereeId) {
    json_err('You cannot apply your own referral code', 400);
}

$existingStmt = $conn->prepare('SELECT id, referrer_id, status FROM referrals WHERE referee_id = ? LIMIT 1');
if (!$existingStmt) {
    json_err('Database query preparation failed', 500);
}
$existingStmt->bind_param('i', $refereeId);
$existingStmt->execute();
$existingRes = $existingStmt->get_result();
$existing = $existingRes ? $existingRes->fetch_assoc() : null;
$existingStmt->close();

if ($existing) {
    if ((int)$existing['referrer_id'] === $referrerId) {
        json_ok([
            'success' => true,
            'already_applied' => true,
            'already_claimed' => true,
            'message' => 'You can apply only one code',
        ]);
    }
    json_err('You can apply only one code', 409);
}

$insertStmt = $conn->prepare("
    INSERT INTO referrals (referrer_id, referee_id, status)
    VALUES (?, ?, 'PENDING')
");
if (!$insertStmt) {
    json_err('Database query preparation failed', 500);
}
$insertStmt->bind_param('ii', $referrerId, $refereeId);
$ok = $insertStmt->execute();
$errno = (int)$insertStmt->errno;
$insertStmt->close();

if (!$ok) {
    if ($errno === 1062) {
        json_ok([
            'success' => true,
            'already_applied' => true,
            'already_claimed' => true,
            'message' => 'You can apply only one code',
        ]);
    }
    json_err('Failed to apply referral code', 500);
}

json_ok([
    'success' => true,
    'message' => 'Code applied successfully',
]);
