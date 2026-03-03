<?php
require_once __DIR__ . '/../lib/v2_pack_entitlements.php';

if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') {
    json_err('Method Not Allowed', 405);
}

$user = require_user($conn);
$userId = (int)$user['id'];

$payload = json_decode(file_get_contents('php://input') ?: '', true);
if (!is_array($payload)) {
    json_err('Invalid JSON body', 400);
}

$packId = (int)($payload['pack_id'] ?? 0);
if ($packId <= 0) {
    json_err('Invalid pack_id', 400);
}

$packStmt = $conn->prepare("
    SELECT id, name, price_points, is_active
    FROM premium_packs
    WHERE id = ?
      AND is_active = 1
    LIMIT 1
");
if (!$packStmt) {
    json_err('Database query preparation failed', 500);
}
$packStmt->bind_param('i', $packId);
$packStmt->execute();
$packRes = $packStmt->get_result();
$pack = $packRes ? $packRes->fetch_assoc() : null;
$packStmt->close();

if (!$pack) {
    json_err('Pack not found', 404);
}

$cost = max(0, (int)($pack['price_points'] ?? 0));

$conn->begin_transaction();
try {
    $walletStmt = $conn->prepare('SELECT points_balance FROM user_wallet WHERE user_id = ? FOR UPDATE');
    if (!$walletStmt) {
        throw new RuntimeException('Failed to prepare wallet lock');
    }
    $walletStmt->bind_param('i', $userId);
    if (!$walletStmt->execute()) {
        throw new RuntimeException('Failed to lock wallet');
    }
    $walletRes = $walletStmt->get_result();
    $walletRow = $walletRes ? $walletRes->fetch_assoc() : null;
    $walletStmt->close();

    if (!$walletRow) {
        $createWalletStmt = $conn->prepare('INSERT INTO user_wallet (user_id, points_balance) VALUES (?, 0)');
        if (!$createWalletStmt) {
            throw new RuntimeException('Failed to prepare wallet creation');
        }
        $createWalletStmt->bind_param('i', $userId);
        if (!$createWalletStmt->execute()) {
            throw new RuntimeException('Failed to create wallet');
        }
        $createWalletStmt->close();

        $walletStmt = $conn->prepare('SELECT points_balance FROM user_wallet WHERE user_id = ? FOR UPDATE');
        if (!$walletStmt) {
            throw new RuntimeException('Failed to re-lock wallet');
        }
        $walletStmt->bind_param('i', $userId);
        if (!$walletStmt->execute()) {
            throw new RuntimeException('Failed to re-lock wallet');
        }
        $walletRes = $walletStmt->get_result();
        $walletRow = $walletRes ? $walletRes->fetch_assoc() : null;
        $walletStmt->close();
    }

    if (!$walletRow) {
        throw new RuntimeException('Wallet row missing');
    }

    $currentBalance = (int)$walletRow['points_balance'];

    $unlockStmt = $conn->prepare("
        INSERT INTO user_pack_unlocks (user_id, pack_id, unlock_type, points_spent)
        VALUES (?, ?, 'POINTS', ?)
    ");
    if (!$unlockStmt) {
        throw new RuntimeException('Failed to prepare pack unlock insert');
    }
    $unlockStmt->bind_param('iii', $userId, $packId, $cost);
    $unlockOk = $unlockStmt->execute();
    $unlockErr = (int)$unlockStmt->errno;
    $unlockStmt->close();

    if (!$unlockOk && $unlockErr === 1062) {
        $conn->commit();
        json_ok([
            'success' => true,
            'unlocked' => true,
            'points_balance' => $currentBalance,
            'cost' => $cost,
        ]);
    }

    if (!$unlockOk) {
        throw new RuntimeException('Failed to insert pack unlock');
    }

    if ($currentBalance < $cost) {
        throw new RuntimeException('Insufficient points');
    }

    $newBalance = $currentBalance - $cost;

    $updateStmt = $conn->prepare('UPDATE user_wallet SET points_balance = ? WHERE user_id = ?');
    if (!$updateStmt) {
        throw new RuntimeException('Failed to prepare wallet debit');
    }
    $newBalanceParam = (string)$newBalance;
    $updateStmt->bind_param('si', $newBalanceParam, $userId);
    if (!$updateStmt->execute()) {
        throw new RuntimeException('Failed to debit wallet');
    }
    $updateStmt->close();

    $ledgerStmt = $conn->prepare("
        INSERT INTO points_ledger
            (user_id, type, delta_points, balance_after, ref_type, ref_id, meta_json)
        VALUES
            (?, 'DEBIT_PACK_UNLOCK', ?, ?, 'pack', ?, NULL)
    ");
    if (!$ledgerStmt) {
        throw new RuntimeException('Failed to prepare ledger insert');
    }
    $deltaParam = (string)(-$cost);
    $balanceAfterParam = (string)$newBalance;
    $ledgerRefId = (string)$packId;
    $ledgerStmt->bind_param('isss', $userId, $deltaParam, $balanceAfterParam, $ledgerRefId);
    if (!$ledgerStmt->execute()) {
        throw new RuntimeException('Failed to insert ledger row');
    }
    $ledgerStmt->close();

    $conn->commit();

    json_ok([
        'success' => true,
        'unlocked' => true,
        'points_balance' => $newBalance,
        'cost' => $cost,
    ]);
} catch (Throwable $e) {
    $conn->rollback();

    if ($e->getMessage() === 'Insufficient points') {
        json_err('Insufficient points', 402);
    }

    json_err('Failed to unlock pack with points', 500);
}
