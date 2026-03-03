<?php
header('Content-Type: application/json; charset=utf-8');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET, POST, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type, X-API-Key, Authorization');

if (($_SERVER['REQUEST_METHOD'] ?? '') === 'OPTIONS') {
    http_response_code(204);
    exit();
}

require_once __DIR__ . '/../../../config.php';

$V2_API_KEY = '7a6f3c27a1b6d5e8e4c8a2b3f9e6d1f47c5b8a9d3e7f2c6a4b9e3d1c5f8a7b2c';

function v2_headers_normalized(): array
{
    $headers = function_exists('getallheaders') ? getallheaders() : [];
    $norm = [];

    foreach ($headers as $k => $v) {
        $norm[strtolower((string)$k)] = $v;
    }

    return $norm;
}

function json_ok(array $arr): void
{
    http_response_code(200);
    echo json_encode($arr);
    exit();
}

function json_err(string $msg, int $code = 400): void
{
    http_response_code($code);
    echo json_encode([
        'success' => false,
        'message' => $msg,
    ]);
    exit();
}

if (!isset($conn) || !$conn || (isset($conn->connect_errno) && $conn->connect_errno)) {
    json_err('Database connection failed', 500);
}

$norm = v2_headers_normalized();
$providedKey = $norm['x-api-key']
    ?? $norm['x_api_key']
    ?? ($_SERVER['HTTP_X_API_KEY'] ?? null)
    ?? ($_GET['api_key'] ?? null);

if ($providedKey !== $V2_API_KEY) {
    json_err('Invalid API Key', 401);
}
