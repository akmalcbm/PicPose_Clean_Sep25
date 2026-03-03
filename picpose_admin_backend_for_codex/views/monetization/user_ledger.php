<?php
session_start();
require '../../config.php';
if (!isset($_SESSION['admin'])) { header('Location: ../../login.php'); exit(); }

$userId = (int)($_GET['user_id'] ?? $_GET['id'] ?? 0);
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
    $selectorNotice = 'Select a user to view their points ledger.';
} elseif (!$user) {
    $selectorNotice = 'User not found. Select a valid user to continue.';
}

$ledgerResult = null;
if ($user) {
    $stmt = $conn->prepare("
        SELECT type, delta_points, balance_after, ref_type, ref_id, created_at
        FROM points_ledger
        WHERE user_id = ?
        ORDER BY id DESC
        LIMIT 500
    ");
    if ($stmt) {
        $stmt->bind_param('i', $userId);
        $stmt->execute();
        $ledgerResult = $stmt->get_result();
        $stmt->close();
    }
}

include '../../includes/header.php';
?>

<div class="container">
  <div class="d-flex justify-content-between align-items-center mb-3">
    <h2 class="mb-0">User Ledger</h2>
    <a href="user_wallets.php" class="btn btn-outline-secondary">Back to Wallets</a>
  </div>

  <?php if (!$user): ?>
    <div class="alert alert-warning"><?php echo htmlspecialchars($selectorNotice); ?></div>
    <?php include __DIR__ . '/_user_selector.php'; ?>
  <?php else: ?>
    <div class="mb-3">
      <strong>User:</strong> #<?php echo (int)$user['id']; ?> (<?php echo htmlspecialchars((string)$user['email']); ?>)
      <span class="text-muted ms-2">Account: <?php echo htmlspecialchars((string)($user['account_type'] ?? 'normal')); ?></span>
    </div>

    <table class="table table-bordered table-striped">
      <thead>
        <tr>
          <th>Type</th>
          <th>Delta</th>
          <th>Balance After</th>
          <th>Reference</th>
          <th>Created At</th>
        </tr>
      </thead>
      <tbody>
        <?php if ($ledgerResult && $ledgerResult->num_rows > 0): ?>
          <?php while ($row = $ledgerResult->fetch_assoc()): ?>
            <tr>
              <td><?php echo htmlspecialchars((string)$row['type']); ?></td>
              <td><?php echo htmlspecialchars((string)$row['delta_points']); ?></td>
              <td><?php echo htmlspecialchars((string)$row['balance_after']); ?></td>
              <td>
                <?php echo htmlspecialchars((string)($row['ref_type'] ?? '')); ?>
                /
                <?php echo htmlspecialchars((string)($row['ref_id'] ?? '')); ?>
              </td>
              <td><?php echo htmlspecialchars((string)$row['created_at']); ?></td>
            </tr>
          <?php endwhile; ?>
        <?php else: ?>
          <tr>
            <td colspan="5" class="text-center py-4 text-muted">No ledger entries found for this user.</td>
          </tr>
        <?php endif; ?>
      </tbody>
    </table>
  <?php endif; ?>
</div>

<?php include '../../includes/footer.php'; ?>
