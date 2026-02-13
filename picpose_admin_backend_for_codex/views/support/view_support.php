<?php
session_start();
require_once '../../config.php';
include_once '../../includes/header.php';

if (!isset($_SESSION['admin'])) {
    header("Location: ../../login.php");
    exit();
}

function h($v) { return htmlspecialchars($v ?? '', ENT_QUOTES); }

$id = (int)($_GET['id'] ?? 0);
if (!$id) {
    echo "<div class='alert alert-danger'>Invalid query ID</div>";
    include_once '../../includes/footer.php';
    exit;
}

$stmt = $pdo->prepare("SELECT * FROM support_queries WHERE id = :id");
$stmt->execute(['id' => $id]);
$row = $stmt->fetch(PDO::FETCH_ASSOC);
if (!$row) {
    echo "<div class='alert alert-danger'>Query not found.</div>";
    include_once '../../includes/footer.php';
    exit;
}

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $newStatus = $_POST['status'] ?? 'Pending';
    $adminNote = trim($_POST['admin_note'] ?? '');
    $upd = $pdo->prepare("UPDATE support_queries SET status = :s, admin_note = :n WHERE id = :id");
    $upd->execute(['s' => $newStatus, 'n' => $adminNote, 'id' => $id]);
    header("Location: manage_support.php?status={$newStatus}");
    exit;
}
?>

<div class="container" style="max-width:800px;">
    <h3 class="mb-3">💬 Support Query #<?= h($row['id']); ?></h3>

    <div class="card mb-4">
        <div class="card-body">
            <p><strong>Name:</strong> <?= h($row['name']); ?></p>
            <p><strong>Email:</strong> <?= h($row['email']); ?></p>
            <p><strong>Phone:</strong> <?= h($row['phone']); ?></p>
            <hr>
            <p><strong>Message:</strong><br><?= nl2br(h($row['message'])); ?></p>
            <hr>
            <p><strong>Current Status:</strong>
                <?php if ($row['status'] === 'Resolved'): ?>
                    <span class="badge bg-success">Resolved</span>
                <?php else: ?>
                    <span class="badge bg-warning text-dark">Pending</span>
                <?php endif; ?>
            </p>
        </div>
    </div>

    <form method="post">
        <div class="mb-3">
            <label for="status" class="form-label fw-semibold">Update Status</label>
            <select id="status" name="status" class="form-select">
                <option value="Pending" <?= $row['status']=='Pending'?'selected':''; ?>>Pending</option>
                <option value="Resolved" <?= $row['status']=='Resolved'?'selected':''; ?>>Resolved</option>
            </select>
        </div>

        <div class="mb-3">
            <label for="admin_note" class="form-label fw-semibold">Admin Note / Reply (optional)</label>
            <textarea id="admin_note" name="admin_note" class="form-control" rows="4"><?= h($row['admin_note'] ?? ''); ?></textarea>
        </div>

        <div class="d-flex justify-content-between">
            <a href="manage_support.php" class="btn btn-outline-secondary">← Back</a>
            <button type="submit" class="btn btn-primary">Save Changes</button>
        </div>
    </form>
</div>

<?php include_once '../../includes/footer.php'; ?>
