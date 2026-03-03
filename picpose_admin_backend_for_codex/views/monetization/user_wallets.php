<?php
session_start();
require '../../config.php';
if (!isset($_SESSION['admin'])) { header('Location: ../../login.php'); exit(); }

$q = trim((string)($_GET['q'] ?? ''));
$page = max(1, (int)($_GET['page'] ?? 1));
$perPage = 20;
$offset = ($page - 1) * $perPage;

$where = 'WHERE 1=1';
$params = [];
$types = '';
if ($q !== '') {
    if (ctype_digit($q)) {
        $where .= ' AND (u.id = ? OR u.email LIKE CONCAT("%", ?, "%"))';
        $params[] = (int)$q;
        $params[] = $q;
        $types .= 'is';
    } else {
        $where .= ' AND (u.email LIKE CONCAT("%", ?, "%"))';
        $params[] = $q;
        $types .= 's';
    }
}

$countSql = "SELECT COUNT(1) AS cnt FROM users u $where";
$countStmt = $conn->prepare($countSql);
if (!$countStmt) {
    $_SESSION['message'] = 'Database error.';
    $_SESSION['message_type'] = 'danger';
    header('Location: ../../index.php');
    exit();
}
if ($types !== '') $countStmt->bind_param($types, ...$params);
$countStmt->execute();
$countRes = $countStmt->get_result()->fetch_assoc();
$total = (int)($countRes['cnt'] ?? 0);
$countStmt->close();

$sql = "
    SELECT
        u.id,
        u.email,
        u.account_type,
        COALESCE(w.points_balance, 0) AS points_balance,
        COALESCE(s.streak_count, 0) AS streak_count
    FROM users u
    LEFT JOIN user_wallet w ON w.user_id = u.id
    LEFT JOIN user_streaks s ON s.user_id = u.id
    $where
    ORDER BY u.id DESC
    LIMIT ? OFFSET ?
";
$stmt = $conn->prepare($sql);
if (!$stmt) {
    $_SESSION['message'] = 'Database error.';
    $_SESSION['message_type'] = 'danger';
    header('Location: ../../index.php');
    exit();
}
if ($types !== '') {
    $bindTypes = $types . 'ii';
    $bindParams = array_merge($params, [$perPage, $offset]);
    $stmt->bind_param($bindTypes, ...$bindParams);
} else {
    $stmt->bind_param('ii', $perPage, $offset);
}
$stmt->execute();
$result = $stmt->get_result();

if (empty($_SESSION['csrf_token'])) $_SESSION['csrf_token'] = bin2hex(random_bytes(32));

include '../../includes/header.php';
?>

<div class="container">
  <div class="d-flex justify-content-between align-items-center mb-3">
    <h2 class="mb-0">Monetization: User Wallets</h2>
  </div>

  <form method="GET" class="mb-3">
    <div class="input-group">
      <input type="text" class="form-control" name="q" placeholder="Search by email or user ID" value="<?php echo htmlspecialchars($q); ?>">
      <button type="submit" class="btn btn-outline-primary">Search</button>
    </div>
  </form>

  <?php if(!empty($_SESSION['message'])): ?>
    <div class="alert alert-<?php echo htmlspecialchars($_SESSION['message_type'] ?? 'info'); ?>">
      <?php echo htmlspecialchars($_SESSION['message']); unset($_SESSION['message'], $_SESSION['message_type']); ?>
    </div>
  <?php endif; ?>

  <table class="table table-bordered table-striped">
    <thead>
      <tr>
        <th>User ID</th>
        <th>Email</th>
        <th>Account Type</th>
        <th>Points Balance</th>
        <th>Streak Count</th>
        <th>Actions</th>
      </tr>
    </thead>
    <tbody>
      <?php while ($row = $result->fetch_assoc()): ?>
        <tr>
          <td><?php echo (int)$row['id']; ?></td>
          <td><?php echo htmlspecialchars((string)$row['email']); ?></td>
          <td><?php echo htmlspecialchars((string)($row['account_type'] ?? 'normal')); ?></td>
          <td><?php echo (int)$row['points_balance']; ?></td>
          <td><?php echo (int)$row['streak_count']; ?></td>
          <td style="white-space:nowrap;">
            <a class="btn btn-sm btn-outline-primary" href="user_ledger.php?user_id=<?php echo (int)$row['id']; ?>">View Ledger</a>
            <a class="btn btn-sm btn-outline-secondary" href="user_unlocks.php?user_id=<?php echo (int)$row['id']; ?>">View Unlocks</a>
            <a class="btn btn-sm btn-warning" href="adjust_points.php?user_id=<?php echo (int)$row['id']; ?>">Adjust Points</a>
          </td>
        </tr>
      <?php endwhile; ?>
    </tbody>
  </table>

  <?php
    $totalPages = max(1, (int)ceil($total / $perPage));
    if ($totalPages > 1):
  ?>
  <nav aria-label="Wallet pagination">
    <ul class="pagination">
      <?php for ($p = 1; $p <= $totalPages; $p++): ?>
        <li class="page-item <?php echo ($p === $page) ? 'active' : ''; ?>">
          <a class="page-link" href="?<?php echo http_build_query(['q' => $q, 'page' => $p]); ?>"><?php echo $p; ?></a>
        </li>
      <?php endfor; ?>
    </ul>
  </nav>
  <?php endif; ?>

  <div id="streak-config" class="card mt-4">
    <div class="card-header">Streak Config</div>
    <div class="card-body">
      <p class="mb-2">Daily login rewards are now configurable by day.</p>
      <a href="streak_config.php" class="btn btn-sm btn-primary">Open Streak Config</a>
    </div>
  </div>
</div>

<?php
$stmt->close();
include '../../includes/footer.php';
?>
