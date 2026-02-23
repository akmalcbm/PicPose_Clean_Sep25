<?php
session_start();
require '../../config.php';
require_once '../../app/helpers/ads_config_helper.php';

if (!isset($_SESSION['admin']) || empty($_SESSION['admin'])) {
    header("Location: " . BASE_URL . "/login.php");
    exit();
}

if (!isset($_POST['csrf_token']) || $_POST['csrf_token'] !== ($_SESSION['csrf_token'] ?? '')) {
    $_SESSION['error'] = 'CSRF token validation failed';
    header('Location: ads_config.php');
    exit();
}

ensure_ads_config_schema($conn);

$action = $_POST['action'] ?? '';

try {
    $conn->begin_transaction();

    if ($action === 'save_global') {
        $adsEnabled = isset($_POST['ads_enabled']) ? 1 : 0;
        $environment = normalize_ads_env((string)($_POST['environment'] ?? 'test'));
        $useTestAds = isset($_POST['use_test_ads']) ? 1 : 0;
        $admobAppIdTest = trim((string)($_POST['admob_app_id_test'] ?? ''));
        $admobAppIdLive = trim((string)($_POST['admob_app_id_live'] ?? ''));
        $cooldown = max(0, min(86400, (int)($_POST['interstitial_cooldown_seconds'] ?? 60)));
        $showEvery = max(1, min(100, (int)($_POST['interstitial_show_every_n_actions'] ?? 3)));

        if ($admobAppIdTest !== '' && !is_valid_admob_app_id($admobAppIdTest)) {
            throw new RuntimeException('Invalid test AdMob App ID format. Expected ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY');
        }
        if ($admobAppIdLive !== '' && !is_valid_admob_app_id($admobAppIdLive)) {
            throw new RuntimeException('Invalid live AdMob App ID format. Expected ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY');
        }

        $stmt = $conn->prepare("\n            UPDATE ads_global_settings\n            SET ads_enabled = ?,\n                environment = ?,\n                use_test_ads = ?,\n                admob_app_id_test = NULLIF(?, ''),\n                admob_app_id_live = NULLIF(?, ''),\n                interstitial_cooldown_seconds = ?,\n                interstitial_show_every_n_actions = ?,\n                config_version = config_version + 1,\n                updated_at = NOW()\n            WHERE id = 1\n        ");
        if (!$stmt) {
            throw new RuntimeException('Failed to prepare global settings update');
        }
        $stmt->bind_param(
            'isissii',
            $adsEnabled,
            $environment,
            $useTestAds,
            $admobAppIdTest,
            $admobAppIdLive,
            $cooldown,
            $showEvery
        );
        if (!$stmt->execute()) {
            throw new RuntimeException('Failed to save global ads settings: ' . $stmt->error);
        }
        $stmt->close();

        $_SESSION['success'] = 'Global ads configuration saved.';

    } elseif ($action === 'add_placement' || $action === 'update_placement') {
        $id = (int)($_POST['id'] ?? 0);
        $placementKey = strtolower(trim((string)($_POST['placement_key'] ?? '')));
        $adType = strtolower(trim((string)($_POST['ad_type'] ?? '')));
        $enabled = isset($_POST['enabled']) ? 1 : 0;
        $testUnit = trim((string)($_POST['ad_unit_id_test'] ?? ''));
        $liveUnit = trim((string)($_POST['ad_unit_id_live'] ?? ''));
        $notes = trim((string)($_POST['notes'] ?? ''));

        if (!preg_match('/^[a-z][a-z0-9_]{1,99}$/', $placementKey)) {
            throw new RuntimeException('Invalid placement key. Use lowercase letters, numbers, underscore.');
        }

        if (!in_array($adType, ['banner', 'native', 'interstitial', 'rewarded'], true)) {
            $adType = infer_ad_type_from_placement($placementKey);
        }

        if ($testUnit !== '' && !is_valid_admob_unit_id($testUnit)) {
            throw new RuntimeException('Invalid test ad unit ID format. Expected ca-app-pub-XXXXXXXXXXXXXXXX/YYYYYYYYYY');
        }
        if ($liveUnit !== '' && !is_valid_admob_unit_id($liveUnit)) {
            throw new RuntimeException('Invalid live ad unit ID format. Expected ca-app-pub-XXXXXXXXXXXXXXXX/YYYYYYYYYY');
        }

        if ($action === 'add_placement') {
            $stmt = $conn->prepare("\n                INSERT INTO ads_placement_settings\n                    (placement_key, ad_type, enabled, ad_unit_id_test, ad_unit_id_live, notes, created_at, updated_at)\n                VALUES (?, ?, ?, NULLIF(?, ''), NULLIF(?, ''), NULLIF(?, ''), NOW(), NOW())\n            ");
            if (!$stmt) {
                throw new RuntimeException('Failed to prepare insert placement');
            }
            $stmt->bind_param('ssisss', $placementKey, $adType, $enabled, $testUnit, $liveUnit, $notes);
            if (!$stmt->execute()) {
                throw new RuntimeException('Failed to add placement: ' . $stmt->error);
            }
            $stmt->close();
            $_SESSION['success'] = 'Placement added.';
        } else {
            if ($id <= 0) {
                throw new RuntimeException('Invalid placement id for update.');
            }
            $stmt = $conn->prepare("\n                UPDATE ads_placement_settings\n                SET placement_key = ?,\n                    ad_type = ?,\n                    enabled = ?,\n                    ad_unit_id_test = NULLIF(?, ''),\n                    ad_unit_id_live = NULLIF(?, ''),\n                    notes = NULLIF(?, ''),\n                    updated_at = NOW()\n                WHERE id = ?\n            ");
            if (!$stmt) {
                throw new RuntimeException('Failed to prepare update placement');
            }
            $stmt->bind_param('ssisssi', $placementKey, $adType, $enabled, $testUnit, $liveUnit, $notes, $id);
            if (!$stmt->execute()) {
                throw new RuntimeException('Failed to update placement: ' . $stmt->error);
            }
            $stmt->close();
            $_SESSION['success'] = 'Placement updated.';
        }

        $conn->query("UPDATE ads_global_settings SET config_version = config_version + 1, updated_at = NOW() WHERE id = 1");

    } elseif ($action === 'delete_placement') {
        $id = (int)($_POST['id'] ?? 0);
        if ($id <= 0) {
            throw new RuntimeException('Invalid placement id for delete.');
        }

        $stmt = $conn->prepare("DELETE FROM ads_placement_settings WHERE id = ?");
        if (!$stmt) {
            throw new RuntimeException('Failed to prepare delete placement');
        }
        $stmt->bind_param('i', $id);
        if (!$stmt->execute()) {
            throw new RuntimeException('Failed to delete placement: ' . $stmt->error);
        }
        $stmt->close();

        $conn->query("UPDATE ads_global_settings SET config_version = config_version + 1, updated_at = NOW() WHERE id = 1");
        $_SESSION['success'] = 'Placement deleted.';
    } else {
        throw new RuntimeException('Unsupported action.');
    }

    $adminId = (int)($_SESSION['admin_id'] ?? 0);
    $details = 'Ads config action: ' . $action;
    $ipAddress = (string)($_SERVER['REMOTE_ADDR'] ?? '');
    $userAgent = (string)($_SERVER['HTTP_USER_AGENT'] ?? '');
    $logStmt = $conn->prepare("INSERT INTO admin_logs (admin_id, action, details, ip_address, user_agent) VALUES (?, 'ads_config_update', ?, ?, ?)");
    if ($logStmt) {
        $logStmt->bind_param('isss', $adminId, $details, $ipAddress, $userAgent);
        $logStmt->execute();
        $logStmt->close();
    }

    $conn->commit();

} catch (Throwable $e) {
    $conn->rollback();
    error_log('save_ads_config error: ' . $e->getMessage());
    $_SESSION['error'] = $e->getMessage();
}

header('Location: ads_config.php');
exit();
