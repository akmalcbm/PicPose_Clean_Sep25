<?php
header('Content-Type: application/json; charset=utf-8');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET, POST, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type, X-API-Key, Authorization');

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(204);
    exit();
}

require_once __DIR__ . '/../../config.php';

$API_KEY = '7a6f3c27a1b6d5e8e4c8a2b3f9e6d1f47c5b8a9d3e7f2c6a4b9e3d1c5f8a7b2c';

function auth_json_response(array $payload, int $code = 200): void {
    http_response_code($code);
    echo json_encode($payload);
    exit();
}

function auth_parse_input(): array {
    $raw = file_get_contents('php://input');
    $decoded = json_decode($raw ?? '', true);
    if (is_array($decoded)) {
        return $decoded;
    }
    return $_POST ?? [];
}

function auth_normalized_headers(): array {
    $headers = function_exists('getallheaders') ? getallheaders() : [];
    $norm = [];
    foreach ($headers as $k => $v) {
        $norm[strtolower($k)] = $v;
    }
    return $norm;
}

function auth_require_api_key(): void {
    global $API_KEY;
    $norm = auth_normalized_headers();
    $key = $norm['x-api-key']
        ?? $norm['x_api_key']
        ?? ($_SERVER['HTTP_X_API_KEY'] ?? null)
        ?? ($_GET['api_key'] ?? null)
        ?? ($_POST['api_key'] ?? null);

    if ($key !== $API_KEY) {
        auth_json_response([
            'status' => 'error',
            'message' => 'Invalid API Key'
        ], 401);
    }
}

function auth_is_debug_mode(): bool {
    $flag = getenv('PICPOSE_AUTH_DEBUG') ?: '0';
    return in_array(strtolower((string)$flag), ['1', 'true', 'yes', 'on'], true);
}

function auth_log_event(string $event, array $context = []): void {
    $safeContext = [];
    foreach ($context as $k => $v) {
        $safeContext[$k] = is_scalar($v) || $v === null ? $v : json_encode($v);
    }
    error_log('[auth] ' . $event . ' ' . json_encode($safeContext));
}

function auth_get_client_ip(): string {
    $forwarded = $_SERVER['HTTP_X_FORWARDED_FOR'] ?? '';
    if (!empty($forwarded)) {
        $first = trim(explode(',', $forwarded)[0]);
        if (filter_var($first, FILTER_VALIDATE_IP)) {
            return $first;
        }
    }
    $remote = $_SERVER['REMOTE_ADDR'] ?? '0.0.0.0';
    return filter_var($remote, FILTER_VALIDATE_IP) ? $remote : '0.0.0.0';
}

function auth_token_hash(string $token): string {
    $configuredKey = getenv('PICPOSE_APP_KEY');
    $appKey = !empty($configuredKey)
        ? $configuredKey
        : hash('sha256', (($GLOBALS['db_pass'] ?? '') . '|picpose-auth-key'));

    return hash_hmac('sha256', $token, $appKey);
}

function auth_generate_token(int $bytes = 32): string {
    return rtrim(strtr(base64_encode(random_bytes($bytes)), '+/', '-_'), '=');
}

function auth_send_email(string $to, string $subject, string $body): bool {
    $from = getenv('PICPOSE_MAIL_FROM') ?: 'noreply@picpose.iamakmal.in';
    $headers = [
        'MIME-Version: 1.0',
        'Content-Type: text/plain; charset=UTF-8',
        'From: PicPose <' . $from . '>',
        'Reply-To: ' . $from,
        'X-Mailer: PHP/' . phpversion(),
    ];

    return @mail($to, $subject, $body, implode("\r\n", $headers));
}

function auth_base_url(): string {
    $configured = getenv('PICPOSE_APP_URL');
    if (!empty($configured)) {
        return rtrim($configured, '/');
    }

    $scheme = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') ? 'https' : 'http';
    $host = $_SERVER['HTTP_HOST'] ?? 'localhost';
    return $scheme . '://' . $host;
}

function auth_is_strong_password(string $password): bool {
    if (strlen($password) < 8) return false;
    $hasLetter = preg_match('/[A-Za-z]/', $password) === 1;
    $hasDigit = preg_match('/\d/', $password) === 1;
    return $hasLetter && $hasDigit;
}
