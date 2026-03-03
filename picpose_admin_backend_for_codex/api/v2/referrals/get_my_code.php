<?php
require_once __DIR__ . '/../lib/v2_auth.php';

if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'GET') {
    json_err('Method Not Allowed', 405);
}

function referral_generate_code(): string
{
    return strtoupper(substr(bin2hex(random_bytes(8)), 0, 10));
}

$user = require_user($conn);
$userId = (int)$user['id'];

$stmt = $conn->prepare('SELECT code FROM referral_codes WHERE user_id = ? LIMIT 1');
if (!$stmt) {
    json_err('Database query preparation failed', 500);
}
$stmt->bind_param('i', $userId);
$stmt->execute();
$res = $stmt->get_result();
$row = $res ? $res->fetch_assoc() : null;
$stmt->close();

if ($row && !empty($row['code'])) {
    json_ok([
        'success' => true,
        'code' => (string)$row['code'],
    ]);
}

for ($attempt = 0; $attempt < 10; $attempt++) {
    $code = referral_generate_code();
    $insertStmt = $conn->prepare('INSERT INTO referral_codes (user_id, code) VALUES (?, ?)');
    if (!$insertStmt) {
        json_err('Database query preparation failed', 500);
    }
    $insertStmt->bind_param('is', $userId, $code);
    $ok = $insertStmt->execute();
    $errno = (int)$insertStmt->errno;
    $insertStmt->close();

    if ($ok) {
        json_ok([
            'success' => true,
            'code' => $code,
        ]);
    }

    if ($errno === 1062) {
        $checkStmt = $conn->prepare('SELECT code FROM referral_codes WHERE user_id = ? LIMIT 1');
        if ($checkStmt) {
            $checkStmt->bind_param('i', $userId);
            $checkStmt->execute();
            $checkRes = $checkStmt->get_result();
            $existing = $checkRes ? $checkRes->fetch_assoc() : null;
            $checkStmt->close();
            if ($existing && !empty($existing['code'])) {
                json_ok([
                    'success' => true,
                    'code' => (string)$existing['code'],
                ]);
            }
        }
        continue;
    }

    json_err('Failed to create referral code', 500);
}

json_err('Failed to generate unique referral code', 500);
