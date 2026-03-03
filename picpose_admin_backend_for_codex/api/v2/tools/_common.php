<?php

declare(strict_types=1);

if (!defined('V2_TOOLS_BOOTSTRAPPED')) {
    define('V2_TOOLS_BOOTSTRAPPED', true);

    $rootConfig = dirname(__DIR__, 3) . '/config.php';
    if (!file_exists($rootConfig)) {
        $stream = defined('STDERR') ? STDERR : STDOUT;
        fwrite($stream, "Root config.php not found\n");
        exit(1);
    }

    try {
        require_once $rootConfig;
    } catch (Throwable $e) {
        $stream = defined('STDERR') ? STDERR : STDOUT;
        fwrite($stream, "Database bootstrap failed: " . $e->getMessage() . PHP_EOL);
        exit(1);
    }
}

if (!isset($conn) || !($conn instanceof mysqli)) {
    $stream = defined('STDERR') ? STDERR : STDOUT;
    fwrite($stream, "MySQLi connection is unavailable\n");
    exit(1);
}

function v2_tool_is_cli(): bool
{
    return PHP_SAPI === 'cli';
}

function v2_tool_render_header(string $title): void
{
    if (!v2_tool_is_cli()) {
        header('Content-Type: text/html; charset=UTF-8');
        echo "<!doctype html><html><head><meta charset=\"utf-8\"><title>" . htmlspecialchars($title, ENT_QUOTES, 'UTF-8') . "</title>";
        echo '<style>body{font-family:Menlo,Consolas,monospace;background:#111;color:#eee;padding:24px}pre{white-space:pre-wrap} .pass{color:#76d275}.fail{color:#ff6b6b}.skip{color:#f6c85f}.info{color:#7ad1ff}</style>';
        echo '</head><body><h1>' . htmlspecialchars($title, ENT_QUOTES, 'UTF-8') . '</h1><pre>';
    }
}

function v2_tool_render_footer(): void
{
    if (!v2_tool_is_cli()) {
        echo '</pre></body></html>';
    }
}

function v2_tool_echo_line(string $line, string $class = 'info'): void
{
    if (v2_tool_is_cli()) {
        echo $line . PHP_EOL;
        return;
    }

    echo '<span class="' . htmlspecialchars($class, ENT_QUOTES, 'UTF-8') . '">' . htmlspecialchars($line, ENT_QUOTES, 'UTF-8') . "</span>\n";
}

function v2_tool_print(string $status, string $name, string $details): void
{
    $classMap = [
        'PASS' => 'pass',
        'FAIL' => 'fail',
        'SKIP' => 'skip',
        'INFO' => 'info',
    ];
    $class = $classMap[$status] ?? 'info';
    v2_tool_echo_line(sprintf('[%s] %s - %s', $status, $name, $details), $class);
}

function v2_tool_pass(string $name, string $details): void
{
    v2_tool_print('PASS', $name, $details);
}

function v2_tool_fail(string $name, string $details): void
{
    v2_tool_print('FAIL', $name, $details);
}

function v2_tool_skip(string $name, string $details): void
{
    v2_tool_print('SKIP', $name, $details);
}

function v2_tool_info(string $name, string $details): void
{
    v2_tool_print('INFO', $name, $details);
}

function v2_tool_mask_secret(?string $value): string
{
    $value = (string)$value;
    if ($value === '') {
        return '';
    }
    $length = strlen($value);
    if ($length <= 4) {
        return str_repeat('*', $length);
    }
    return substr($value, 0, 2) . str_repeat('*', max(0, $length - 4)) . substr($value, -2);
}

function v2_tool_load_config(): array
{
    static $config = null;
    if ($config !== null) {
        return $config;
    }

    $fileConfig = [];
    $configPath = __DIR__ . '/config.php';
    if (file_exists($configPath)) {
        $loaded = require $configPath;
        if (is_array($loaded)) {
            $fileConfig = $loaded;
        }
    }

    $defaults = [
        'BASE_URL' => '',
        'API_KEY' => '',
        'TEST_EMAIL' => '',
        'TEST_PASS' => '',
        'TEST_TOKEN' => '',
        'HTTP_TIMEOUT' => 20,
    ];

    $config = $defaults;
    foreach ($defaults as $key => $defaultValue) {
        $envValue = getenv($key);
        if ($envValue !== false && $envValue !== '') {
            $config[$key] = $envValue;
            continue;
        }
        if (array_key_exists($key, $fileConfig) && $fileConfig[$key] !== '') {
            $config[$key] = $fileConfig[$key];
        }
    }

    $config['BASE_URL'] = rtrim((string)$config['BASE_URL'], '/');
    $config['HTTP_TIMEOUT'] = max(5, (int)$config['HTTP_TIMEOUT']);

    return $config;
}

function v2_tool_table_exists(mysqli $conn, string $tableName): bool
{
    $stmt = $conn->prepare('SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ? LIMIT 1');
    if (!$stmt) {
        return false;
    }
    $stmt->bind_param('s', $tableName);
    $stmt->execute();
    $exists = (bool)$stmt->get_result()->fetch_assoc();
    $stmt->close();
    return $exists;
}

function v2_tool_column_exists(mysqli $conn, string $tableName, string $columnName): bool
{
    $stmt = $conn->prepare('SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ? LIMIT 1');
    if (!$stmt) {
        return false;
    }
    $stmt->bind_param('ss', $tableName, $columnName);
    $stmt->execute();
    $exists = (bool)$stmt->get_result()->fetch_assoc();
    $stmt->close();
    return $exists;
}

function v2_tool_get_column_meta(mysqli $conn, string $tableName, string $columnName): ?array
{
    $stmt = $conn->prepare('SELECT column_default, is_nullable, column_type, extra FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ? LIMIT 1');
    if (!$stmt) {
        return null;
    }
    $stmt->bind_param('ss', $tableName, $columnName);
    $stmt->execute();
    $res = $stmt->get_result();
    $row = $res ? $res->fetch_assoc() : null;
    $stmt->close();
    return $row ?: null;
}

function v2_tool_index_exists(mysqli $conn, string $tableName, string $indexName): bool
{
    $stmt = $conn->prepare('SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ? LIMIT 1');
    if (!$stmt) {
        return false;
    }
    $stmt->bind_param('ss', $tableName, $indexName);
    $stmt->execute();
    $exists = (bool)$stmt->get_result()->fetch_assoc();
    $stmt->close();
    return $exists;
}

function v2_tool_fetch_value(mysqli $conn, string $sql, string $types = '', array $params = [])
{
    $stmt = $conn->prepare($sql);
    if (!$stmt) {
        return null;
    }
    if ($types !== '' && !empty($params)) {
        $stmt->bind_param($types, ...$params);
    }
    if (!$stmt->execute()) {
        $stmt->close();
        return null;
    }
    $res = $stmt->get_result();
    $row = $res ? $res->fetch_row() : null;
    $stmt->close();
    return $row[0] ?? null;
}

function v2_tool_http_request(string $method, string $path, array $options = []): array
{
    $config = v2_tool_load_config();
    $baseUrl = $config['BASE_URL'];
    if ($baseUrl === '') {
        return [
            'ok' => false,
            'status' => 0,
            'error' => 'BASE_URL not configured',
            'body' => '',
            'json' => null,
        ];
    }

    $url = preg_match('#^https?://#i', $path) ? $path : $baseUrl . '/' . ltrim($path, '/');
    $query = $options['query'] ?? [];
    if (!empty($query)) {
        $qs = http_build_query($query);
        $url .= (str_contains($url, '?') ? '&' : '?') . $qs;
    }

    $headers = $options['headers'] ?? [];
    if (!empty($options['api_key'])) {
        $headers[] = 'X-API-Key: ' . $options['api_key'];
    }
    if (!empty($options['token'])) {
        $headers[] = 'Authorization: Bearer ' . $options['token'];
    }

    $body = $options['body'] ?? null;
    if (is_array($body)) {
        $body = json_encode($body);
        $headers[] = 'Content-Type: application/json';
    }

    $ch = curl_init($url);
    curl_setopt_array($ch, [
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_FOLLOWLOCATION => true,
        CURLOPT_TIMEOUT => (int)$config['HTTP_TIMEOUT'],
        CURLOPT_CUSTOMREQUEST => strtoupper($method),
        CURLOPT_HTTPHEADER => $headers,
        CURLOPT_HEADER => false,
    ]);
    if ($body !== null) {
        curl_setopt($ch, CURLOPT_POSTFIELDS, $body);
    }

    $responseBody = curl_exec($ch);
    $error = curl_error($ch);
    $status = (int)curl_getinfo($ch, CURLINFO_HTTP_CODE);
    curl_close($ch);

    $decoded = null;
    if (is_string($responseBody) && $responseBody !== '') {
        $decoded = json_decode($responseBody, true);
        if (!is_array($decoded)) {
            $decoded = null;
        }
    }

    return [
        'ok' => $error === '',
        'status' => $status,
        'error' => $error,
        'body' => is_string($responseBody) ? $responseBody : '',
        'json' => $decoded,
        'url' => $url,
    ];
}

function v2_tool_endpoint_exists(string $relativePath): bool
{
    $fullPath = dirname(__DIR__) . '/' . ltrim($relativePath, '/');
    return file_exists($fullPath);
}

function v2_tool_finish(int $exitCode): void
{
    v2_tool_render_footer();
    if (v2_tool_is_cli()) {
        exit($exitCode);
    }
}
