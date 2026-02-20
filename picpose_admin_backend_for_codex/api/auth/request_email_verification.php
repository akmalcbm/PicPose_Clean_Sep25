<?php
require_once __DIR__ . '/auth_common.php';

auth_require_api_key();

if (!$conn || $conn->connect_errno) {
    auth_json_response(['status' => 'error', 'message' => 'Database unavailable'], 500);
}

$input = auth_parse_input();
$userId = (int)($input['user_id'] ?? 0);
$ip = auth_get_client_ip();
$userAgent = substr((string)($_SERVER['HTTP_USER_AGENT'] ?? ''), 0, 255);

if ($userId <= 0) {
    auth_json_response([
        'status' => 'success',
        'success' => true,
        'message' => 'If verification is possible, an email will be sent shortly.'
    ]);
}

try {
    $limitStmt = $conn->prepare(
        'SELECT COUNT(*) AS cnt FROM email_verifications WHERE user_id = ? AND created_at >= (NOW() - INTERVAL 1 HOUR)'
    );
    if (!$limitStmt) {
        throw new RuntimeException('Failed to prepare verification rate-limit query');
    }
    $limitStmt->bind_param('i', $userId);
    $limitStmt->execute();
    $count = (int)(($limitStmt->get_result()->fetch_assoc())['cnt'] ?? 0);
    $limitStmt->close();

    if ($count >= 5) {
        auth_log_event('email_verification_rate_limited', ['user_id' => $userId, 'ip' => $ip]);
        auth_json_response([
            'status' => 'success',
            'success' => true,
            'message' => 'If verification is possible, an email will be sent shortly.'
        ]);
    }

    $userStmt = $conn->prepare('SELECT id, email, display_name, username, email_verified FROM users WHERE id = ? LIMIT 1');
    if (!$userStmt) {
        throw new RuntimeException('Failed to prepare user lookup');
    }
    $userStmt->bind_param('i', $userId);
    $userStmt->execute();
    $user = $userStmt->get_result()->fetch_assoc();
    $userStmt->close();

    if (!$user || empty($user['email'])) {
        auth_json_response([
            'status' => 'success',
            'success' => true,
            'message' => 'If verification is possible, an email will be sent shortly.'
        ]);
    }

    if ((int)($user['email_verified'] ?? 0) === 1) {
        auth_json_response([
            'status' => 'success',
            'success' => true,
            'message' => 'Email is already verified.'
        ]);
    }

    $token = auth_generate_token(32);
    $tokenHash = auth_token_hash($token);

    $insertStmt = $conn->prepare(
        'INSERT INTO email_verifications (user_id, token_hash, expires_at, created_at, ip, user_agent) '
        . 'VALUES (?, ?, DATE_ADD(NOW(), INTERVAL 24 HOUR), NOW(), ?, ?)'
    );
    if (!$insertStmt) {
        throw new RuntimeException('Failed to prepare verification token insert');
    }
    $insertStmt->bind_param('isss', $userId, $tokenHash, $ip, $userAgent);
    $insertStmt->execute();
    $insertStmt->close();

    $name = trim((string)($user['display_name'] ?? $user['username'] ?? 'there'));
    if ($name === '') $name = 'there';

    $verifyLink = auth_base_url() . '/api/auth/verify_email.php?token=' . urlencode($token);
    $subject = 'Verify your PicPose email';
    $body = "Hi {$name},\n\n"
        . "Please verify your email for PicPose by opening this secure link (valid for 24 hours):\n"
        . $verifyLink . "\n\n"
        . "If you did not request this, you can ignore this email.\n\n"
        . "- PicPose Security";

    $mailSent = auth_send_email((string)$user['email'], $subject, $body);
    auth_log_event('email_verification_requested', [
        'user_id' => $userId,
        'ip' => $ip,
        'mail_sent' => $mailSent ? 1 : 0,
    ]);

    auth_json_response([
        'status' => 'success',
        'success' => true,
        'message' => 'If verification is possible, an email will be sent shortly.'
    ]);
} catch (Throwable $e) {
    auth_log_event('email_verification_request_error', [
        'user_id' => $userId,
        'ip' => $ip,
        'error' => $e->getMessage(),
    ]);

    auth_json_response([
        'status' => 'success',
        'success' => true,
        'message' => 'If verification is possible, an email will be sent shortly.'
    ]);
}
