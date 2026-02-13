<?php
/**
 * PicPose API — Ads Configuration Endpoint
 *
 * Provides centralized ads configuration for mobile apps
 * Location: /api/ads_config.php
 *
 * @version 2.0.0
 */

declare(strict_types=1);

error_log("ads_config.php __DIR__=" . __DIR__);

/* ---------------- CONFIGURATION ---------------- */
define('API_VERSION', '2.0.0');
define('CACHE_DURATION', 300); // 5 minutes cache
define('DEFAULT_CONFIG_VERSION', 1);

/* ---------------- HEADERS ---------------- */
header('Content-Type: application/json; charset=utf-8');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type, X-API-Key, X-Device-ID, X-App-Version, X-Platform');
header('Cache-Control: public, max-age=' . CACHE_DURATION);
header('X-API-Version: ' . API_VERSION);

// Handle preflight requests
if (($_SERVER['REQUEST_METHOD'] ?? '') === 'OPTIONS') {
    http_response_code(204);
    exit;
}

/* ---------------- INITIALIZATION ---------------- */
$start_time = microtime(true);
$response = [
    'success' => false,
    'data' => null,
    'meta' => [
        'api_version' => API_VERSION,
        'generated_at' => gmdate('c'),
        'timestamp' => time(),
        'response_time' => 0
    ],
    'error' => null
];

ini_set('display_errors', '1');
ini_set('display_startup_errors', '1');
error_reporting(E_ALL);

try {
    /* ---------------- LOAD CONFIG ---------------- */
    $root = dirname(__DIR__);
    $config_path = $root . '/config.php';
    error_log("ads_config.php config_path=" . $config_path);

    if (!file_exists($config_path)) {
        throw new RuntimeException('Configuration file not found');
    }

    require_once $config_path;

    // Verify database connection
    if (!isset($conn) || !($conn instanceof mysqli)) {
        throw new RuntimeException('Database connection not established');
    }

    /**
     * Collation safety:
     * Tables are utf8mb4_unicode_ci but server default collation is utf8mb4_uca1400_ai_ci.
     * If session/connection collation differs, MySQL can throw "Illegal mix of collations" (1267)
     * during string comparisons (column = ?, ? = 'literal', CASE, etc).
     *
     * Enforce utf8mb4 + utf8mb4_unicode_ci at session level for this endpoint.
     */
    $conn->set_charset('utf8mb4');
    $conn->query("SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci");
    $conn->query("SET SESSION collation_connection = 'utf8mb4_unicode_ci'");

    /* ---------------- API AUTHENTICATION ---------------- */
    $api_key = $_GET['api_key'] ?? ($_SERVER['HTTP_X_API_KEY'] ?? '');
    $device_id = $_SERVER['HTTP_X_DEVICE_ID'] ?? '';
    $app_version = $_SERVER['HTTP_X_APP_VERSION'] ?? 'unknown';
    $platform = $_SERVER['HTTP_X_PLATFORM'] ?? 'unknown';

    // Log request
    logApiRequest($conn, $device_id, $app_version, $platform);

    // Check API key if configured
    if (defined('API_READ_KEY') && API_READ_KEY !== '' && API_READ_KEY !== 'your_api_key_here') {
        if ($api_key === '' || !hash_equals(API_READ_KEY, $api_key)) {
            throw new Exception('Invalid or missing API key', 401);
        }
    }

    /* ---------------- VALIDATE REQUEST ---------------- */
    $requested_config_version = (int)($_GET['config_version'] ?? 0);
    $client_app_version = $_GET['app_version'] ?? $app_version;
    $country_code = strtoupper(substr($_GET['country'] ?? ($_SERVER['HTTP_CF_IPCOUNTRY'] ?? ''), 0, 2));

    /* ---------------- FETCH GLOBAL SETTINGS ---------------- */
    $global_stmt = $conn->prepare("
        SELECT
            ads_enabled,
            environment,
            cmp_required,
            default_frequency_per_hour,
            config_version,
            updated_at
        FROM ads_global_settings
        WHERE id = 1
        LIMIT 1
    ");

    if (!$global_stmt->execute()) {
        throw new Exception('Failed to fetch global settings', 500);
    }

    $global_result = $global_stmt->get_result();
    $global = $global_result->fetch_assoc();
    $global_stmt->close();

    // Initialize defaults if no settings exist
    if (!$global) {
        $global = [
            'ads_enabled' => 1,
            'environment' => 'test',
            'cmp_required' => 0,
            'default_frequency_per_hour' => 3,
            'config_version' => DEFAULT_CONFIG_VERSION,
            'updated_at' => date('Y-m-d H:i:s')
        ];

        $init_stmt = $conn->prepare("
            INSERT INTO ads_global_settings
                (ads_enabled, environment, cmp_required, default_frequency_per_hour, config_version)
            VALUES (1, 'test', 0, 3, ?)
        ");
        $init_stmt->bind_param('i', $global['config_version']);
        $init_stmt->execute();
        $init_stmt->close();
    }

    /* ---------------- CHECK IF ADS ARE ENABLED ---------------- */
    $ads_enabled = ((int)$global['ads_enabled'] === 1);
    $environment = (string)$global['environment'];
    $server_config_version = (int)$global['config_version'];

    // If client has same config version and no refresh requested
    if ($requested_config_version >= $server_config_version && !isset($_GET['force_refresh'])) {
        $response['success'] = true;
        $response['data'] = [
            'config_version' => $server_config_version,
            'config_unchanged' => true,
            'global' => [
                'ads_enabled' => $ads_enabled
            ]
        ];
        $response['meta']['note'] = 'Configuration unchanged';
        echo json_encode($response, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE);
        exit;
    }

    /* ---------------- IF ADS ARE DISABLED ---------------- */
    if (!$ads_enabled) {
        $response['success'] = true;
        $response['data'] = [
            'config_version' => $server_config_version,
            'global' => [
                'ads_enabled' => false,
                'environment' => $environment,
                'cmp_required' => (bool)$global['cmp_required'],
                'default_frequency_per_hour' => (int)$global['default_frequency_per_hour']
            ],
            'placements' => new stdClass()
        ];
        $response['meta']['note'] = 'Ads are globally disabled';
        echo json_encode($response, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE);
        exit;
    }

    /* ---------------- FETCH PLACEMENTS WITH UNITS ---------------- */
    $use_test_ads = ($environment !== 'production');

    /**
     * 1267 Fix:
     * Explicit COLLATE on string comparisons where column/param/literal collations can differ.
     */
    $placements_sql = "
        SELECT
            p.id,
            p.key_name,
            p.ad_type,
            p.screen_hint,
            p.refresh_seconds,
            COALESCE(p.frequency_override, ?) as frequency,
            p.enabled as placement_enabled,
            p.auto_disabled,
            u.id as unit_id,
            u.ad_unit_id,
            u.ad_unit_name,
            u.priority,
            u.is_test,
            u.is_live,
            u.enabled as unit_enabled,
            u.country_code,
            n.code as network_code,
            n.display_name as network_name,
            n.sdk_required
        FROM ad_placements p
        CROSS JOIN ads_global_settings g ON g.id = 1
        LEFT JOIN ad_network_units u ON u.placement_id = p.id
            AND u.enabled = 1
            AND (
                u.country_code IS NULL
                OR u.country_code COLLATE utf8mb4_unicode_ci = '' COLLATE utf8mb4_unicode_ci
                OR u.country_code COLLATE utf8mb4_unicode_ci = ? COLLATE utf8mb4_unicode_ci
            )
        LEFT JOIN ad_networks n ON n.id = u.network_id
            AND n.enabled = 1
        WHERE p.enabled = 1
        ORDER BY
            p.id,
            CASE
                WHEN (? COLLATE utf8mb4_unicode_ci) = ('production' COLLATE utf8mb4_unicode_ci) AND u.is_live = 1 THEN 0
                WHEN (? COLLATE utf8mb4_unicode_ci) <> ('production' COLLATE utf8mb4_unicode_ci) AND u.is_test = 1 THEN 1
                ELSE 2
            END,
            u.priority ASC,
            u.updated_at DESC
    ";

    $placements_stmt = $conn->prepare($placements_sql);
    $placements_stmt->bind_param(
        'isss',
        $global['default_frequency_per_hour'],
        $country_code,
        $environment,
        $environment
    );

    if (!$placements_stmt->execute()) {
        throw new Exception('Failed to fetch placements', 500);
    }

    $placements_result = $placements_stmt->get_result();
    $placements_stmt->close();

    /* ---------------- PROCESS PLACEMENTS ---------------- */
    $processed_placements = [];

    if ($placements_result && $placements_result->num_rows > 0) {
        while ($row = $placements_result->fetch_assoc()) {
            $key = $row['key_name'];

            if (!isset($processed_placements[$key])) {
                $processed_placements[$key] = [
                    'key' => $key,
                    'ad_type' => $row['ad_type'],
                    'enabled' => (bool)$row['placement_enabled'],
                    'refresh_seconds' => $row['refresh_seconds'] !== null ? (int)$row['refresh_seconds'] : null,
                    'frequency' => (int)$row['frequency'],
                    'screen_hint' => $row['screen_hint'],
                    'auto_disabled' => (bool)$row['auto_disabled'],
                    'units' => []
                ];
            }

            if ($row['unit_id'] && $row['network_code']) {
                $is_preferred = ($environment === 'production') ? ($row['is_live'] == 1) : ($row['is_test'] == 1);
                $should_include = ($environment !== 'production') || $is_preferred;

                if ($should_include) {
                    $unit = [
                        'id' => (int)$row['unit_id'],
                        'network' => $row['network_code'],
                        'network_name' => $row['network_name'],
                        'ad_unit_id' => $row['ad_unit_id'],
                        'ad_unit_name' => $row['ad_unit_name'],
                        'priority' => (int)$row['priority'],
                        'is_test' => (bool)$row['is_test'],
                        'is_live' => (bool)$row['is_live'],
                        'sdk_required' => (bool)$row['sdk_required']
                    ];

                    if (!empty($row['country_code'])) {
                        $unit['country_code'] = $row['country_code'];
                    }

                    $processed_placements[$key]['units'][] = $unit;
                }
            }
        }
    }

    // Sort units by priority within each placement
    foreach ($processed_placements as &$placement) {
        if (!empty($placement['units'])) {
            usort($placement['units'], function ($a, $b) {
                return $a['priority'] <=> $b['priority'];
            });
        }
    }
    unset($placement);

    /* ---------------- FALLBACK PLACEMENTS (TEST MODE) ---------------- */
    if (empty($processed_placements) && $use_test_ads) {
        $processed_placements = generateTestPlacements();
    }

    /* ---------------- ANALYTICS TRACKING ---------------- */
    if ($device_id !== '') {
        trackConfigRequest($conn, $device_id, $server_config_version, count($processed_placements));
    }

    /* ---------------- PREPARE RESPONSE ---------------- */
    $response_data = [
        'config_version' => $server_config_version,
        'global' => [
            'ads_enabled' => $ads_enabled,
            'environment' => $environment,
            'cmp_required' => (bool)$global['cmp_required'],
            'default_frequency_per_hour' => (int)$global['default_frequency_per_hour'],
            'use_test_ads' => $use_test_ads,
            'config_updated_at' => $global['updated_at']
        ],
        'placements' => array_values($processed_placements),
        'client' => [
            'app_version' => $client_app_version,
            'platform' => $platform,
            'country' => ($country_code !== '' ? $country_code : null)
        ]
    ];

    $response['success'] = true;
    $response['data'] = $response_data;

    header('ETag: "' . md5(json_encode($response_data)) . '"');
    header('Last-Modified: ' . gmdate('D, d M Y H:i:s', strtotime($global['updated_at'])) . ' GMT');

    $response_time = round((microtime(true) - $start_time) * 1000, 2);
    $response['meta']['response_time'] = $response_time;
    $response['meta']['placements_count'] = count($processed_placements);
    $response['meta']['environment'] = $environment;
    $response['meta']['cache_ttl'] = CACHE_DURATION;

    echo json_encode($response, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT);

} catch (Exception $e) {
    http_response_code($e->getCode() >= 400 ? $e->getCode() : 500);

    $response['success'] = false;
    $response['error'] = [
        'code' => $e->getCode() ?: 500,
        'message' => $e->getMessage(),
        'type' => get_class($e)
    ];

    $response['meta']['response_time'] = round((microtime(true) - $start_time) * 1000, 2);

    if (isset($conn)) {
        logApiError($conn, $e);
    }

    echo json_encode($response, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE);
}


/**
 * Log API request for analytics
 */
function logApiRequest($conn, $device_id, $app_version, $platform) {
    try {
        $stmt = $conn->prepare("
            INSERT INTO api_request_logs 
                (endpoint, device_id, app_version, platform, ip_address, user_agent, created_at)
            VALUES (?, ?, ?, ?, ?, ?, NOW())
        ");
        
        $endpoint = 'ads_config';
        $ip_address = $_SERVER['HTTP_CF_CONNECTING_IP'] ?? $_SERVER['HTTP_X_FORWARDED_FOR'] ?? $_SERVER['REMOTE_ADDR'] ?? '';
        $user_agent = $_SERVER['HTTP_USER_AGENT'] ?? '';
        
        $stmt->bind_param(
            'ssssss',
            $endpoint,
            $device_id,
            $app_version,
            $platform,
            $ip_address,
            $user_agent
        );
        
        $stmt->execute();
        $stmt->close();
    } catch (Exception $e) {
        // Silently fail logging - don't break the API
        error_log("API Request Log Error: " . $e->getMessage());
    }
}

/**
 * Track config request for analytics
 */
function trackConfigRequest($conn, $device_id, $config_version, $placements_count) {
    try {
        $stmt = $conn->prepare("
            INSERT INTO config_request_logs 
                (device_id, config_version, placements_count, created_at)
            VALUES (?, ?, ?, NOW())
        ");
        
        $stmt->bind_param(
            'sii',
            $device_id,
            $config_version,
            $placements_count
        );
        
        $stmt->execute();
        $stmt->close();
    } catch (Exception $e) {
        // Silently fail analytics - don't break the API
        error_log("Config Request Log Error: " . $e->getMessage());
    }
}

/**
 * Log API errors
 */
function logApiError($conn, $exception) {
    try {
        $stmt = $conn->prepare("
            INSERT INTO api_error_logs 
                (endpoint, error_message, error_code, error_type, stack_trace, created_at)
            VALUES (?, ?, ?, ?, ?, NOW())
        ");
        
        $endpoint = 'ads_config';
        $error_message = $exception->getMessage();
        $error_code = $exception->getCode();
        $error_type = get_class($exception);
        $stack_trace = $exception->getTraceAsString();
        
        $stmt->bind_param(
            'ssiss',
            $endpoint,
            $error_message,
            $error_code,
            $error_type,
            $stack_trace
        );
        
        $stmt->execute();
        $stmt->close();
    } catch (Exception $e) {
        error_log("API Error Log Error: " . $e->getMessage());
    }
}

/**
 * Generate test placements for fallback
 */
function generateTestPlacements() {
    return [
        'home_banner' => [
            'key' => 'home_banner',
            'ad_type' => 'banner',
            'enabled' => true,
            'refresh_seconds' => 60,
            'frequency' => 3,
            'screen_hint' => 'Home Screen',
            'auto_disabled' => false,
            'units' => [[
                'id' => 9991,
                'network' => 'admob',
                'network_name' => 'Google AdMob',
                'ad_unit_id' => 'ca-app-pub-3940256099942544/6300978111',
                'ad_unit_name' => 'Test Home Banner',
                'priority' => 1,
                'is_test' => true,
                'is_live' => false,
                'sdk_required' => true
            ]]
        ],
        'home_interstitial' => [
            'key' => 'home_interstitial',
            'ad_type' => 'interstitial',
            'enabled' => true,
            'refresh_seconds' => 300,
            'frequency' => 2,
            'screen_hint' => 'Home Screen',
            'auto_disabled' => false,
            'units' => [[
                'id' => 9992,
                'network' => 'admob',
                'network_name' => 'Google AdMob',
                'ad_unit_id' => 'ca-app-pub-3940256099942544/1033173712',
                'ad_unit_name' => 'Test Home Interstitial',
                'priority' => 1,
                'is_test' => true,
                'is_live' => false,
                'sdk_required' => true
            ]]
        ],
        'detail_interstitial' => [
            'key' => 'detail_interstitial',
            'ad_type' => 'interstitial',
            'enabled' => true,
            'refresh_seconds' => 600,
            'frequency' => 1,
            'screen_hint' => 'Post Detail Screen',
            'auto_disabled' => false,
            'units' => [[
                'id' => 9993,
                'network' => 'admob',
                'network_name' => 'Google AdMob',
                'ad_unit_id' => 'ca-app-pub-3940256099942544/1033173712',
                'ad_unit_name' => 'Test Detail Interstitial',
                'priority' => 1,
                'is_test' => true,
                'is_live' => false,
                'sdk_required' => true
            ]]
        ],
        'native_ad' => [
            'key' => 'native_ad',
            'ad_type' => 'native',
            'enabled' => true,
            'refresh_seconds' => null,
            'frequency' => 3,
            'screen_hint' => 'Various Screens',
            'auto_disabled' => false,
            'units' => [[
                'id' => 9994,
                'network' => 'admob',
                'network_name' => 'Google AdMob',
                'ad_unit_id' => 'ca-app-pub-3940256099942544/2247696110',
                'ad_unit_name' => 'Test Native Ad',
                'priority' => 1,
                'is_test' => true,
                'is_live' => false,
                'sdk_required' => true
            ]]
        ],
        'rewarded_ad' => [
            'key' => 'rewarded_ad',
            'ad_type' => 'rewarded',
            'enabled' => true,
            'refresh_seconds' => null,
            'frequency' => 1,
            'screen_hint' => 'Reward Screen',
            'auto_disabled' => false,
            'units' => [[
                'id' => 9995,
                'network' => 'admob',
                'network_name' => 'Google AdMob',
                'ad_unit_id' => 'ca-app-pub-3940256099942544/5224354917',
                'ad_unit_name' => 'Test Rewarded Ad',
                'priority' => 1,
                'is_test' => true,
                'is_live' => false,
                'sdk_required' => true
            ]]
        ]
    ];
}

/**
 * Create required logging tables if they don't exist
 */
function ensureLoggingTables($conn) {
    $tables = [
        "CREATE TABLE IF NOT EXISTS api_request_logs (
            id INT AUTO_INCREMENT PRIMARY KEY,
            endpoint VARCHAR(50) NOT NULL,
            device_id VARCHAR(100),
            app_version VARCHAR(20),
            platform VARCHAR(20),
            ip_address VARCHAR(45),
            user_agent TEXT,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            INDEX idx_endpoint (endpoint),
            INDEX idx_device (device_id),
            INDEX idx_created (created_at)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
        
        "CREATE TABLE IF NOT EXISTS config_request_logs (
            id INT AUTO_INCREMENT PRIMARY KEY,
            device_id VARCHAR(100),
            config_version INT NOT NULL,
            placements_count INT DEFAULT 0,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            INDEX idx_device (device_id),
            INDEX idx_version (config_version),
            INDEX idx_created (created_at)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
        
        "CREATE TABLE IF NOT EXISTS api_error_logs (
            id INT AUTO_INCREMENT PRIMARY KEY,
            endpoint VARCHAR(50) NOT NULL,
            error_message TEXT NOT NULL,
            error_code INT,
            error_type VARCHAR(100),
            stack_trace TEXT,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            INDEX idx_endpoint (endpoint),
            INDEX idx_error_code (error_code),
            INDEX idx_created (created_at)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
    ];
    
    foreach ($tables as $sql) {
        $conn->query($sql);
    }
}

// Ensure logging tables exist (run once)
if (isset($conn)) {
    ensureLoggingTables($conn);
}