<?php
session_start();
require_once '../../config.php';
include_once '../../includes/header.php';

if (!isset($_SESSION['admin'])) {
    header("Location: ../../login.php");
    exit();
}

function h($v) { return htmlspecialchars($v ?? '', ENT_QUOTES); }

$statusFilter = $_GET['status'] ?? 'All';

$query = "SELECT * FROM support_queries";
if ($statusFilter !== 'All') {
    $stmt = $pdo->prepare("SELECT * FROM support_queries WHERE status = :status ORDER BY created_at DESC");
    $stmt->execute(['status' => $statusFilter]);
} else {
    $stmt = $pdo->query("SELECT * FROM support_queries ORDER BY created_at DESC");
}
$queries = $stmt->fetchAll(PDO::FETCH_ASSOC);
?>
<div class="container-fluid" style="max-width:1200px;">
    <div class="d-flex align-items-center justify-content-between mb-4">
        <div>
            <h2 class="mb-0">💬 Help & Support</h2>
            <div class="text-muted">View and manage all user queries</div>
        </div>
        <div>
            <a href="?status=All" class="btn btn-outline-secondary btn-sm <?php if($statusFilter=='All') echo 'active'; ?>">All</a>
            <a href="?status=Pending" class="btn btn-outline-warning btn-sm <?php if($statusFilter=='Pending') echo 'active'; ?>">Pending</a>
            <a href="?status=Resolved" class="btn btn-outline-success btn-sm <?php if($statusFilter=='Resolved') echo 'active'; ?>">Resolved</a>
        </div>
    </div>

    <div class="card shadow-sm">
        <div class="card-body p-0">
            <table class="table table-hover align-middle mb-0">
                <thead class="table-light">
                    <tr>
                        <th>#</th>
                        <th>Name</th>
                        <th>Email</th>
                        <th>Phone</th>
                        <th>Message</th>
                        <th>Status</th>
                        <th>Created</th>
                        <th>Action</th>
                    </tr>
                </thead>
                <tbody>
                    <?php if (empty($queries)): ?>
                        <tr><td colspan="8" class="text-center text-muted py-4">No support queries found.</td></tr>
                    <?php else: foreach ($queries as $row): ?>
                        <tr>
                            <td><?= h($row['id']); ?></td>
                            <td><?= h($row['name']); ?></td>
                            <td><?= h($row['email']); ?></td>
                            <td><?= h($row['phone']); ?></td>
                            <td><?= h(mb_strimwidth($row['message'], 0, 40, '...')); ?></td>
                            <td>
                                <?php if ($row['status'] === 'Resolved'): ?>
                                    <span class="badge bg-success">Resolved</span>
                                <?php else: ?>
                                    <span class="badge bg-warning text-dark">Pending</span>
                                <?php endif; ?>
                            </td>
                            <td><?= h(date("d M Y, h:i A", strtotime($row['created_at']))); ?></td>
                            <td>
                                <a href="view_support.php?id=<?= (int)$row['id']; ?>" class="btn btn-sm btn-primary">View</a>
                            </td>
                        </tr>
                    <?php endforeach; endif; ?>
                </tbody>
            </table>
        </div>
    </div>
</div>

<?php include_once '../../includes/footer.php'; ?>
