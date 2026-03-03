<?php
require_once __DIR__ . '/v2_common.php';

function get_bearer_token(): ?string
{
    $headers = function_exists('getallheaders') ? getallheaders() : [];
    $auth = $headers['Authorization']
        ?? $headers['authorization']
        ?? ($_SERVER['HTTP_AUTHORIZATION'] ?? null)
        ?? ($_SERVER['REDIRECT_HTTP_AUTHORIZATION'] ?? null);

    if (!is_string($auth) || $auth === '') {
        return null;
    }

    if (!preg_match('/^Bearer\s+(.+)$/i', trim($auth), $matches)) {
        return null;
    }

    $token = trim($matches[1]);
    return $token !== '' ? $token : null;
}

function require_user(mysqli $conn): array
{
    $token = get_bearer_token();

    if (!$token) {
        json_err('Unauthorized', 401);
    }

    $sql = 'SELECT id, email, account_type FROM users WHERE api_token = ? LIMIT 1';
    $stmt = $conn->prepare($sql);

    if (!$stmt) {
        json_err('Database query preparation failed', 500);
    }

    $stmt->bind_param('s', $token);
    $stmt->execute();
    $result = $stmt->get_result();
    $user = $result ? $result->fetch_assoc() : null;
    $stmt->close();

    if (!$user) {
        json_err('Unauthorized', 401);
    }

    return [
        'id' => (int)($user['id'] ?? 0),
        'email' => (string)($user['email'] ?? ''),
        'account_type' => (string)($user['account_type'] ?? 'normal'),
    ];
}
