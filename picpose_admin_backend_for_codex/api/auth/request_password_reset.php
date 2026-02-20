<?php
require_once __DIR__ . '/auth_common.php';

auth_require_api_key();

if (!$conn || $conn->connect_errno) {
    auth_json_response(['status' => 'error', 'message' => 'Database unavailable'], 500);
}

$input = auth_parse_input();
$email = strtolower(trim((string)($input['email'] ?? '')));
$ip = auth_get_client_ip();
$userAgent = substr((string)($_SERVER['HTTP_USER_AGENT'] ?? ''), 0, 255);
$genericMessage = 'If an account exists, you\'ll receive instructions.';

if ($email === '' || !filter_var($email, FILTER_VALIDATE_EMAIL)) {
    auth_json_response(['status' => 'success', 'success' => true, 'message' => $genericMessage]);
}

try {
    $emailLimit = 5;
    $ipLimit = 20;

    $limitStmt = $conn->prepare(
        'SELECT '
        . '(SELECT COUNT(*) FROM password_reset_requests WHERE email = ? AND created_at >= (NOW() - INTERVAL 1 HOUR)) AS email_count, '
        . '(SELECT COUNT(*) FROM password_reset_requests WHERE ip = ? AND created_at >= (NOW() - INTERVAL 1 HOUR)) AS ip_count'
    );
    if (!$limitStmt) {
        throw new RuntimeException('Failed to prepare rate-limit query');
    }
    $limitStmt->bind_param('ss', $email, $ip);
    $limitStmt->execute();
    $counts = $limitStmt->get_result()->fetch_assoc() ?: ['email_count' => 0, 'ip_count' => 0];
    $limitStmt->close();

    $requestLogStmt = $conn->prepare(
        'INSERT INTO password_reset_requests (email, ip, user_agent, created_at) VALUES (?, ?, ?, NOW())'
    );
    if ($requestLogStmt) {
        $requestLogStmt->bind_param('sss', $email, $ip, $userAgent);
        $requestLogStmt->execute();
        $requestLogStmt->close();
    }

    if ((int)$counts['email_count'] >= $emailLimit || (int)$counts['ip_count'] >= $ipLimit) {
        auth_log_event('password_reset_rate_limited', ['email' => $email, 'ip' => $ip]);
        auth_json_response(['status' => 'success', 'success' => true, 'message' => $genericMessage]);
    }

    $userStmt = $conn->prepare('SELECT id, email, display_name, username FROM users WHERE email = ? LIMIT 1');
    if (!$userStmt) {
        throw new RuntimeException('Failed to prepare user lookup');
    }
    $userStmt->bind_param('s', $email);
    $userStmt->execute();
    $user = $userStmt->get_result()->fetch_assoc();
    $userStmt->close();

    if ($user) {
        $token = auth_generate_token(32);
        $tokenHash = auth_token_hash($token);
        $userId = (int)$user['id'];

        $insertStmt = $conn->prepare(
            'INSERT INTO password_resets (user_id, token_hash, expires_at, created_at, ip, user_agent) '
            . 'VALUES (?, ?, DATE_ADD(NOW(), INTERVAL 30 MINUTE), NOW(), ?, ?)'
        );
        if (!$insertStmt) {
            throw new RuntimeException('Failed to prepare reset token insert');
        }
        $insertStmt->bind_param('isss', $userId, $tokenHash, $ip, $userAgent);
        $insertStmt->execute();
        $insertStmt->close();

        $name = trim((string)($user['display_name'] ?? $user['username'] ?? 'there'));
        if ($name === '') $name = 'there';

        $webResetLink = auth_base_url() . '/api/auth/reset_password_page.php?token=' . urlencode($token);
        $subject = 'PicPose Password Reset';
        $body = "Hi {$name},\n\n"
            . "We received a request to reset your PicPose password.\n\n"
            . "Reset your password using this secure link (valid for 30 minutes):\n"
            . $webResetLink . "\n\n"
            . "If you didn\'t request this, you can safely ignore this email.\n\n"
            . "- PicPose Security";

        $mailSent = auth_send_email($user['email'], $subject, $body);
        auth_log_event('password_reset_requested', [
            'user_id' => $userId,
            'ip' => $ip,
            'mail_sent' => $mailSent ? 1 : 0,
        ]);
    }

    auth_json_response(['status' => 'success', 'success' => true, 'message' => $genericMessage]);
} catch (Throwable $e) {
    auth_log_event('password_reset_request_error', ['error' => $e->getMessage(), 'ip' => $ip]);
    auth_json_response(['status' => 'success', 'success' => true, 'message' => $genericMessage]);
}
