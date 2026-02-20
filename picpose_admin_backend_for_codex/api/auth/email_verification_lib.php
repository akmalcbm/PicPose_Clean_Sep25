<?php
require_once __DIR__ . '/auth_common.php';

function auth_consume_email_verification_token(mysqli $conn, string $rawToken): array {
    $token = trim($rawToken);
    if ($token === '') {
        return ['ok' => false, 'message' => 'Invalid or expired verification link.'];
    }

    $tokenHash = auth_token_hash($token);

    $findStmt = $conn->prepare(
        'SELECT id, user_id FROM email_verifications '
        . 'WHERE token_hash = ? AND used_at IS NULL AND expires_at >= NOW() '
        . 'ORDER BY id DESC LIMIT 1'
    );
    if (!$findStmt) {
        return ['ok' => false, 'message' => 'Invalid or expired verification link.'];
    }

    $findStmt->bind_param('s', $tokenHash);
    $findStmt->execute();
    $row = $findStmt->get_result()->fetch_assoc();
    $findStmt->close();

    if (!$row) {
        return ['ok' => false, 'message' => 'Invalid or expired verification link.'];
    }

    $verificationId = (int)$row['id'];
    $userId = (int)$row['user_id'];

    $conn->begin_transaction();
    try {
        $verifyUserStmt = $conn->prepare(
            'UPDATE users SET email_verified = 1, email_verified_at = NOW() WHERE id = ? LIMIT 1'
        );
        if (!$verifyUserStmt) {
            throw new RuntimeException('Failed to verify user email');
        }
        $verifyUserStmt->bind_param('i', $userId);
        $verifyUserStmt->execute();
        $verifyUserStmt->close();

        $markUsedStmt = $conn->prepare('UPDATE email_verifications SET used_at = NOW() WHERE id = ? LIMIT 1');
        if (!$markUsedStmt) {
            throw new RuntimeException('Failed to mark verification token as used');
        }
        $markUsedStmt->bind_param('i', $verificationId);
        $markUsedStmt->execute();
        $markUsedStmt->close();

        $invalidateOtherStmt = $conn->prepare(
            'UPDATE email_verifications SET used_at = NOW() WHERE user_id = ? AND used_at IS NULL AND id <> ?'
        );
        if ($invalidateOtherStmt) {
            $invalidateOtherStmt->bind_param('ii', $userId, $verificationId);
            $invalidateOtherStmt->execute();
            $invalidateOtherStmt->close();
        }

        $conn->commit();

        return [
            'ok' => true,
            'message' => 'Email verified successfully.',
            'user_id' => $userId,
        ];
    } catch (Throwable $e) {
        $conn->rollback();
        auth_log_event('email_verification_consume_error', ['error' => $e->getMessage()]);
        return ['ok' => false, 'message' => 'Invalid or expired verification link.'];
    }
}
