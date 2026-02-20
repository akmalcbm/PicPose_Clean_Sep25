<?php
require_once __DIR__ . '/auth_common.php';

auth_require_api_key();

if (!$conn || $conn->connect_errno) {
    auth_json_response(['status' => 'error', 'message' => 'Database unavailable'], 500);
}

$input = auth_parse_input();
$token = trim((string)($input['token'] ?? ''));
$newPassword = (string)($input['new_password'] ?? '');

if ($token === '' || !auth_is_strong_password($newPassword)) {
    auth_json_response([
        'status' => 'error',
        'success' => false,
        'message' => 'Invalid or expired reset token.'
    ], 400);
}

$tokenHash = auth_token_hash($token);
$ip = auth_get_client_ip();

try {
    $stmt = $conn->prepare(
        'SELECT pr.id AS reset_id, pr.user_id AS user_id '
        . 'FROM password_resets pr '
        . 'WHERE pr.token_hash = ? AND pr.used_at IS NULL AND pr.expires_at >= NOW() '
        . 'ORDER BY pr.id DESC LIMIT 1'
    );
    if (!$stmt) {
        throw new RuntimeException('Failed to prepare token lookup');
    }
    $stmt->bind_param('s', $tokenHash);
    $stmt->execute();
    $tokenRow = $stmt->get_result()->fetch_assoc();
    $stmt->close();

    if (!$tokenRow) {
        auth_log_event('password_reset_invalid_token', ['ip' => $ip]);
        auth_json_response([
            'status' => 'error',
            'success' => false,
            'message' => 'Invalid or expired reset token.'
        ], 400);
    }

    $conn->begin_transaction();

    $newHash = password_hash($newPassword, PASSWORD_DEFAULT);
    $userId = (int)$tokenRow['user_id'];
    $resetId = (int)$tokenRow['reset_id'];

    $updatePasswordStmt = $conn->prepare('UPDATE users SET password = ? WHERE id = ? LIMIT 1');
    if (!$updatePasswordStmt) {
        throw new RuntimeException('Failed to prepare password update');
    }
    $updatePasswordStmt->bind_param('si', $newHash, $userId);
    $updatePasswordStmt->execute();
    $updatePasswordStmt->close();

    $useTokenStmt = $conn->prepare('UPDATE password_resets SET used_at = NOW() WHERE id = ? LIMIT 1');
    if (!$useTokenStmt) {
        throw new RuntimeException('Failed to mark token used');
    }
    $useTokenStmt->bind_param('i', $resetId);
    $useTokenStmt->execute();
    $useTokenStmt->close();

    $invalidateOthersStmt = $conn->prepare(
        'UPDATE password_resets SET used_at = NOW() WHERE user_id = ? AND used_at IS NULL AND id <> ?'
    );
    if ($invalidateOthersStmt) {
        $invalidateOthersStmt->bind_param('ii', $userId, $resetId);
        $invalidateOthersStmt->execute();
        $invalidateOthersStmt->close();
    }

    $conn->commit();

    auth_log_event('password_reset_success', ['user_id' => $userId, 'ip' => $ip]);

    auth_json_response([
        'status' => 'success',
        'success' => true,
        'message' => 'Password reset successful.'
    ]);
} catch (Throwable $e) {
    if ($conn) {
        @$conn->rollback();
    }
    auth_log_event('password_reset_error', ['error' => $e->getMessage(), 'ip' => $ip]);
    auth_json_response([
        'status' => 'error',
        'success' => false,
        'message' => 'Invalid or expired reset token.'
    ], 400);
}
