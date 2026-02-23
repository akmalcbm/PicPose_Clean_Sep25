<?php
declare(strict_types=1);

/**
 * PicPose Ads Config API
 * Public read-only endpoint for Android ads configuration.
 */

header('Content-Type: application/json; charset=utf-8');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type, X-API-Key, X-Device-ID, X-App-Version, X-Platform');
header('Cache-Control: public, max-age=300, stale-while-revalidate=60');

if (($_SERVER['REQUEST_METHOD'] ?? '') === 'OPTIONS') {
    http_response_code(204);
    exit;
}

require_once dirname(__DIR__) . '/config.php';
require_once dirname(__DIR__) . '/app/helpers/ads_config_helper.php';

$apiVersion = '2.1.0';
$start = microtime(true);

$response = [
    'success' => false,
    'data' => null,
    'meta' => [
        'api_version' => $apiVersion,
        'generated_at' => gmdate('c')
    ],
    'error' => null
];

try {
    if (!isset($conn) || !($conn instanceof mysqli)) {
        throw new RuntimeException('Database connection unavailable', 500);
    }

    ensure_ads_config_schema($conn);

    if (defined('API_READ_KEY') && API_READ_KEY !== '' && API_READ_KEY !== 'your_api_key_here') {
        $apiKey = $_GET['api_key'] ?? ($_SERVER['HTTP_X_API_KEY'] ?? '');
        if ($apiKey === '' || !hash_equals(API_READ_KEY, $apiKey)) {
            throw new RuntimeException('Invalid API key', 401);
        }
    }

    $globalRow = [
        'ads_enabled' => 1,
        'environment' => 'test',
        'cmp_required' => 0,
        'default_frequency_per_hour' => 3,
        'config_version' => 1,
        'use_test_ads' => 1,
        'admob_app_id_test' => null,
        'admob_app_id_live' => null,
        'interstitial_cooldown_seconds' => 60,
        'interstitial_show_every_n_actions' => 3,
        'updated_at' => gmdate('Y-m-d H:i:s')
    ];

    $globalStmt = $conn->prepare('SELECT * FROM ads_global_settings WHERE id = 1 LIMIT 1');
    if ($globalStmt && $globalStmt->execute()) {
        $dbGlobal = $globalStmt->get_result()->fetch_assoc();
        if ($dbGlobal) {
            $globalRow = array_merge($globalRow, $dbGlobal);
        }
        $globalStmt->close();
    }

    $env = normalize_ads_env((string)($globalRow['environment'] ?? 'test'));
    $adsEnabled = ((int)($globalRow['ads_enabled'] ?? 0) === 1);
    $useTestAds = ((int)($globalRow['use_test_ads'] ?? 0) === 1);

    // Env-aware selected app id.
    $appIdTest = trim((string)($globalRow['admob_app_id_test'] ?? ''));
    $appIdLive = trim((string)($globalRow['admob_app_id_live'] ?? ''));
    $selectedAppId = ($env === 'live' && !$useTestAds) ? $appIdLive : $appIdTest;

    $placementsMap = [];
    $placementsList = [];

    $placementsResult = $conn->query('SELECT * FROM ads_placement_settings ORDER BY placement_key ASC');
    if ($placementsResult) {
        while ($row = $placementsResult->fetch_assoc()) {
            $key = (string)$row['placement_key'];
            $isPlacementEnabled = ((int)$row['enabled'] === 1);
            $testUnit = trim((string)($row['ad_unit_id_test'] ?? ''));
            $liveUnit = trim((string)($row['ad_unit_id_live'] ?? ''));

            $selectedUnit = '';
            if ($env === 'live' && !$useTestAds) {
                $selectedUnit = $liveUnit !== '' ? $liveUnit : $testUnit;
            } else {
                $selectedUnit = $testUnit !== '' ? $testUnit : $liveUnit;
            }

            $placementsMap[$key] = [
                'enabled' => $isPlacementEnabled,
                'ad_type' => (string)$row['ad_type'],
                'ad_unit_id' => $selectedUnit,
                'ad_unit_id_test' => $testUnit,
                'ad_unit_id_live' => $liveUnit,
                'notes' => $row['notes'],
                'updated_at' => $row['updated_at'] ?? null
            ];

            $units = [];
            if ($testUnit !== '') {
                $units[] = [
                    'network' => 'admob',
                    'network_name' => 'Google AdMob',
                    'ad_unit_id' => $testUnit,
                    'ad_unit_name' => $key . ' test',
                    'priority' => 1,
                    'is_test' => true,
                    'is_live' => false,
                    'sdk_required' => true
                ];
            }
            if ($liveUnit !== '') {
                $units[] = [
                    'network' => 'admob',
                    'network_name' => 'Google AdMob',
                    'ad_unit_id' => $liveUnit,
                    'ad_unit_name' => $key . ' live',
                    'priority' => $testUnit !== '' ? 2 : 1,
                    'is_test' => false,
                    'is_live' => true,
                    'sdk_required' => true
                ];
            }

            $placementsList[] = [
                'key' => $key,
                'ad_type' => (string)$row['ad_type'],
                'enabled' => $isPlacementEnabled,
                'refresh_seconds' => null,
                'frequency' => (int)($globalRow['default_frequency_per_hour'] ?? 3),
                'screen_hint' => null,
                'auto_disabled' => false,
                'units' => $units
            ];
        }
    }

    // Fallback test placements for safety in test mode.
    if (empty($placementsMap) && ($useTestAds || $env !== 'live')) {
        $fallback = [
            'home_native' => 'ca-app-pub-3940256099942544/2247696110',
            'detail_native' => 'ca-app-pub-3940256099942544/2247696110',
            'interstitial_home' => 'ca-app-pub-3940256099942544/1033173712',
            'interstitial_detail' => 'ca-app-pub-3940256099942544/1033173712',
            'rewarded' => 'ca-app-pub-3940256099942544/5224354917',
            'banner_home' => 'ca-app-pub-3940256099942544/6300978111'
        ];

        foreach ($fallback as $key => $unitId) {
            $adType = infer_ad_type_from_placement($key);
            $placementsMap[$key] = [
                'enabled' => true,
                'ad_type' => $adType,
                'ad_unit_id' => $unitId,
                'ad_unit_id_test' => $unitId,
                'ad_unit_id_live' => '',
                'notes' => 'Fallback test placement',
                'updated_at' => gmdate('Y-m-d H:i:s')
            ];

            $placementsList[] = [
                'key' => $key,
                'ad_type' => $adType,
                'enabled' => true,
                'refresh_seconds' => null,
                'frequency' => (int)($globalRow['default_frequency_per_hour'] ?? 3),
                'screen_hint' => null,
                'auto_disabled' => false,
                'units' => [[
                    'network' => 'admob',
                    'network_name' => 'Google AdMob',
                    'ad_unit_id' => $unitId,
                    'ad_unit_name' => $key . ' test',
                    'priority' => 1,
                    'is_test' => true,
                    'is_live' => false,
                    'sdk_required' => true
                ]]
            ];
        }
    }

    $configVersion = (int)($globalRow['config_version'] ?? 1);
    $updatedAt = (string)($globalRow['updated_at'] ?? gmdate('Y-m-d H:i:s'));

    $data = [
        // New contract
        'ads_enabled' => $adsEnabled,
        'env' => $env,
        'use_test_ads' => $useTestAds,
        'admob_app_id' => $selectedAppId,
        'admob_app_id_test' => $appIdTest,
        'admob_app_id_live' => $appIdLive,
        'placements' => (object)$placementsMap,
        'interstitial_cooldown_seconds' => (int)($globalRow['interstitial_cooldown_seconds'] ?? 60),
        'interstitial_show_every_n_actions' => (int)($globalRow['interstitial_show_every_n_actions'] ?? 3),

        // Compatibility fields for older clients
        'config_version' => $configVersion,
        'global' => [
            'ads_enabled' => $adsEnabled,
            'environment' => $env === 'live' ? 'production' : 'test',
            'cmp_required' => ((int)($globalRow['cmp_required'] ?? 0) === 1),
            'default_frequency_per_hour' => (int)($globalRow['default_frequency_per_hour'] ?? 3),
            'use_test_ads' => $useTestAds,
            'config_updated_at' => $updatedAt,
            'admob_app_id' => $selectedAppId,
            'admob_app_id_test' => $appIdTest,
            'admob_app_id_live' => $appIdLive,
            'interstitial_cooldown_seconds' => (int)($globalRow['interstitial_cooldown_seconds'] ?? 60),
            'interstitial_show_every_n_actions' => (int)($globalRow['interstitial_show_every_n_actions'] ?? 3)
        ],
        'placements_list' => $placementsList
    ];

    // Preserve historical list key if client still expects data.placements as array.
    if (isset($_GET['legacy']) && $_GET['legacy'] === '1') {
        $data['placements'] = $placementsList;
    }

    $etag = '"' . sha1(json_encode($data)) . '"';
    header('ETag: ' . $etag);
    header('Last-Modified: ' . gmdate('D, d M Y H:i:s', strtotime($updatedAt)) . ' GMT');
    header('X-API-Version: ' . $apiVersion);

    if (isset($_SERVER['HTTP_IF_NONE_MATCH']) && trim((string)$_SERVER['HTTP_IF_NONE_MATCH']) === $etag) {
        http_response_code(304);
        exit;
    }

    $response['success'] = true;
    $response['data'] = $data;
    $response['meta']['generated_at'] = gmdate('c');
    $response['meta']['response_ms'] = (int)round((microtime(true) - $start) * 1000);
    $response['meta']['placements_count'] = count($placementsMap);

    echo json_encode($response, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE);

} catch (Throwable $e) {
    $code = (int)$e->getCode();
    if ($code < 400 || $code > 599) {
        $code = 500;
    }
    http_response_code($code);

    $response['success'] = false;
    $response['error'] = [
        'code' => $code,
        'message' => $e->getMessage()
    ];
    $response['meta']['response_ms'] = (int)round((microtime(true) - $start) * 1000);

    echo json_encode($response, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE);
}
