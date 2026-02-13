<?php
// api/common_api.php
// Shared helpers used by API endpoints (ai_posts, guide_posts, etc.)
//
// Place this file in api/ and include it from endpoints:
//   require_once __DIR__ . '/common_api.php';
//
// Requirements:
// - config.php should create $conn (mysqli) and may define API_ADMIN_TOKEN
// - Functions provided here are intentionally defensive and return JSON error responses

// -------------------------
// Configuration
// -------------------------
if (!defined('API_DEBUG')) define('API_DEBUG', false); // set true in dev to include more error details

// -------------------------
// Utility / Response Helpers
// -------------------------
function send_json($data, $code = 200) {
    http_response_code($code);
    header('Content-Type: application/json; charset=utf-8');
    // Prevent UTF-8 encoding surprises and try to produce valid JSON even if partial
    echo json_encode($data, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_PARTIAL_OUTPUT_ON_ERROR);
    exit;
}

function error_json($message, $code = 400, $details = null) {
    $payload = ['error' => $message];
    if (API_DEBUG && $details) $payload['details'] = $details;
    send_json($payload, $code);
}

// Convenience: return request body parsed from JSON or empty array
function get_request_body(): array {
    $raw = @file_get_contents('php://input');
    if ($raw) {
        $decoded = @json_decode($raw, true);
        if (json_last_error() === JSON_ERROR_NONE && is_array($decoded)) {
            return $decoded;
        }
    }
    // fallback to $_REQUEST (includes $_POST + $_GET)
    return $_REQUEST ?? [];
}

// Safe getallheaders fallback for non-Apache environments
function safe_getallheaders(): array {
    if (function_exists('getallheaders')) {
        return getallheaders();
    }
    $headers = [];
    foreach ($_SERVER as $name => $value) {
        if (substr($name, 0, 5) === 'HTTP_') {
            $key = str_replace(' ', '-', ucwords(strtolower(str_replace('_', ' ', substr($name, 5)))));
            $headers[$key] = $value;
        }
    }
    // also include CONTENT_TYPE and CONTENT_LENGTH if set
    if (!empty($_SERVER['CONTENT_TYPE'])) $headers['Content-Type'] = $_SERVER['CONTENT_TYPE'];
    if (!empty($_SERVER['CONTENT_LENGTH'])) $headers['Content-Length'] = $_SERVER['CONTENT_LENGTH'];
    return $headers;
}

// Get Bearer token from Authorization header (or null)
function get_bearer_token(): ?string {
    $headers = safe_getallheaders();
    $auth = $headers['Authorization'] ?? $headers['authorization'] ?? null;
    if ($auth && preg_match('/Bearer\s+(.+)$/i', $auth, $m)) {
        return trim($m[1]);
    }
    return null;
}

// -------------------------
// Authorization helpers
// -------------------------
// Uses session-based admin (for admin panel) OR Bearer token matching API_ADMIN_TOKEN or env
function require_admin() {
    // Prefer session-based admin
    if (session_status() !== PHP_SESSION_ACTIVE) session_start();
    if (!empty($_SESSION['admin'])) return true;

    // Otherwise check Authorization Bearer token header
    $token = get_bearer_token();
    if ($token) {
        if (defined('API_ADMIN_TOKEN') && $token === API_ADMIN_TOKEN) return true;
        $envToken = getenv('API_ADMIN_TOKEN') ?: null;
        if (!empty($envToken) && $token === $envToken) return true;
    }

    error_json('Unauthorized', 401);
}

// Optional: safe check function (returns bool rather than terminating)
function is_admin(): bool {
    if (session_status() !== PHP_SESSION_ACTIVE) session_start();
    if (!empty($_SESSION['admin'])) return true;
    $token = get_bearer_token();
    if ($token) {
        if (defined('API_ADMIN_TOKEN') && $token === API_ADMIN_TOKEN) return true;
        $envToken = getenv('API_ADMIN_TOKEN') ?: null;
        if (!empty($envToken) && $token === $envToken) return true;
    }
    return false;
}

// -------------------------
// Pagination helper
// -------------------------
// returns [page, limit, offset]
function parse_pagination(): array {
    $page = max(1, intval($_GET['page'] ?? ($_POST['page'] ?? 1)));
    $limit = intval($_GET['limit'] ?? ($_POST['limit'] ?? 20));
    // enforce sensible limits
    if ($limit < 1) $limit = 1;
    if ($limit > 200) $limit = 200; // hard cap
    $offset = max(0, intval($_GET['offset'] ?? ($_POST['offset'] ?? (($page - 1) * $limit))));
    return [$page, $limit, $offset];
}

// -------------------------
// Search clause builder
// -------------------------
// Builds a SQL fragment (starting with AND ...) and fills $types and $params by reference
// Default searches title, short_description, tags, and prompt_text (if present).
// Caller is responsible for using prepared statements and passing $types/$params to bind_param.
function build_search_clause($q, string &$types = null, array &$params = null): string {
    $types = '';
    $params = [];
    $q = trim((string)$q);
    if ($q === '') return '';

    // Some tables may not have prompt_text; caller should adapt if needed.
    $clause = " AND (title LIKE CONCAT('%', ?, '%') OR short_description LIKE CONCAT('%', ?, '%') OR tags LIKE CONCAT('%', ?, '%') OR prompt_text LIKE CONCAT('%', ?, '%'))";
    $types = 'ssss';
    $params = [$q, $q, $q, $q];
    return $clause;
}

// Alternative simple search clause if you want to search fewer fields
function build_search_clause_minimal($q, string &$types = null, array &$params = null): string {
    $types = '';
    $params = [];
    $q = trim((string)$q);
    if ($q === '') return '';
    $clause = " AND (title LIKE CONCAT('%', ?, '%') OR short_description LIKE CONCAT('%', ?, '%') OR tags LIKE CONCAT('%', ?, '%'))";
    $types = 'sss';
    $params = [$q, $q, $q];
    return $clause;
}

// -------------------------
// Misc helpers
// -------------------------
function json_or_empty($key, $default = null) {
    $body = get_request_body();
    return $body[$key] ?? $default;
}

// Simple logger helper (writes to PHP error_log)
function api_log($msg) {
    if (is_array($msg) || is_object($msg)) {
        error_log('[api] ' . print_r($msg, true));
    } else {
        error_log('[api] ' . $msg);
    }
}

// Optional CORS helper - call at top of endpoint if needed
function allow_cors() {
    header('Access-Control-Allow-Origin: *'); // adjust for production to restrict origins
    header('Access-Control-Allow-Methods: GET, POST, PUT, PATCH, DELETE, OPTIONS');
    header('Access-Control-Allow-Headers: Authorization, Content-Type, X-Requested-With');
    if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
        http_response_code(204);
        exit;
    }
}

// -------------------------
// Backwards compatibility aliases (if older code references these)
// -------------------------
if (!function_exists('send_json')) {
    function send_json($data, $code = 200) { http_response_code($code); header('Content-Type: application/json; charset=utf-8'); echo json_encode($data, JSON_UNESCAPED_UNICODE); exit; }
}
if (!function_exists('error_json')) {
    function error_json($message, $code = 400) { send_json(['error' => $message], $code); }
}
