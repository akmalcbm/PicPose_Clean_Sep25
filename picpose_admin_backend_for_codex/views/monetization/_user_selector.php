<?php
$selectorPage = basename((string)($_SERVER['PHP_SELF'] ?? 'user_wallets.php'));
$selectorSearch = trim((string)($_GET['search'] ?? ''));
$selectorUsers = [];

$selectorSql = "
    SELECT
        u.id,
        u.email,
        u.account_type,
        COALESCE(w.points_balance, 0) AS points_balance
    FROM users u
    LEFT JOIN user_wallet w ON w.user_id = u.id
";
$selectorTypes = '';
$selectorParams = [];

if ($selectorSearch !== '') {
    if (ctype_digit($selectorSearch)) {
        $selectorSql .= ' WHERE u.id = ?';
        $selectorTypes = 'i';
        $selectorParams[] = (int)$selectorSearch;
    } else {
        $selectorSql .= ' WHERE u.email LIKE CONCAT("%", ?, "%")';
        $selectorTypes = 's';
        $selectorParams[] = $selectorSearch;
    }
}

$selectorSql .= ' ORDER BY u.id DESC LIMIT 20';
$selectorStmt = $conn->prepare($selectorSql);
if ($selectorStmt) {
    if ($selectorTypes !== '') {
        $selectorStmt->bind_param($selectorTypes, ...$selectorParams);
    }
    $selectorStmt->execute();
    $selectorResult = $selectorStmt->get_result();
    if ($selectorResult) {
        while ($selectorRow = $selectorResult->fetch_assoc()) {
            $selectorUsers[] = $selectorRow;
        }
    }
    $selectorStmt->close();
}
?>

<div class="card mb-4">
  <div class="card-header">Select User</div>
  <div class="card-body">
    <form method="GET" class="row g-2 align-items-end">
      <div class="col-md-8">
        <label for="search" class="form-label">Search by email or numeric user ID</label>
        <input
          type="text"
          class="form-control"
          id="search"
          name="search"
          value="<?php echo htmlspecialchars($selectorSearch); ?>"
          placeholder="example@domain.com or 123"
        >
      </div>
      <div class="col-md-4 d-flex gap-2">
        <button type="submit" class="btn btn-primary">Go</button>
        <a href="<?php echo htmlspecialchars($selectorPage); ?>" class="btn btn-outline-secondary">Reset</a>
      </div>
    </form>
  </div>
</div>

<div class="card">
  <div class="card-header d-flex justify-content-between align-items-center">
    <span><?php echo $selectorSearch === '' ? 'Recent Users' : 'Search Results'; ?></span>
    <a href="user_wallets.php" class="btn btn-sm btn-outline-secondary">Monetization → User Wallets</a>
  </div>
  <div class="card-body p-0">
    <div class="table-responsive">
      <table class="table table-striped table-bordered mb-0">
        <thead>
          <tr>
            <th>User ID</th>
            <th>Email</th>
            <th>Account Type</th>
            <th>Points Balance</th>
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>
          <?php if ($selectorUsers): ?>
            <?php foreach ($selectorUsers as $selectorUser): ?>
              <tr>
                <td><?php echo (int)$selectorUser['id']; ?></td>
                <td><?php echo htmlspecialchars((string)$selectorUser['email']); ?></td>
                <td><?php echo htmlspecialchars((string)($selectorUser['account_type'] ?? 'normal')); ?></td>
                <td><?php echo (int)$selectorUser['points_balance']; ?></td>
                <td class="text-nowrap">
                  <a class="btn btn-sm btn-outline-primary" href="user_ledger.php?user_id=<?php echo (int)$selectorUser['id']; ?>">Ledger</a>
                  <a class="btn btn-sm btn-outline-secondary" href="user_unlocks.php?user_id=<?php echo (int)$selectorUser['id']; ?>">Unlocks</a>
                  <a class="btn btn-sm btn-warning" href="adjust_points.php?user_id=<?php echo (int)$selectorUser['id']; ?>">Adjust</a>
                </td>
              </tr>
            <?php endforeach; ?>
          <?php else: ?>
            <tr>
              <td colspan="5" class="text-center py-4 text-muted">
                No users matched that search.
              </td>
            </tr>
          <?php endif; ?>
        </tbody>
      </table>
    </div>
  </div>
</div>
