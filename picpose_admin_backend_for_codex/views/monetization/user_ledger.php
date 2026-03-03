<?php
session_start();
require '../../config.php';
if (!isset($_SESSION['admin'])) { header('Location: ../../login.php'); exit(); }

$userId = (int)($_GET['user_id'] ?? 0);
$user = null;

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
    <div class="alert alert-warning">Valid <code>user_id</code> is required.</div>
  <?php else: ?>
    <div class="mb-3">
      <strong>User:</strong> #<?php echo (int)$user['id']; ?> (<?php echo htmlspecialchars((string)$user['email']); ?>)
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
        <?php if ($ledgerResult): while ($row = $ledgerResult->fetch_assoc()): ?>
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
        <?php endwhile; endif; ?>
      </tbody>
    </table>
  <?php endif; ?>
</div>

<?php include '../../includes/footer.php'; ?>
