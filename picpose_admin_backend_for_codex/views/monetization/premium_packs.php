<?php
session_start();
require '../../config.php';
if (!isset($_SESSION['admin'])) { header('Location: ../../login.php'); exit(); }

if (empty($_SESSION['csrf_token'])) $_SESSION['csrf_token'] = bin2hex(random_bytes(32));
$csrf = $_SESSION['csrf_token'];

$editPackId = (int)($_GET['edit'] ?? 0);
$promptSearch = trim((string)($_GET['prompt_q'] ?? ''));

$packs = [];
$packsRes = $conn->query("
    SELECT
        pp.id,
        pp.name,
        pp.description,
        pp.price_points,
        pp.is_active,
        pp.created_at,
        COUNT(DISTINCT ppi.post_id) AS item_count,
        COUNT(DISTINCT upu.id) AS unlock_count,
        COALESCE(SUM(upu.points_spent), 0) AS revenue_points
    FROM premium_packs pp
    LEFT JOIN premium_pack_items ppi ON ppi.pack_id = pp.id
    LEFT JOIN user_pack_unlocks upu ON upu.pack_id = pp.id
    GROUP BY pp.id, pp.name, pp.description, pp.price_points, pp.is_active, pp.created_at
    ORDER BY pp.created_at DESC, pp.id DESC
");
if ($packsRes) {
    while ($row = $packsRes->fetch_assoc()) {
        $packs[] = $row;
    }
}

$editPack = [
    'id' => 0,
    'name' => '',
    'description' => '',
    'price_points' => 0,
    'is_active' => 1,
];
$selectedPromptIds = [];
$selectedPrompts = [];

if ($editPackId > 0) {
    $packStmt = $conn->prepare('SELECT id, name, description, price_points, is_active FROM premium_packs WHERE id = ? LIMIT 1');
    if ($packStmt) {
        $packStmt->bind_param('i', $editPackId);
        $packStmt->execute();
        $packRes = $packStmt->get_result();
        $packRow = $packRes ? $packRes->fetch_assoc() : null;
        $packStmt->close();
        if ($packRow) {
            $editPack = $packRow;
        } else {
            $editPackId = 0;
        }
    }
}

if ($editPackId > 0) {
    $itemsStmt = $conn->prepare("
        SELECT p.id, p.title, p.short_description
        FROM premium_pack_items ppi
        INNER JOIN ai_posts p ON p.id = ppi.post_id
        WHERE ppi.pack_id = ?
        ORDER BY p.priority DESC, p.created_at DESC
    ");
    if ($itemsStmt) {
        $itemsStmt->bind_param('i', $editPackId);
        $itemsStmt->execute();
        $itemsRes = $itemsStmt->get_result();
        while ($row = ($itemsRes ? $itemsRes->fetch_assoc() : null)) {
            $selectedPromptIds[] = (int)$row['id'];
            $selectedPrompts[(int)$row['id']] = $row;
        }
        $itemsStmt->close();
    }
}

$promptRows = $selectedPrompts;
$promptSql = "
    SELECT id, title, short_description
    FROM ai_posts
    WHERE status = 'published'
";
$params = [];
$types = '';
if ($promptSearch !== '') {
    $promptSql .= " AND (title LIKE CONCAT('%', ?, '%') OR short_description LIKE CONCAT('%', ?, '%'))";
    $params[] = $promptSearch;
    $params[] = $promptSearch;
    $types .= 'ss';
}
$promptSql .= " ORDER BY priority DESC, created_at DESC LIMIT 50";
$promptStmt = $conn->prepare($promptSql);
if ($promptStmt) {
    if ($types !== '') {
        $promptStmt->bind_param($types, ...$params);
    }
    $promptStmt->execute();
    $promptRes = $promptStmt->get_result();
    while ($row = ($promptRes ? $promptRes->fetch_assoc() : null)) {
        $promptRows[(int)$row['id']] = $row;
    }
    $promptStmt->close();
}

include '../../includes/header.php';
?>

<div class="container">
  <div class="d-flex justify-content-between align-items-center mb-3">
    <h2 class="mb-0">Premium Packs</h2>
    <?php if ($editPackId > 0): ?>
      <a href="premium_packs.php" class="btn btn-outline-secondary">Create New Pack</a>
    <?php endif; ?>
  </div>

  <?php if(!empty($_SESSION['message'])): ?>
    <div class="alert alert-<?php echo htmlspecialchars($_SESSION['message_type'] ?? 'info'); ?>">
      <?php echo htmlspecialchars($_SESSION['message']); unset($_SESSION['message'], $_SESSION['message_type']); ?>
    </div>
  <?php endif; ?>

  <div class="row g-4">
    <div class="col-lg-5">
      <div class="card">
        <div class="card-header">
          <?php echo $editPackId > 0 ? 'Edit Pack' : 'Create Pack'; ?>
        </div>
        <div class="card-body">
          <form method="GET" class="mb-3">
            <?php if ($editPackId > 0): ?>
              <input type="hidden" name="edit" value="<?php echo $editPackId; ?>">
            <?php endif; ?>
            <label class="form-label">Search Published Prompts</label>
            <div class="input-group">
              <input type="text" name="prompt_q" class="form-control" placeholder="Search prompts by title or short prompt" value="<?php echo htmlspecialchars($promptSearch); ?>">
              <button type="submit" class="btn btn-outline-primary">Search</button>
            </div>
          </form>

          <form method="POST" action="premium_packs_process.php">
            <input type="hidden" name="csrf_token" value="<?php echo htmlspecialchars($csrf); ?>">
            <input type="hidden" name="action" value="<?php echo $editPackId > 0 ? 'update' : 'create'; ?>">
            <input type="hidden" name="pack_id" value="<?php echo (int)$editPackId; ?>">

            <div class="mb-3">
              <label class="form-label">Name</label>
              <input type="text" name="name" class="form-control" maxlength="80" required value="<?php echo htmlspecialchars((string)$editPack['name']); ?>">
            </div>

            <div class="mb-3">
              <label class="form-label">Description</label>
              <textarea name="description" class="form-control" rows="3"><?php echo htmlspecialchars((string)($editPack['description'] ?? '')); ?></textarea>
            </div>

            <div class="mb-3">
              <label class="form-label">Price Points</label>
              <input type="number" min="0" name="price_points" class="form-control" required value="<?php echo (int)$editPack['price_points']; ?>">
            </div>

            <div class="form-check mb-3">
              <input class="form-check-input" type="checkbox" name="is_active" id="is_active" value="1" <?php echo !empty($editPack['is_active']) ? 'checked' : ''; ?>>
              <label class="form-check-label" for="is_active">Active</label>
            </div>

            <div class="mb-3">
              <label class="form-label">Included Prompts</label>
              <div class="border rounded p-2" style="max-height: 420px; overflow:auto;">
                <?php if (empty($promptRows)): ?>
                  <div class="text-muted small">No published prompts found.</div>
                <?php else: ?>
                  <?php foreach ($promptRows as $row): $postId = (int)$row['id']; ?>
                    <div class="form-check mb-2">
                      <input
                        class="form-check-input"
                        type="checkbox"
                        name="post_ids[]"
                        value="<?php echo $postId; ?>"
                        id="post_<?php echo $postId; ?>"
                        <?php echo in_array($postId, $selectedPromptIds, true) ? 'checked' : ''; ?>
                      >
                      <label class="form-check-label" for="post_<?php echo $postId; ?>">
                        <strong><?php echo htmlspecialchars((string)$row['title']); ?></strong>
                        <br>
                        <small class="text-muted"><?php echo htmlspecialchars(substr((string)($row['short_description'] ?? ''), 0, 120)); ?></small>
                      </label>
                    </div>
                  <?php endforeach; ?>
                <?php endif; ?>
              </div>
            </div>

            <div class="d-flex gap-2">
              <button type="submit" class="btn btn-primary"><?php echo $editPackId > 0 ? 'Save Changes' : 'Create Pack'; ?></button>
              <?php if ($editPackId > 0): ?>
                <a href="premium_packs.php" class="btn btn-outline-secondary">Cancel</a>
              <?php endif; ?>
            </div>
          </form>
        </div>
      </div>
    </div>

    <div class="col-lg-7">
      <div class="card">
        <div class="card-header">Existing Packs</div>
        <div class="card-body p-0">
          <table class="table table-striped table-bordered mb-0">
            <thead>
              <tr>
                <th>ID</th>
                <th>Name</th>
                <th>Price</th>
                <th>Items</th>
                <th>Status</th>
                <th>Revenue</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              <?php if (empty($packs)): ?>
                <tr>
                  <td colspan="7" class="text-center text-muted">No premium packs created yet.</td>
                </tr>
              <?php else: ?>
                <?php foreach ($packs as $pack): ?>
                  <tr>
                    <td><?php echo (int)$pack['id']; ?></td>
                    <td>
                      <strong><?php echo htmlspecialchars((string)$pack['name']); ?></strong>
                      <?php if (!empty($pack['description'])): ?>
                        <br><small class="text-muted"><?php echo htmlspecialchars(substr((string)$pack['description'], 0, 120)); ?></small>
                      <?php endif; ?>
                    </td>
                    <td><?php echo (int)$pack['price_points']; ?> pts</td>
                    <td><?php echo (int)$pack['item_count']; ?></td>
                    <td>
                      <?php if (!empty($pack['is_active'])): ?>
                        <span class="badge bg-success">Active</span>
                      <?php else: ?>
                        <span class="badge bg-secondary">Inactive</span>
                      <?php endif; ?>
                    </td>
                    <td>
                      <div><?php echo (int)$pack['unlock_count']; ?> unlocks</div>
                      <small class="text-muted"><?php echo (int)$pack['revenue_points']; ?> pts</small>
                    </td>
                    <td style="white-space:nowrap;">
                      <a href="premium_packs.php?edit=<?php echo (int)$pack['id']; ?>" class="btn btn-sm btn-primary">Edit</a>
                      <form method="POST" action="premium_packs_process.php" style="display:inline-block;" onsubmit="return confirm('Delete this premium pack?');">
                        <input type="hidden" name="csrf_token" value="<?php echo htmlspecialchars($csrf); ?>">
                        <input type="hidden" name="action" value="delete">
                        <input type="hidden" name="pack_id" value="<?php echo (int)$pack['id']; ?>">
                        <button type="submit" class="btn btn-sm btn-danger">Delete</button>
                      </form>
                    </td>
                  </tr>
                <?php endforeach; ?>
              <?php endif; ?>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  </div>
</div>

<?php include '../../includes/footer.php'; ?>
