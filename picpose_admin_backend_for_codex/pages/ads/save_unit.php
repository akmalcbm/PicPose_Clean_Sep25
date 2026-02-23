<?php
session_start();
require '../../config.php';
require_once '../../app/helpers/ads_config_helper.php';

$conn->query("SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci");

if (!isset($_SESSION['admin']) || empty($_SESSION['admin'])) {
    header("Location: " . BASE_URL . "/login.php");
    exit();
}

if (!isset($_POST['csrf_token']) || $_POST['csrf_token'] !== ($_SESSION['csrf_token'] ?? '')) {
    $_SESSION['error'] = 'CSRF token validation failed';
    header('Location: units.php');
    exit();
}

ensure_ads_config_schema($conn);

$id = (int)($_POST['id'] ?? 0);
$placementId = (int)($_POST['placement_id'] ?? 0);
$networkId = (int)($_POST['network_id'] ?? 0);
$adUnitName = trim((string)($_POST['ad_unit_name'] ?? ''));
$adUnitId = trim((string)($_POST['ad_unit_id'] ?? ''));
$priority = max(1, min(10, (int)($_POST['priority'] ?? 1)));
$countryCode = strtoupper(substr(trim((string)($_POST['country_code'] ?? '')), 0, 2));
$notes = trim((string)($_POST['notes'] ?? ''));
$enabled = isset($_POST['enabled']) ? 1 : 0;
$isTest = isset($_POST['is_test']) ? 1 : 0;
$isLive = isset($_POST['is_live']) ? 1 : 0;

if ($placementId <= 0 || $networkId <= 0 || $adUnitId === '') {
    $_SESSION['error'] = 'Placement, network, and ad unit id are required';
    header('Location: units.php');
    exit();
}

if ($countryCode === '') {
    $countryCode = null;
}

try {
    $networkCodeStmt = $conn->prepare('SELECT code FROM ad_networks WHERE id = ? LIMIT 1');
    $networkCodeStmt->bind_param('i', $networkId);
    $networkCodeStmt->execute();
    $networkRow = $networkCodeStmt->get_result()->fetch_assoc();
    $networkCodeStmt->close();

    if (!$networkRow) {
        throw new RuntimeException('Selected network does not exist');
    }

    if (($networkRow['code'] ?? '') === 'admob' && !is_valid_admob_unit_id($adUnitId)) {
        throw new RuntimeException('Invalid AdMob ad unit ID format. Expected ca-app-pub-XXXXXXXXXXXXXXXX/YYYYYYYYYY');
    }

    $conn->begin_transaction();

    if ($id > 0) {
        $stmt = $conn->prepare("\n            UPDATE ad_network_units\n            SET placement_id = ?,\n                network_id = ?,\n                ad_unit_id = ?,\n                ad_unit_name = NULLIF(?, ''),\n                priority = ?,\n                is_test = ?,\n                is_live = ?,\n                notes = NULLIF(?, ''),\n                enabled = ?,\n                country_code = ?,\n                updated_at = NOW()\n            WHERE id = ?\n        ");
        if (!$stmt) {
            throw new RuntimeException('Failed to prepare update ad unit');
        }
        $stmt->bind_param(
            'iissiiisisi',
            $placementId,
            $networkId,
            $adUnitId,
            $adUnitName,
            $priority,
            $isTest,
            $isLive,
            $notes,
            $enabled,
            $countryCode,
            $id
        );
        if (!$stmt->execute()) {
            throw new RuntimeException('Failed to update ad unit: ' . $stmt->error);
        }
        $stmt->close();

        $_SESSION['success'] = 'Ad unit updated successfully.';
    } else {
        $stmt = $conn->prepare("\n            INSERT INTO ad_network_units\n                (placement_id, network_id, ad_unit_id, ad_unit_name, priority, is_test, is_live, notes, enabled, country_code, created_at, updated_at)\n            VALUES (?, ?, ?, NULLIF(?, ''), ?, ?, ?, NULLIF(?, ''), ?, ?, NOW(), NOW())\n        ");
        if (!$stmt) {
            throw new RuntimeException('Failed to prepare insert ad unit');
        }
        $stmt->bind_param(
            'iissiiisis',
            $placementId,
            $networkId,
            $adUnitId,
            $adUnitName,
            $priority,
            $isTest,
            $isLive,
            $notes,
            $enabled,
            $countryCode
        );
        if (!$stmt->execute()) {
            throw new RuntimeException('Failed to create ad unit: ' . $stmt->error);
        }
        $stmt->close();

        $_SESSION['success'] = 'Ad unit created successfully.';
    }

    $conn->query("UPDATE ads_global_settings SET config_version = config_version + 1, updated_at = NOW() WHERE id = 1");

    $adminId = (int)($_SESSION['admin_id'] ?? 0);
    $details = $id > 0 ? 'Updated ad unit: ' . $adUnitId : 'Created ad unit: ' . $adUnitId;
    $ipAddress = (string)($_SERVER['REMOTE_ADDR'] ?? '');
    $userAgent = (string)($_SERVER['HTTP_USER_AGENT'] ?? '');
    $logStmt = $conn->prepare("INSERT INTO admin_logs (admin_id, action, details, ip_address, user_agent) VALUES (?, 'ad_unit_save', ?, ?, ?)");
    if ($logStmt) {
        $logStmt->bind_param('isss', $adminId, $details, $ipAddress, $userAgent);
        $logStmt->execute();
        $logStmt->close();
    }

    $conn->commit();
} catch (Throwable $e) {
    $conn->rollback();
    error_log('save_unit error: ' . $e->getMessage());
    $_SESSION['error'] = $e->getMessage();
}

header('Location: units.php');
exit();
