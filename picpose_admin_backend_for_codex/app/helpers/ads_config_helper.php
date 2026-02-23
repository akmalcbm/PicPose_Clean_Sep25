<?php

if (!function_exists('normalize_ads_env')) {
    function normalize_ads_env(string $env): string {
        $value = strtolower(trim($env));
        if (in_array($value, ['live', 'production', 'prod'], true)) {
            return 'live';
        }
        return 'test';
    }
}

if (!function_exists('is_valid_admob_app_id')) {
    function is_valid_admob_app_id(string $value): bool {
        return (bool)preg_match('/^ca-app-pub-[0-9]{16}~[0-9]{10}$/', trim($value));
    }
}

if (!function_exists('is_valid_admob_unit_id')) {
    function is_valid_admob_unit_id(string $value): bool {
        return (bool)preg_match('/^ca-app-pub-[0-9]{16}\/[0-9]{10}$/', trim($value));
    }
}

if (!function_exists('infer_ad_type_from_placement')) {
    function infer_ad_type_from_placement(string $placementKey): string {
        $key = strtolower(trim($placementKey));
        if (strpos($key, 'reward') !== false) {
            return 'rewarded';
        }
        if (strpos($key, 'interstitial') !== false) {
            return 'interstitial';
        }
        if (strpos($key, 'native') !== false) {
            return 'native';
        }
        return 'banner';
    }
}

if (!function_exists('ensure_ads_config_schema')) {
    function ensure_ads_config_schema(mysqli $conn): void {
        $queries = [
            "ALTER TABLE ads_global_settings MODIFY COLUMN environment VARCHAR(16) NOT NULL DEFAULT 'test'",
            "ALTER TABLE ads_global_settings ADD COLUMN IF NOT EXISTS use_test_ads TINYINT(1) NOT NULL DEFAULT 1 AFTER environment",
            "ALTER TABLE ads_global_settings ADD COLUMN IF NOT EXISTS admob_app_id_test VARCHAR(64) DEFAULT NULL AFTER use_test_ads",
            "ALTER TABLE ads_global_settings ADD COLUMN IF NOT EXISTS admob_app_id_live VARCHAR(64) DEFAULT NULL AFTER admob_app_id_test",
            "ALTER TABLE ads_global_settings ADD COLUMN IF NOT EXISTS interstitial_cooldown_seconds INT NOT NULL DEFAULT 60 AFTER default_frequency_per_hour",
            "ALTER TABLE ads_global_settings ADD COLUMN IF NOT EXISTS interstitial_show_every_n_actions INT NOT NULL DEFAULT 3 AFTER interstitial_cooldown_seconds",
            "CREATE TABLE IF NOT EXISTS ads_placement_settings (\n                id INT NOT NULL AUTO_INCREMENT,\n                placement_key VARCHAR(100) NOT NULL,\n                ad_type ENUM('banner','native','interstitial','rewarded') NOT NULL,\n                enabled TINYINT(1) NOT NULL DEFAULT 1,\n                ad_unit_id_test VARCHAR(64) DEFAULT NULL,\n                ad_unit_id_live VARCHAR(64) DEFAULT NULL,\n                notes VARCHAR(500) DEFAULT NULL,\n                created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,\n                updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,\n                PRIMARY KEY (id),\n                UNIQUE KEY uq_ads_placement_settings_key (placement_key),\n                KEY idx_ads_placement_settings_type (ad_type),\n                KEY idx_ads_placement_settings_enabled (enabled)\n            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
            "INSERT INTO ads_global_settings (id, ads_enabled, environment, cmp_required, default_frequency_per_hour, use_test_ads, interstitial_cooldown_seconds, interstitial_show_every_n_actions, config_version)\n             SELECT 1, 1, 'test', 0, 3, 1, 60, 3, 1\n             WHERE NOT EXISTS (SELECT 1 FROM ads_global_settings WHERE id = 1)",
            "UPDATE ads_global_settings\n             SET environment = CASE\n                 WHEN LOWER(COALESCE(environment, '')) IN ('production','prod','live') THEN 'live'\n                 WHEN LOWER(COALESCE(environment, '')) IN ('development','dev','staging','stage','test') THEN 'test'\n                 ELSE 'test'\n             END"
        ];

        foreach ($queries as $sql) {
            $conn->query($sql);
        }

        // Seed placement settings from legacy data if available.
        $seedSql = "
            INSERT INTO ads_placement_settings (placement_key, ad_type, enabled, ad_unit_id_test, ad_unit_id_live, notes)
            SELECT
                p.key_name,
                p.ad_type,
                p.enabled,
                MAX(CASE WHEN u.is_test = 1 THEN u.ad_unit_id END) AS ad_unit_id_test,
                MAX(CASE WHEN u.is_live = 1 THEN u.ad_unit_id END) AS ad_unit_id_live,
                'Migrated from ad_placements/ad_network_units'
            FROM ad_placements p
            LEFT JOIN ad_network_units u ON u.placement_id = p.id AND u.enabled = 1
            GROUP BY p.id, p.key_name, p.ad_type, p.enabled
            ON DUPLICATE KEY UPDATE
                ad_type = VALUES(ad_type),
                enabled = VALUES(enabled),
                ad_unit_id_test = COALESCE(ads_placement_settings.ad_unit_id_test, VALUES(ad_unit_id_test)),
                ad_unit_id_live = COALESCE(ads_placement_settings.ad_unit_id_live, VALUES(ad_unit_id_live)),
                updated_at = CURRENT_TIMESTAMP
        ";
        $conn->query($seedSql);
    }
}
