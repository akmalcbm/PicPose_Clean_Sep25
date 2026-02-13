<?php
declare(strict_types=1);

header('Content-Type: application/json');

require_once __DIR__ . '/../config.php';

header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: POST, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type, Authorization, X-API-Key');

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(200);
    exit();
}

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    http_response_code(405);
    echo json_encode(['success' => false, 'error' => 'Method not allowed']);
    exit();
}

$input = json_decode((string)file_get_contents('php://input'), true);
if (!is_array($input)) {
    http_response_code(400);
    echo json_encode(['success' => false, 'error' => 'Invalid JSON body']);
    exit();
}

$token = trim((string)($input['token'] ?? $input['device_token'] ?? ''));
if ($token === '' || strlen($token) < 100) {
    http_response_code(400);
    echo json_encode(['success' => false, 'error' => 'Invalid FCM token']);
    exit();
}

$userId = isset($input['user_id']) && $input['user_id'] !== '' ? (int)$input['user_id'] : null;
$platform = trim((string)($input['platform'] ?? $input['device_type'] ?? 'android'));
$appVersion = trim((string)($input['app_version'] ?? ''));
$deviceModel = trim((string)($input['device_model'] ?? ''));
$osVersion = trim((string)($input['os_version'] ?? ''));
$language = trim((string)($input['language'] ?? ''));
$country = trim((string)($input['country'] ?? ''));
$timezone = trim((string)($input['timezone'] ?? ''));

$platform = $platform !== '' ? $platform : 'android';
$appVersion = $appVersion !== '' ? $appVersion : null;
$deviceModel = $deviceModel !== '' ? $deviceModel : null;
$osVersion = $osVersion !== '' ? $osVersion : null;
$language = $language !== '' ? $language : null;
$country = $country !== '' ? $country : null;
$timezone = $timezone !== '' ? $timezone : null;

try {
    $stmt = $conn->prepare(
        'INSERT INTO device_tokens
            (user_id, token, fcm_token, platform, device_type, device_model, os_version, app_version, language, country, timezone, last_seen_at, last_active, is_active, updated_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW(), 1, NOW())
         ON DUPLICATE KEY UPDATE
            user_id = VALUES(user_id),
            token = VALUES(token),
            fcm_token = VALUES(fcm_token),
            platform = VALUES(platform),
            device_type = VALUES(device_type),
            device_model = VALUES(device_model),
            os_version = VALUES(os_version),
            app_version = VALUES(app_version),
            language = VALUES(language),
            country = VALUES(country),
            timezone = VALUES(timezone),
            last_seen_at = NOW(),
            last_active = NOW(),
            is_active = 1,
            updated_at = NOW(),
            deactivation_reason = NULL,
            deactivated_at = NULL'
    );

    $stmt->bind_param(
        'issssssssss',
        $userId,
        $token,
        $token,
        $platform,
        $platform,
        $deviceModel,
        $osVersion,
        $appVersion,
        $language,
        $country,
        $timezone
    );

    if (!$stmt->execute()) {
        throw new RuntimeException($stmt->error);
    }

    $tokenId = (int)$stmt->insert_id;
    $stmt->close();

    echo json_encode([
        'success' => true,
        'message' => 'Device token registered',
        'token_id' => $tokenId > 0 ? $tokenId : null,
    ]);
} catch (Throwable $e) {
    http_response_code(500);
    echo json_encode([
        'success' => false,
        'error' => 'Server error: ' . $e->getMessage(),
    ]);
}
