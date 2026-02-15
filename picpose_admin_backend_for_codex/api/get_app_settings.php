<?php
/**
 * /api/get_app_settings.php
 * Returns the latest app_settings as JSON.
 */
declare(strict_types=1);

// ---- Headers (CORS + JSON) ----
header('Content-Type: application/json; charset=utf-8');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type, X-API-Key');
header('Cache-Control: no-store');

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(204);
    exit;
}

// ---- Bootstrap DB ----
$root = dirname(__DIR__);
require_once $root . '/config.php';

// ---- Optional API key gate ----
// Define API_READ_KEY in config.php to enable this gate.
if (defined('API_READ_KEY') && API_READ_KEY !== '') {
    $provided = $_GET['api_key'] ?? $_SERVER['HTTP_X_API_KEY'] ?? '';
    if (!hash_equals(API_READ_KEY, (string)$provided)) {
        http_response_code(401);
        echo json_encode(['success' => false, 'error' => 'Unauthorized: invalid api_key.'], JSON_UNESCAPED_UNICODE);
        exit;
    }
}

// ---- Helper: Strip HTML to plain text ----
function html_to_text(?string $html): string {
    if ($html === null) return '';
    $decoded = html_entity_decode($html, ENT_QUOTES | ENT_HTML5, 'UTF-8');
    $stripped = strip_tags($decoded);
    $stripped = preg_replace('/\s+/u', ' ', $stripped);
    return trim((string)$stripped);
}

function normalize_support_email(?string $value): string {
    $text = (string)($value ?? '');
    return str_replace(
        ['support@picpose.iamakmal.in', 'support@picpose.com'],
        'picposeapp@gmail.com',
        $text
    );
}

try {
    if (!isset($conn) || !($conn instanceof mysqli)) {
        throw new RuntimeException('Database connection $conn not found.');
    }

    // ✅ FIXED QUERY - Remove Admob columns that don't exist in your table
    $sql = "SELECT 
                id,
                admin_name,
                app_name,
                tagline,
                description,
                google_play_url,
                privacy_policy,
                terms_conditions,
                support_email,
                support_phone,
                about,
                created_at,
                updated_at
            FROM app_settings
            ORDER BY id DESC
            LIMIT 1";

    $stmt = $conn->prepare($sql);
    if (!$stmt) {
        throw new RuntimeException('Failed to prepare query: ' . $conn->error);
    }

    if (!$stmt->execute()) {
        throw new RuntimeException('Failed to execute query: ' . $stmt->error);
    }

    $row = $stmt->get_result()?->fetch_assoc() ?? null;

    if (!$row) {
        echo json_encode([
            'success' => true,
            'data' => null,
            'meta' => [
                'message' => 'No settings found',
                'generated_at' => gmdate('c'),
            ],
        ], JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
        exit;
    }

    $privacyHtml = normalize_support_email($row['privacy_policy'] ?? '');
    $termsHtml = normalize_support_email($row['terms_conditions'] ?? '');
    $supportEmail = normalize_support_email($row['support_email'] ?? '');

    // ---- Build JSON payload ----
    $payload = [
        'id' => (int)$row['id'],
        'admin_name' => $row['admin_name'] ?? '',
        'app_name' => $row['app_name'] ?? '',
        'tagline' => $row['tagline'] ?? '',
        'description' => $row['description'] ?? '',
        'google_play_url' => $row['google_play_url'] ?? '',

        // ✅ REMOVE Admob section since columns don't exist
        // 'admob' => [
        //     'app_id' => $row['admob_app_id'] ?? '',
        //     'banner1_id' => $row['admob_banner1_id'] ?? '',
        //     'banner2_id' => $row['admob_banner2_id'] ?? '',
        //     'interstitial1_id' => $row['admob_interstitial1_id'] ?? '',
        //     'interstitial2_id' => $row['admob_interstitial2_id'] ?? '',
        //     'native1_id' => $row['admob_native1_id'] ?? '',
        //     'native2_id' => $row['admob_native2_id'] ?? '',
        //     'native3_id' => $row['admob_native3_id'] ?? '',
        //     'rewarded1_id' => $row['admob_rewarded1_id'] ?? '',
        // ],

        'contact' => [
            'email' => $supportEmail,
            'phone' => $row['support_phone'] ?? '',
        ],

        'policies' => [
            'privacy_policy_html' => $privacyHtml,
            'terms_conditions_html' => $termsHtml,
            'privacy_policy_text' => html_to_text($privacyHtml),
            'terms_conditions_text' => html_to_text($termsHtml),
        ],

        'about' => [
            'html' => $row['about'] ?? '',
            'text' => html_to_text($row['about'] ?? ''),
        ],

        'meta' => [
            'created_at' => $row['created_at'] ?? '',
            'updated_at' => $row['updated_at'] ?? '',
        ]
    ];

    echo json_encode([
        'success' => true,
        'data' => $payload,
        'meta' => ['generated_at' => gmdate('c')],
    ], JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);

} catch (Throwable $e) {
    http_response_code(500);
    echo json_encode([
        'success' => false,
        'error' => 'Server error: ' . $e->getMessage(),
    ], JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
}
