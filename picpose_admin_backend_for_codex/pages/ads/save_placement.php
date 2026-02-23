<?php
session_start();
require '../../config.php';

function isAjaxRequest(): bool {
    $xrw = $_SERVER['HTTP_X_REQUESTED_WITH'] ?? '';
    if (strtolower($xrw) === 'xmlhttprequest') {
        return true;
    }
    $accept = $_SERVER['HTTP_ACCEPT'] ?? '';
    return stripos($accept, 'application/json') !== false;
}

function respondPlacement(bool $ok, string $message, int $code = 200, array $extra = []): void {
    if (isAjaxRequest()) {
        http_response_code($code);
        header('Content-Type: application/json; charset=utf-8');
        echo json_encode(array_merge([
            'success' => $ok,
            'message' => $message
        ], $extra), JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE);
        exit();
    }

    if ($ok) {
        $_SESSION['success'] = $message;
    } else {
        $_SESSION['error'] = $message;
    }
    header('Location: placements.php');
    exit();
}

if (!isset($_SESSION['admin']) || empty($_SESSION['admin'])) {
    error_log('save_placement unauthorized: missing admin session');
    header('Location: ' . BASE_URL . '/login.php');
    exit();
}

if (!isset($_POST['csrf_token']) || $_POST['csrf_token'] !== ($_SESSION['csrf_token'] ?? '')) {
    error_log('save_placement csrf_failed admin_id=' . (int)($_SESSION['admin_id'] ?? 0));
    respondPlacement(false, 'CSRF token validation failed', 403);
}

$id = (int)($_POST['id'] ?? 0);
$keyName = strtolower(trim(cleanInput($_POST['key_name'] ?? '')));
$adType = strtolower(trim(cleanInput($_POST['ad_type'] ?? '')));
$screenHint = trim(cleanInput($_POST['screen_hint'] ?? ''));
$enabled = isset($_POST['enabled']) ? 1 : 0;
$autoDisabled = isset($_POST['auto_disabled']) ? 1 : 0;
$refreshSeconds = isset($_POST['refresh_seconds']) && $_POST['refresh_seconds'] !== '' ? (int)$_POST['refresh_seconds'] : null;
$frequencyOverride = isset($_POST['frequency_override']) && $_POST['frequency_override'] !== '' ? (int)$_POST['frequency_override'] : null;

if ($keyName === '' || $adType === '') {
    respondPlacement(false, 'Placement key and ad type are required', 422);
}

if (!preg_match('/^[a-z][a-z0-9_]*$/', $keyName)) {
    respondPlacement(false, 'Placement key must be lowercase snake_case and start with a letter', 422);
}

$allowedTypes = ['banner', 'interstitial', 'native', 'rewarded'];
if (!in_array($adType, $allowedTypes, true)) {
    respondPlacement(false, 'Invalid ad type', 422);
}

if ($refreshSeconds !== null && ($refreshSeconds < 0 || $refreshSeconds > 3600)) {
    respondPlacement(false, 'Refresh seconds must be between 0 and 3600', 422);
}

if ($frequencyOverride !== null && ($frequencyOverride < 0 || $frequencyOverride > 20)) {
    respondPlacement(false, 'Frequency override must be between 0 and 20', 422);
}

// Optional validation when legacy UI sends network_id / ad_unit_id with placement payload.
$networkId = isset($_POST['network_id']) && $_POST['network_id'] !== '' ? (int)$_POST['network_id'] : null;
if ($networkId !== null && $networkId > 0) {
    $netStmt = $conn->prepare('SELECT id FROM ad_networks WHERE id = ? LIMIT 1');
    if ($netStmt) {
        $netStmt->bind_param('i', $networkId);
        $netStmt->execute();
        $exists = $netStmt->get_result()->num_rows > 0;
        $netStmt->close();
        if (!$exists) {
            respondPlacement(false, 'Selected network does not exist', 422);
        }
    }
}

try {
    $conn->begin_transaction();

    if ($id > 0) {
        $checkStmt = $conn->prepare('SELECT key_name FROM ad_placements WHERE id = ? LIMIT 1');
        if (!$checkStmt) {
            throw new RuntimeException('Failed to prepare placement lookup: ' . $conn->error);
        }
        $checkStmt->bind_param('i', $id);
        $checkStmt->execute();
        $oldPlacement = $checkStmt->get_result()->fetch_assoc();
        $checkStmt->close();

        if (!$oldPlacement) {
            throw new RuntimeException('Placement not found for update');
        }

        if ((string)$oldPlacement['key_name'] !== $keyName) {
            $dupStmt = $conn->prepare('SELECT id FROM ad_placements WHERE key_name = ? AND id != ? LIMIT 1');
            if (!$dupStmt) {
                throw new RuntimeException('Failed to prepare duplicate check: ' . $conn->error);
            }
            $dupStmt->bind_param('si', $keyName, $id);
            $dupStmt->execute();
            $hasDuplicate = $dupStmt->get_result()->num_rows > 0;
            $dupStmt->close();

            if ($hasDuplicate) {
                throw new RuntimeException('Placement key already exists');
            }
        }

        if ($refreshSeconds !== null && $frequencyOverride !== null) {
            $updateStmt = $conn->prepare(
                'UPDATE ad_placements
                 SET key_name = ?, ad_type = ?, screen_hint = ?, enabled = ?, auto_disabled = ?, refresh_seconds = ?, frequency_override = ?, updated_at = NOW()
                 WHERE id = ?'
            );
            if (!$updateStmt) {
                throw new RuntimeException('Failed to prepare placement update: ' . $conn->error);
            }
            $updateStmt->bind_param('sssiiiii', $keyName, $adType, $screenHint, $enabled, $autoDisabled, $refreshSeconds, $frequencyOverride, $id);
        } elseif ($refreshSeconds !== null) {
            $updateStmt = $conn->prepare(
                'UPDATE ad_placements
                 SET key_name = ?, ad_type = ?, screen_hint = ?, enabled = ?, auto_disabled = ?, refresh_seconds = ?, frequency_override = NULL, updated_at = NOW()
                 WHERE id = ?'
            );
            if (!$updateStmt) {
                throw new RuntimeException('Failed to prepare placement update: ' . $conn->error);
            }
            $updateStmt->bind_param('sssiiii', $keyName, $adType, $screenHint, $enabled, $autoDisabled, $refreshSeconds, $id);
        } elseif ($frequencyOverride !== null) {
            $updateStmt = $conn->prepare(
                'UPDATE ad_placements
                 SET key_name = ?, ad_type = ?, screen_hint = ?, enabled = ?, auto_disabled = ?, refresh_seconds = NULL, frequency_override = ?, updated_at = NOW()
                 WHERE id = ?'
            );
            if (!$updateStmt) {
                throw new RuntimeException('Failed to prepare placement update: ' . $conn->error);
            }
            $updateStmt->bind_param('sssiiii', $keyName, $adType, $screenHint, $enabled, $autoDisabled, $frequencyOverride, $id);
        } else {
            $updateStmt = $conn->prepare(
                'UPDATE ad_placements
                 SET key_name = ?, ad_type = ?, screen_hint = ?, enabled = ?, auto_disabled = ?, refresh_seconds = NULL, frequency_override = NULL, updated_at = NOW()
                 WHERE id = ?'
            );
            if (!$updateStmt) {
                throw new RuntimeException('Failed to prepare placement update: ' . $conn->error);
            }
            $updateStmt->bind_param('sssiii', $keyName, $adType, $screenHint, $enabled, $autoDisabled, $id);
        }

        if (!$updateStmt->execute()) {
            throw new RuntimeException('Failed to update placement: ' . $updateStmt->error);
        }
        $updateStmt->close();

        $action = 'updated';
        $placementId = $id;
    } else {
        $checkStmt = $conn->prepare('SELECT id FROM ad_placements WHERE key_name = ? LIMIT 1');
        if (!$checkStmt) {
            throw new RuntimeException('Failed to prepare duplicate check: ' . $conn->error);
        }
        $checkStmt->bind_param('s', $keyName);
        $checkStmt->execute();
        $exists = $checkStmt->get_result()->num_rows > 0;
        $checkStmt->close();

        if ($exists) {
            throw new RuntimeException('Placement key already exists');
        }

        if ($refreshSeconds !== null && $frequencyOverride !== null) {
            $insertStmt = $conn->prepare(
                'INSERT INTO ad_placements (key_name, ad_type, screen_hint, enabled, auto_disabled, refresh_seconds, frequency_override)
                 VALUES (?, ?, ?, ?, ?, ?, ?)'
            );
            if (!$insertStmt) {
                throw new RuntimeException('Failed to prepare placement insert: ' . $conn->error);
            }
            $insertStmt->bind_param('sssiiii', $keyName, $adType, $screenHint, $enabled, $autoDisabled, $refreshSeconds, $frequencyOverride);
        } elseif ($refreshSeconds !== null) {
            $insertStmt = $conn->prepare(
                'INSERT INTO ad_placements (key_name, ad_type, screen_hint, enabled, auto_disabled, refresh_seconds, frequency_override)
                 VALUES (?, ?, ?, ?, ?, ?, NULL)'
            );
            if (!$insertStmt) {
                throw new RuntimeException('Failed to prepare placement insert: ' . $conn->error);
            }
            $insertStmt->bind_param('sssiii', $keyName, $adType, $screenHint, $enabled, $autoDisabled, $refreshSeconds);
        } elseif ($frequencyOverride !== null) {
            $insertStmt = $conn->prepare(
                'INSERT INTO ad_placements (key_name, ad_type, screen_hint, enabled, auto_disabled, refresh_seconds, frequency_override)
                 VALUES (?, ?, ?, ?, ?, NULL, ?)'
            );
            if (!$insertStmt) {
                throw new RuntimeException('Failed to prepare placement insert: ' . $conn->error);
            }
            $insertStmt->bind_param('sssiii', $keyName, $adType, $screenHint, $enabled, $autoDisabled, $frequencyOverride);
        } else {
            $insertStmt = $conn->prepare(
                'INSERT INTO ad_placements (key_name, ad_type, screen_hint, enabled, auto_disabled, refresh_seconds, frequency_override)
                 VALUES (?, ?, ?, ?, ?, NULL, NULL)'
            );
            if (!$insertStmt) {
                throw new RuntimeException('Failed to prepare placement insert: ' . $conn->error);
            }
            $insertStmt->bind_param('sssii', $keyName, $adType, $screenHint, $enabled, $autoDisabled);
        }

        if (!$insertStmt->execute()) {
            throw new RuntimeException('Failed to create placement: ' . $insertStmt->error);
        }
        $placementId = (int)$insertStmt->insert_id;
        $insertStmt->close();

        $action = 'created';
    }

    $conn->query('UPDATE ads_global_settings SET config_version = config_version + 1, updated_at = NOW() WHERE id = 1');

    $adminId = (int)($_SESSION['admin_id'] ?? 0);
    $actionDetails = "Placement {$action}: {$keyName} ({$adType})";
    $ipAddress = (string)($_SERVER['REMOTE_ADDR'] ?? '');
    $userAgent = (string)($_SERVER['HTTP_USER_AGENT'] ?? '');

    $logStmt = $conn->prepare(
        "INSERT INTO admin_logs (admin_id, action, details, ip_address, user_agent)
         VALUES (?, 'placement_{$action}', ?, ?, ?)"
    );
    if ($logStmt) {
        $logStmt->bind_param('isss', $adminId, $actionDetails, $ipAddress, $userAgent);
        $logStmt->execute();
        $logStmt->close();
    }

    $conn->commit();

    respondPlacement(true, 'Placement ' . ($id > 0 ? 'updated' : 'created') . ' successfully!', 200, [
        'placement_id' => $placementId,
        'action' => $action
    ]);
} catch (Throwable $e) {
    $conn->rollback();
    error_log('save_placement failed admin_id=' . (int)($_SESSION['admin_id'] ?? 0) . ' placement_id=' . $id . ' key=' . $keyName . ' type=' . $adType . ' error=' . $e->getMessage());
    respondPlacement(false, $e->getMessage(), 500);
}
