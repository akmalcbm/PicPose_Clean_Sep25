<?php
session_start();
require '../../config.php';
if (!isset($_SESSION['admin'])) { header('Location: ../../login.php'); exit(); }

if (empty($_SESSION['csrf_token'])) $_SESSION['csrf_token'] = bin2hex(random_bytes(32));
$csrf = $_SESSION['csrf_token'];

$userId = (int)($_GET['user_id'] ?? $_GET['id'] ?? $_POST['user_id'] ?? $_POST['id'] ?? 0);
$user = null;
$selectorNotice = '';

if ($userId > 0) {
    $userStmt = $conn->prepare('SELECT id, email, account_type FROM users WHERE id = ? LIMIT 1');
    if ($userStmt) {
        $userStmt->bind_param('i', $userId);
        $userStmt->execute();
        $userRes = $userStmt->get_result();
        $user = $userRes ? $userRes->fetch_assoc() : null;
        $userStmt->close();
    }
}

if ($userId <= 0) {
    $selectorNotice = 'Select a user before adjusting points.';
} elseif (!$user) {
    $selectorNotice = 'User not found. Select a valid user to continue.';
}

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $csrfPost = $_POST['csrf_token'] ?? '';
    if (!hash_equals((string)$csrf, (string)$csrfPost)) {
        $_SESSION['message'] = 'Invalid CSRF token.';
        $_SESSION['message_type'] = 'danger';
        header('Location: adjust_points.php?user_id=' . (int)$userId);
        exit();
    }

    if (!$user) {
        $_SESSION['message'] = $userId > 0 ? 'User not found.' : 'Select a user before adjusting points.';
        $_SESSION['message_type'] = 'danger';
        header('Location: adjust_points.php');
        exit();
    }

    $delta = (int)($_POST['delta_points'] ?? 0);
    $reason = trim((string)($_POST['reason'] ?? ''));
    if ($delta === 0) {
        $_SESSION['message'] = 'Points adjustment cannot be zero.';
        $_SESSION['message_type'] = 'warning';
        header('Location: adjust_points.php?user_id=' . (int)$userId);
        exit();
    }
    if ($reason === '') {
        $_SESSION['message'] = 'Reason is required.';
        $_SESSION['message_type'] = 'warning';
        header('Location: adjust_points.php?user_id=' . (int)$userId);
        exit();
    }

    $adminId = (int)($_SESSION['admin_id'] ?? 0);
    $refId = 'admin:' . $adminId;
    if (strlen($refId) > 80) $refId = substr($refId, 0, 80);

    $meta = json_encode([
        'reason' => $reason,
        'admin_id' => $adminId,
    ], JSON_UNESCAPED_UNICODE);

    $conn->begin_transaction();
    try {
        $walletStmt = $conn->prepare('SELECT points_balance FROM user_wallet WHERE user_id = ? FOR UPDATE');
        if (!$walletStmt) throw new RuntimeException('wallet lock prepare failed');
        $walletStmt->bind_param('i', $userId);
        if (!$walletStmt->execute()) throw new RuntimeException('wallet lock failed');
        $walletRes = $walletStmt->get_result();
        $walletRow = $walletRes ? $walletRes->fetch_assoc() : null;
        $walletStmt->close();

        if (!$walletRow) {
            $createWalletStmt = $conn->prepare('INSERT INTO user_wallet (user_id, points_balance) VALUES (?, 0)');
            if (!$createWalletStmt) throw new RuntimeException('wallet create prepare failed');
            $createWalletStmt->bind_param('i', $userId);
            if (!$createWalletStmt->execute()) throw new RuntimeException('wallet create failed');
            $createWalletStmt->close();

            $walletStmt = $conn->prepare('SELECT points_balance FROM user_wallet WHERE user_id = ? FOR UPDATE');
            if (!$walletStmt) throw new RuntimeException('wallet relock prepare failed');
            $walletStmt->bind_param('i', $userId);
            if (!$walletStmt->execute()) throw new RuntimeException('wallet relock failed');
            $walletRes = $walletStmt->get_result();
            $walletRow = $walletRes ? $walletRes->fetch_assoc() : null;
            $walletStmt->close();
        }

        if (!$walletRow) throw new RuntimeException('wallet row missing');

        $currentBalance = (int)$walletRow['points_balance'];
        $newBalance = $currentBalance + $delta;
        if ($newBalance < 0) {
            throw new InvalidArgumentException('negative_balance');
        }

        $updateWalletStmt = $conn->prepare('UPDATE user_wallet SET points_balance = ? WHERE user_id = ?');
        if (!$updateWalletStmt) throw new RuntimeException('wallet update prepare failed');
        $newBalanceParam = (string)$newBalance;
        $updateWalletStmt->bind_param('si', $newBalanceParam, $userId);
        if (!$updateWalletStmt->execute()) throw new RuntimeException('wallet update failed');
        $updateWalletStmt->close();

        $insertLedgerStmt = $conn->prepare("
            INSERT INTO points_ledger
                (user_id, type, delta_points, balance_after, ref_type, ref_id, meta_json)
            VALUES
                (?, 'ADJUST', ?, ?, 'admin', ?, ?)
        ");
        if (!$insertLedgerStmt) throw new RuntimeException('ledger insert prepare failed');
        $deltaParam = (string)$delta;
        $balanceAfterParam = (string)$newBalance;
        $metaParam = $meta ?: null;
        $insertLedgerStmt->bind_param('issss', $userId, $deltaParam, $balanceAfterParam, $refId, $metaParam);
        if (!$insertLedgerStmt->execute()) throw new RuntimeException('ledger insert failed');
        $insertLedgerStmt->close();

        $conn->commit();
        $_SESSION['message'] = 'Points adjusted successfully. New balance: ' . $newBalance;
        $_SESSION['message_type'] = 'success';
    } catch (Throwable $e) {
        $conn->rollback();
        if ($e instanceof InvalidArgumentException && $e->getMessage() === 'negative_balance') {
            $_SESSION['message'] = 'Adjustment blocked: resulting balance would be negative.';
            $_SESSION['message_type'] = 'danger';
        } else {
            error_log('adjust_points error: ' . $e->getMessage());
            $_SESSION['message'] = 'Failed to adjust points.';
            $_SESSION['message_type'] = 'danger';
        }
    }

    header('Location: adjust_points.php?user_id=' . (int)$userId);
    exit();
}

$currentBalance = 0;
if ($user) {
    $balStmt = $conn->prepare('SELECT points_balance FROM user_wallet WHERE user_id = ? LIMIT 1');
    if ($balStmt) {
        $balStmt->bind_param('i', $userId);
        $balStmt->execute();
        $balRes = $balStmt->get_result();
        $balRow = $balRes ? $balRes->fetch_assoc() : null;
        $balStmt->close();
        $currentBalance = (int)($balRow['points_balance'] ?? 0);
    }
}

include '../../includes/header.php';
?>

<div class="container">
  <div class="d-flex justify-content-between align-items-center mb-3">
    <h2 class="mb-0">Adjust Points</h2>
    <a href="user_wallets.php" class="btn btn-outline-secondary">Back to Wallets</a>
  </div>

  <?php if(!empty($_SESSION['message'])): ?>
    <div class="alert alert-<?php echo htmlspecialchars($_SESSION['message_type'] ?? 'info'); ?>">
      <?php echo htmlspecialchars($_SESSION['message']); unset($_SESSION['message'], $_SESSION['message_type']); ?>
    </div>
  <?php endif; ?>

  <?php if (!$user): ?>
    <div class="alert alert-warning"><?php echo htmlspecialchars($selectorNotice); ?></div>
    <?php include __DIR__ . '/_user_selector.php'; ?>
  <?php else: ?>
    <div class="card mb-3">
      <div class="card-body">
        <div><strong>User:</strong> #<?php echo (int)$user['id']; ?> (<?php echo htmlspecialchars((string)$user['email']); ?>)</div>
        <div><strong>Account Type:</strong> <?php echo htmlspecialchars((string)($user['account_type'] ?? 'normal')); ?></div>
        <div><strong>Current Balance:</strong> <?php echo $currentBalance; ?> pts</div>
      </div>
    </div>

    <form method="POST" class="card">
      <div class="card-body">
        <input type="hidden" name="csrf_token" value="<?php echo htmlspecialchars($csrf); ?>">
        <input type="hidden" name="user_id" value="<?php echo (int)$userId; ?>">

        <div class="mb-3">
          <label class="form-label">Points Delta</label>
          <input type="number" name="delta_points" class="form-control" placeholder="Use positive to add, negative to deduct" required>
        </div>

        <div class="mb-3">
          <label class="form-label">Reason</label>
          <textarea name="reason" class="form-control" rows="3" maxlength="500" required></textarea>
        </div>

        <button type="submit" class="btn btn-primary">Apply Adjustment</button>
      </div>
    </form>
  <?php endif; ?>
</div>

<?php include '../../includes/footer.php'; ?>
