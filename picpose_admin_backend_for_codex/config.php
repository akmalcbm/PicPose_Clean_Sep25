<?php
/**
 * config.php — Hybrid version supporting both PDO and MySQLi
 * Works with all your APIs (old + new)
 */

/* =======================
   SESSION HANDLING
   ======================= */
if (session_status() === PHP_SESSION_NONE) {
    session_set_cookie_params([
        'lifetime' => 86400, // 24 hours
        'path' => '/',
        'domain' => $_SERVER['HTTP_HOST'] ?? '',
        'secure' => (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off'),
        'httponly' => true,
        'samesite' => 'Lax'
    ]);
    @session_start();
}

/* =======================
   ERROR REPORTING
   ======================= */
error_reporting(E_ALL);
ini_set('display_errors', 1);
ini_set('display_startup_errors', 1);
ini_set('log_errors', 1);
ini_set('error_log', __DIR__ . '/php_error.log');

/* =======================
   DATABASE CONFIG
   ======================= */
$db_host = "localhost";
$db_user = "u856987069_picpose_un";
$db_pass = "@ks@M2025";
$db_name = "u856987069_picpose_dbn";

/* =======================
   MYSQLI CONNECTION
   ======================= */
$conn = @new mysqli($db_host, $db_user, $db_pass, $db_name);
if ($conn->connect_errno) {
    error_log("MySQLi connect failed: " . $conn->connect_error);
    $conn = null;
    die("Database connection failed. Please check your configuration.");
} else {
    /**
     * IMPORTANT (Collation fix):
     * Your tables are utf8mb4_unicode_ci, but the MySQL server default collation is utf8mb4_uca1400_ai_ci.
     * If the connection/session collation differs from the column collation, MySQL can throw:
     *   "Illegal mix of collations" (error 1267)
     * when comparing strings (column = parameter, parameter = 'literal', CASE, ORDER BY, etc).
     *
     * This forces the connection to use utf8mb4 + utf8mb4_unicode_ci consistently.
     */
    if (!$conn->set_charset("utf8mb4")) {
        error_log("Failed to set mysqli charset to utf8mb4: " . $conn->error);
    }

    // Stronger than only setting collation_connection: it sets client/connection/results too.
    if (!$conn->query("SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci")) {
        error_log("Failed to SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci: " . $conn->error);
    }

    // Keep explicit session collation as well (defensive).
    if (!$conn->query("SET SESSION collation_connection = 'utf8mb4_unicode_ci'")) {
        error_log("Failed to SET SESSION collation_connection: " . $conn->error);
    }
}


/* =======================
   PDO CONNECTION
   ======================= */
try {
    $pdo = new PDO(
        "mysql:host=$db_host;dbname=$db_name;charset=utf8mb4",
        $db_user,
        $db_pass,
        [
            PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
            PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
            PDO::ATTR_EMULATE_PREPARES => false,
        ]
    );
} catch (PDOException $e) {
    error_log("PDO connect failed: " . $e->getMessage());
    $pdo = null;
}

/* =======================
   INPUT SANITIZATION
   ======================= */
if (!function_exists('cleanInput')) {
    function cleanInput($data) {
        if (empty($data)) return '';
        $data = trim($data);
        $data = stripslashes($data);
        $data = htmlspecialchars($data, ENT_QUOTES, 'UTF-8');
        return $data;
    }
}

if (!function_exists('sanitize')) {
    function sanitize($input, $type = 'string') {
        if (empty($input)) return '';
        switch ($type) {
            case 'int': return (int)$input;
            case 'float': return (float)$input;
            case 'email': return filter_var(trim($input), FILTER_SANITIZE_EMAIL);
            case 'url': return filter_var(trim($input), FILTER_SANITIZE_URL);
            case 'string':
            default: return htmlspecialchars(trim($input), ENT_QUOTES, 'UTF-8');
        }
    }
}

/* =======================
   SHARED CONSTANTS
   ======================= */
define('BASE_PATH', '/picpose_admin');
define('FAVICON_URL', '/favicon.ico');

/* ---------- Firebase (NEW - REQUIRED) ---------- */
/**
 * 🔥 IMPORTANT:
 * Replace ONLY the value with your Firebase Project ID
 * Firebase Console → Project Settings → General → Project ID
 */
define('FIREBASE_PROJECT_ID', 'picpose-ai-prompt-book');

/* Safety check (prevents silent CLI crashes) */
if (!defined('FIREBASE_PROJECT_ID') || empty(FIREBASE_PROJECT_ID)) {
    die('FIREBASE_PROJECT_ID is not defined in config.php');
}

/* ---------- Base URL ---------- */
if (!defined('BASE_URL')) {
    $protocol = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') ? 'https' : 'http';
    $host = $_SERVER['HTTP_HOST'] ?? ($_SERVER['SERVER_NAME'] ?? 'localhost');
    define('BASE_URL', $protocol . '://' . $host . BASE_PATH);
}

/* =======================
   AUTH HELPERS
   ======================= */
function is_admin_logged_in() {
    return isset($_SESSION['admin']) && !empty($_SESSION['admin']);
}

function require_admin_login($redirect_to = 'login.php') {
    if (!is_admin_logged_in()) {
        header("Location: " . BASE_URL . "/" . $redirect_to);
        exit();
    }
}
?>
