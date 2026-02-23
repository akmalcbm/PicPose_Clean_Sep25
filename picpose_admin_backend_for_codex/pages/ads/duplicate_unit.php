<?php
session_start();
require '../../config.php';

if (!isset($_SESSION['admin']) || empty($_SESSION['admin'])) {
    header("Location: " . BASE_URL . "/login.php");
    exit();
}

if (!isset($_POST['csrf_token']) || $_POST['csrf_token'] !== ($_SESSION['csrf_token'] ?? '')) {
    $_SESSION['error'] = 'CSRF token validation failed';
    header('Location: units.php');
    exit();
}

$id = (int)($_POST['id'] ?? 0);
if ($id <= 0) {
    $_SESSION['error'] = 'Invalid unit id';
    header('Location: units.php');
    exit();
}

try {
    $conn->begin_transaction();

    $fetchStmt = $conn->prepare('SELECT placement_id, network_id, ad_unit_id, ad_unit_name, priority, is_test, is_live, notes, enabled, country_code FROM ad_network_units WHERE id = ? LIMIT 1');
    $fetchStmt->bind_param('i', $id);
    $fetchStmt->execute();
    $row = $fetchStmt->get_result()->fetch_assoc();
    $fetchStmt->close();

    if (!$row) {
        throw new RuntimeException('Ad unit not found');
    }

    $name = trim((string)($row['ad_unit_name'] ?? ''));
    if ($name !== '') {
        $name .= ' (Copy)';
    }

    $insertStmt = $conn->prepare("\n        INSERT INTO ad_network_units\n            (placement_id, network_id, ad_unit_id, ad_unit_name, priority, is_test, is_live, notes, enabled, country_code, created_at, updated_at)\n        VALUES (?, ?, ?, NULLIF(?, ''), ?, ?, ?, ?, ?, ?, NOW(), NOW())\n    ");

    $adUnitId = (string)$row['ad_unit_id'];
    $priority = min(10, ((int)$row['priority']) + 1);
    $isTest = (int)$row['is_test'];
    $isLive = (int)$row['is_live'];
    $notes = (string)($row['notes'] ?? '');
    $enabled = 0;
    $countryCode = $row['country_code'] ? (string)$row['country_code'] : null;

    $insertStmt->bind_param(
        'iissiiisis',
        $row['placement_id'],
        $row['network_id'],
        $adUnitId,
        $name,
        $priority,
        $isTest,
        $isLive,
        $notes,
        $enabled,
        $countryCode
    );

    if (!$insertStmt->execute()) {
        throw new RuntimeException('Failed to duplicate ad unit: ' . $insertStmt->error);
    }
    $insertStmt->close();

    $conn->query("UPDATE ads_global_settings SET config_version = config_version + 1, updated_at = NOW() WHERE id = 1");
    $conn->commit();

    $_SESSION['success'] = 'Ad unit duplicated (new copy starts disabled).';
} catch (Throwable $e) {
    $conn->rollback();
    $_SESSION['error'] = $e->getMessage();
}

header('Location: units.php');
exit();
