<?php
session_start();
require '../../config.php';
require_once '../../app/helpers/potd_helper.php';

if (!isset($_SESSION['admin'])) {
    http_response_code(403);
    die('Access denied.');
}

function potd_redirect_with_message(string $message, string $type = 'info'): void
{
    $_SESSION['message'] = $message;
    $_SESSION['message_type'] = $type;
    header('Location: prompt_of_day_management.php');
    exit;
}

function potd_clean_text(?string $value, int $maxLen): ?string
{
    $text = trim((string)$value);
    if ($text === '') {
        return null;
    }
    if (mb_strlen($text) > $maxLen) {
        $text = mb_substr($text, 0, $maxLen);
    }
    return $text;
}

function potd_parse_date(?string $value): ?string
{
    $raw = trim((string)$value);
    if ($raw === '') {
        return null;
    }
    $dt = DateTime::createFromFormat('Y-m-d', $raw);
    if (!$dt || $dt->format('Y-m-d') !== $raw) {
        return null;
    }
    return $raw;
}

if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') {
    potd_redirect_with_message('Invalid request method.', 'danger');
}

$csrfPost = $_POST['csrf_token'] ?? '';
if (empty($_SESSION['csrf_token']) || !hash_equals((string)$_SESSION['csrf_token'], (string)$csrfPost)) {
    potd_redirect_with_message('Invalid CSRF token. Please retry.', 'danger');
}

if (!potd_db_table_exists($conn, 'prompt_of_day_entries') || !potd_db_table_exists($conn, 'prompt_of_day_config')) {
    potd_redirect_with_message('Prompt of the Day tables are missing. Run migration 2026_03_24_prompt_of_day_management.sql first.', 'danger');
}

$action = strtolower(trim((string)($_POST['action'] ?? '')));
$adminId = isset($_SESSION['admin_id']) ? (int)$_SESSION['admin_id'] : null;
$config = potd_load_config($conn);
$allowPremium = !empty($config['allow_premium_prompts']);

if ($action === 'save_config') {
    $allowFeaturedFallback = !empty($_POST['allow_featured_fallback']) ? 1 : 0;
    $allowPremiumPrompts = !empty($_POST['allow_premium_prompts']) ? 1 : 0;
    $legacyFallback = !empty($_POST['enable_legacy_daily_fallback']) ? 1 : 0;

    $mode = potd_normalize_mode((string)($_POST['featured_fallback_mode'] ?? 'NORMAL'));
    $discount = max(0, (int)($_POST['featured_fallback_discount_cost_points'] ?? 0));
    if ($mode !== 'DISCOUNT') {
        $discount = 0;
    } elseif ($discount <= 0) {
        $discount = 50;
    }

    $defaultBadge = potd_clean_text($_POST['default_badge_text'] ?? null, 80);

    $sql = "
        INSERT INTO prompt_of_day_config (
            id,
            allow_featured_fallback,
            allow_premium_prompts,
            enable_legacy_daily_fallback,
            featured_fallback_mode,
            featured_fallback_discount_cost_points,
            default_badge_text,
            updated_by_admin_id
        ) VALUES (1, ?, ?, ?, ?, ?, ?, ?)
        ON DUPLICATE KEY UPDATE
            allow_featured_fallback = VALUES(allow_featured_fallback),
            allow_premium_prompts = VALUES(allow_premium_prompts),
            enable_legacy_daily_fallback = VALUES(enable_legacy_daily_fallback),
            featured_fallback_mode = VALUES(featured_fallback_mode),
            featured_fallback_discount_cost_points = VALUES(featured_fallback_discount_cost_points),
            default_badge_text = VALUES(default_badge_text),
            updated_by_admin_id = VALUES(updated_by_admin_id),
            updated_at = CURRENT_TIMESTAMP
    ";

    $stmt = $conn->prepare($sql);
    if (!$stmt) {
        potd_redirect_with_message('Failed to prepare config update.', 'danger');
    }
    $stmt->bind_param(
        'iiisisi',
        $allowFeaturedFallback,
        $allowPremiumPrompts,
        $legacyFallback,
        $mode,
        $discount,
        $defaultBadge,
        $adminId
    );
    $ok = $stmt->execute();
    $stmt->close();

    if (!$ok) {
        potd_redirect_with_message('Failed to save Prompt of the Day config.', 'danger');
    }

    potd_redirect_with_message('Prompt of the Day config updated successfully.', 'success');
}

if ($action === 'save_entry') {
    $entryId = (int)($_POST['entry_id'] ?? 0);
    $promptId = (int)($_POST['prompt_id'] ?? 0);
    $isDefault = !empty($_POST['is_default']) ? 1 : 0;
    $isActive = !empty($_POST['is_active']) ? 1 : 0;
    $priority = (int)($_POST['priority'] ?? 0);
    if ($priority < -9999) {
        $priority = -9999;
    }
    if ($priority > 9999) {
        $priority = 9999;
    }

    $titleOverride = potd_clean_text($_POST['title_override'] ?? null, 255);
    $subtitleOverride = potd_clean_text($_POST['subtitle_override'] ?? null, 255);
    $badgeText = potd_clean_text($_POST['badge_text'] ?? null, 80);

    $mode = potd_normalize_mode((string)($_POST['mode'] ?? 'NORMAL'));
    $discountCostPoints = max(0, (int)($_POST['discount_cost_points'] ?? 0));
    if ($mode !== 'DISCOUNT') {
        $discountCostPoints = 0;
    } elseif ($discountCostPoints <= 0) {
        $discountCostPoints = 50;
    }

    $startDate = null;
    $endDate = null;

    if ($promptId <= 0) {
        potd_redirect_with_message('Select a valid published prompt.', 'danger');
    }

    $effectiveAllowPremium = !empty($config['allow_premium_prompts']);
    if (!potd_prompt_is_eligible($conn, $promptId, $effectiveAllowPremium)) {
        if ($effectiveAllowPremium) {
            potd_redirect_with_message('Selected prompt must be published to be used in Prompt of the Day.', 'danger');
        }
        potd_redirect_with_message('Selected prompt is not allowed. Premium prompts are currently disabled in POTD config.', 'danger');
    }

    if (!$isDefault) {
        $startDate = potd_parse_date($_POST['start_date'] ?? null);
        $endDate = potd_parse_date($_POST['end_date'] ?? null);

        if ($startDate === null) {
            potd_redirect_with_message('Start date is required for scheduled Prompt of the Day entries.', 'danger');
        }
        if ($endDate !== null && $endDate < $startDate) {
            potd_redirect_with_message('End date cannot be earlier than start date.', 'danger');
        }
    }

    if ($isActive) {
        if ($isDefault) {
            if (potd_has_other_active_default($conn, $entryId)) {
                potd_redirect_with_message('Another active default Prompt of the Day exists. Deactivate it first.', 'danger');
            }
        } else {
            if (potd_has_schedule_conflict($conn, $startDate, $endDate, $entryId)) {
                potd_redirect_with_message('Schedule conflict: another active Prompt of the Day overlaps the selected date range.', 'danger');
            }
        }
    }

    if ($entryId > 0) {
        $sql = "
            UPDATE prompt_of_day_entries
            SET
                prompt_id = ?,
                title_override = ?,
                subtitle_override = ?,
                badge_text = ?,
                start_date = ?,
                end_date = ?,
                mode = ?,
                discount_cost_points = ?,
                priority = ?,
                is_default = ?,
                is_active = ?,
                updated_by_admin_id = ?
            WHERE id = ?
            LIMIT 1
        ";

        $stmt = $conn->prepare($sql);
        if (!$stmt) {
            potd_redirect_with_message('Failed to prepare Prompt of the Day update.', 'danger');
        }
        $stmt->bind_param(
            'issssssiiiiii',
            $promptId,
            $titleOverride,
            $subtitleOverride,
            $badgeText,
            $startDate,
            $endDate,
            $mode,
            $discountCostPoints,
            $priority,
            $isDefault,
            $isActive,
            $adminId,
            $entryId
        );
        $ok = $stmt->execute();
        $stmt->close();

        if (!$ok) {
            potd_redirect_with_message('Failed to update Prompt of the Day entry.', 'danger');
        }

        potd_redirect_with_message('Prompt of the Day entry updated successfully.', 'success');
    }

    $sql = "
        INSERT INTO prompt_of_day_entries (
            prompt_id,
            title_override,
            subtitle_override,
            badge_text,
            start_date,
            end_date,
            mode,
            discount_cost_points,
            priority,
            is_default,
            is_active,
            created_by_admin_id,
            updated_by_admin_id
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    ";

    $stmt = $conn->prepare($sql);
    if (!$stmt) {
        potd_redirect_with_message('Failed to prepare Prompt of the Day insert.', 'danger');
    }
    $stmt->bind_param(
        'issssssiiiiii',
        $promptId,
        $titleOverride,
        $subtitleOverride,
        $badgeText,
        $startDate,
        $endDate,
        $mode,
        $discountCostPoints,
        $priority,
        $isDefault,
        $isActive,
        $adminId,
        $adminId
    );
    $ok = $stmt->execute();
    $stmt->close();

    if (!$ok) {
        potd_redirect_with_message('Failed to create Prompt of the Day entry.', 'danger');
    }

    potd_redirect_with_message('Prompt of the Day entry created successfully.', 'success');
}

if ($action === 'toggle_entry') {
    $entryId = (int)($_POST['entry_id'] ?? 0);
    $nextActive = !empty($_POST['next_active']) ? 1 : 0;

    if ($entryId <= 0) {
        potd_redirect_with_message('Invalid entry id for toggle.', 'danger');
    }

    $stmt = $conn->prepare(" 
        SELECT id, prompt_id, is_default, start_date, end_date
        FROM prompt_of_day_entries
        WHERE id = ?
        LIMIT 1
    ");
    if (!$stmt) {
        potd_redirect_with_message('Failed to load entry for toggle.', 'danger');
    }
    $stmt->bind_param('i', $entryId);
    $stmt->execute();
    $res = $stmt->get_result();
    $entry = $res ? $res->fetch_assoc() : null;
    $stmt->close();

    if (!$entry) {
        potd_redirect_with_message('Prompt of the Day entry not found.', 'danger');
    }

    if ($nextActive === 1) {
        if (!potd_prompt_is_eligible($conn, (int)$entry['prompt_id'], $allowPremium)) {
            potd_redirect_with_message('Entry cannot be activated because its prompt is no longer eligible/published.', 'danger');
        }

        if ((int)($entry['is_default'] ?? 0) === 1) {
            if (potd_has_other_active_default($conn, $entryId)) {
                potd_redirect_with_message('Another active default Prompt of the Day exists. Deactivate it first.', 'danger');
            }
        } else {
            $startDate = (string)($entry['start_date'] ?? '');
            if ($startDate === '') {
                potd_redirect_with_message('Scheduled entries require a start date before activation.', 'danger');
            }
            $endDate = $entry['end_date'] ? (string)$entry['end_date'] : null;
            if (potd_has_schedule_conflict($conn, $startDate, $endDate, $entryId)) {
                potd_redirect_with_message('Cannot activate due to overlapping active schedule.', 'danger');
            }
        }
    }

    $stmt = $conn->prepare(" 
        UPDATE prompt_of_day_entries
        SET is_active = ?, updated_by_admin_id = ?
        WHERE id = ?
        LIMIT 1
    ");
    if (!$stmt) {
        potd_redirect_with_message('Failed to toggle entry.', 'danger');
    }
    $stmt->bind_param('iii', $nextActive, $adminId, $entryId);
    $ok = $stmt->execute();
    $stmt->close();

    if (!$ok) {
        potd_redirect_with_message('Failed to update entry active state.', 'danger');
    }

    potd_redirect_with_message('Prompt of the Day entry status updated.', 'success');
}

if ($action === 'delete_entry') {
    $entryId = (int)($_POST['entry_id'] ?? 0);
    if ($entryId <= 0) {
        potd_redirect_with_message('Invalid entry id for delete.', 'danger');
    }

    $stmt = $conn->prepare('DELETE FROM prompt_of_day_entries WHERE id = ? LIMIT 1');
    if (!$stmt) {
        potd_redirect_with_message('Failed to prepare delete operation.', 'danger');
    }
    $stmt->bind_param('i', $entryId);
    $ok = $stmt->execute();
    $affected = (int)$stmt->affected_rows;
    $stmt->close();

    if (!$ok) {
        potd_redirect_with_message('Failed to delete Prompt of the Day entry.', 'danger');
    }
    if ($affected <= 0) {
        potd_redirect_with_message('Prompt of the Day entry not found or already deleted.', 'warning');
    }

    potd_redirect_with_message('Prompt of the Day entry deleted.', 'success');
}

potd_redirect_with_message('Unknown action.', 'danger');
