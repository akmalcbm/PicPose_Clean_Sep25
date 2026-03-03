<?php
require_once __DIR__ . '/../lib/v2_auth.php';

function referral_mark_qualified(mysqli $conn, int $refereeId): bool
{
    return mark_referral_qualified($conn, $refereeId);
}

function mark_referral_qualified(mysqli $conn, int $refereeId): bool
{
    $stmt = $conn->prepare("
        UPDATE referrals
        SET status = 'QUALIFIED'
        WHERE referee_id = ?
          AND status = 'PENDING'
        LIMIT 1
    ");
    if (!$stmt) {
        error_log('referral_mark_qualified: prepare failed');
        return false;
    }
    $stmt->bind_param('i', $refereeId);
    $ok = $stmt->execute();
    $stmt->close();
    return (bool)$ok;
}

if (realpath($_SERVER['SCRIPT_FILENAME'] ?? '') === __FILE__) {
    if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') {
        json_err('Method Not Allowed', 405);
    }

    $user = require_user($conn);
    $qualified = mark_referral_qualified($conn, (int)$user['id']);

    json_ok([
        'success' => true,
        'qualified' => $qualified,
    ]);
}
