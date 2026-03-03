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

$unlockResult = null;
if ($user) {
    $stmt = $conn->prepare("
        SELECT
            u.post_id,
            p.title,
            u.unlock_type,
            u.created_at
        FROM user_prompt_unlocks u
        LEFT JOIN ai_posts p ON p.id = u.post_id
        WHERE u.user_id = ?
        ORDER BY u.id DESC
        LIMIT 500
    ");
    if ($stmt) {
        $stmt->bind_param('i', $userId);
        $stmt->execute();
        $unlockResult = $stmt->get_result();
        $stmt->close();
    }
}

include '../../includes/header.php';
?>

<div class="container">
  <div class="d-flex justify-content-between align-items-center mb-3">
    <h2 class="mb-0">User Unlocks</h2>
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
          <th>Post ID</th>
          <th>Prompt Title</th>
          <th>Unlock Type</th>
          <th>Unlocked At</th>
        </tr>
      </thead>
      <tbody>
        <?php if ($unlockResult): while ($row = $unlockResult->fetch_assoc()): ?>
          <tr>
            <td><?php echo (int)$row['post_id']; ?></td>
            <td><?php echo htmlspecialchars((string)($row['title'] ?? 'Unknown Prompt')); ?></td>
            <td><?php echo htmlspecialchars((string)$row['unlock_type']); ?></td>
            <td><?php echo htmlspecialchars((string)$row['created_at']); ?></td>
          </tr>
        <?php endwhile; endif; ?>
      </tbody>
    </table>
  <?php endif; ?>
</div>

<?php include '../../includes/footer.php'; ?>
