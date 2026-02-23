-- PicPose Ads Config V2
-- Backward-compatible migration for centralized ads configuration.

START TRANSACTION;

-- Expand global settings with explicit env/test/live controls and AdMob app IDs.
ALTER TABLE ads_global_settings
    MODIFY COLUMN environment VARCHAR(16) NOT NULL DEFAULT 'test';

ALTER TABLE ads_global_settings
    ADD COLUMN IF NOT EXISTS use_test_ads TINYINT(1) NOT NULL DEFAULT 1 AFTER environment,
    ADD COLUMN IF NOT EXISTS admob_app_id_test VARCHAR(64) DEFAULT NULL AFTER use_test_ads,
    ADD COLUMN IF NOT EXISTS admob_app_id_live VARCHAR(64) DEFAULT NULL AFTER admob_app_id_test,
    ADD COLUMN IF NOT EXISTS interstitial_cooldown_seconds INT NOT NULL DEFAULT 60 AFTER default_frequency_per_hour,
    ADD COLUMN IF NOT EXISTS interstitial_show_every_n_actions INT NOT NULL DEFAULT 3 AFTER interstitial_cooldown_seconds;

-- Normalize any invalid/legacy environment values.
UPDATE ads_global_settings
SET environment = CASE
    WHEN LOWER(COALESCE(environment, '')) IN ('production', 'prod', 'live') THEN 'live'
    WHEN LOWER(COALESCE(environment, '')) IN ('development', 'dev', 'staging', 'stage', 'test') THEN 'test'
    ELSE 'test'
END;

-- Placement-level configuration table for test/live IDs and per-placement enable toggle.
CREATE TABLE IF NOT EXISTS ads_placement_settings (
    id INT NOT NULL AUTO_INCREMENT,
    placement_key VARCHAR(100) NOT NULL,
    ad_type ENUM('banner', 'native', 'interstitial', 'rewarded') NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    ad_unit_id_test VARCHAR(64) DEFAULT NULL,
    ad_unit_id_live VARCHAR(64) DEFAULT NULL,
    notes VARCHAR(500) DEFAULT NULL,
    created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_ads_placement_settings_key (placement_key),
    KEY idx_ads_placement_settings_type (ad_type),
    KEY idx_ads_placement_settings_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Seed placement settings from existing legacy placement + units data if not present.
INSERT INTO ads_placement_settings (placement_key, ad_type, enabled, ad_unit_id_test, ad_unit_id_live, notes)
SELECT
    p.key_name AS placement_key,
    p.ad_type,
    p.enabled,
    MAX(CASE WHEN u.is_test = 1 THEN u.ad_unit_id END) AS ad_unit_id_test,
    MAX(CASE WHEN u.is_live = 1 THEN u.ad_unit_id END) AS ad_unit_id_live,
    'Migrated from ad_placements/ad_network_units' AS notes
FROM ad_placements p
LEFT JOIN ad_network_units u ON u.placement_id = p.id AND u.enabled = 1
GROUP BY p.id, p.key_name, p.ad_type, p.enabled
ON DUPLICATE KEY UPDATE
    ad_type = VALUES(ad_type),
    enabled = VALUES(enabled),
    ad_unit_id_test = COALESCE(ads_placement_settings.ad_unit_id_test, VALUES(ad_unit_id_test)),
    ad_unit_id_live = COALESCE(ads_placement_settings.ad_unit_id_live, VALUES(ad_unit_id_live)),
    updated_at = CURRENT_TIMESTAMP;

-- Ensure singleton global row exists.
INSERT INTO ads_global_settings (
    id,
    ads_enabled,
    environment,
    cmp_required,
    default_frequency_per_hour,
    use_test_ads,
    interstitial_cooldown_seconds,
    interstitial_show_every_n_actions,
    config_version
)
SELECT 1, 1, 'test', 0, 3, 1, 60, 3, 1
WHERE NOT EXISTS (SELECT 1 FROM ads_global_settings WHERE id = 1);

COMMIT;
