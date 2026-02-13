<?php
// views/tips/manage_tips.php
session_start();
require '../../config.php';
if (!isset($_SESSION['admin'])) { header('Location: ../../login.php'); exit(); }

include '../../includes/header.php';

// flash messages
function flash() {
    if (!empty($_SESSION['message'])) {
        $html = '<div class="alert alert-'.htmlspecialchars($_SESSION['message_type'] ?? 'info').'">';
        $html .= htmlspecialchars($_SESSION['message']);
        $html .= '</div>';
        unset($_SESSION['message'], $_SESSION['message_type']);
        echo $html;
    }
}

// fetch tips
$res = $conn->query("SELECT * FROM daily_tips ORDER BY display_order ASC, created_at DESC");
$tips = [];
while ($r = $res->fetch_assoc()) $tips[] = $r;
?>

<div class="container">
  <div class="d-flex justify-content-between align-items-center mb-3">
    <h2>Daily Tips</h2>
    <div>
      <button class="btn btn-success" id="addTipBtn">Add Tip</button>
    </div>
  </div>

  <?php flash(); ?>

  <table class="table table-bordered align-middle">
    <thead>
      <tr>
        <th style="width:80px;">#</th>
        <th>Tip Text</th>
        <th style="width:120px;">Order</th>
        <th style="width:120px;">Active</th>
        <th style="width:200px;">Actions</th>
      </tr>
    </thead>
    <tbody>
      <?php if (empty($tips)): ?>
        <tr><td colspan="5" class="text-center">No tips yet. Add one.</td></tr>
      <?php else: ?>
        <?php foreach ($tips as $t): ?>
          <tr data-tip='<?= htmlspecialchars(json_encode($t), ENT_QUOTES); ?>'>
            <td><?= (int)$t['id']; ?></td>
            <td><?= nl2br(htmlspecialchars($t['tip_text'])); ?></td>
            <td><?= (int)$t['display_order']; ?></td>
            <td><?= $t['is_active'] ? '<span class="badge bg-success">Active</span>' : '<span class="badge bg-secondary">Inactive</span>'; ?></td>
            <td>
              <button class="btn btn-sm btn-primary editTipBtn">Edit</button>
              <form method="POST" action="../../process_tip.php" style="display:inline-block;">
                <input type="hidden" name="action" value="delete">
                <input type="hidden" name="tip_id" value="<?= (int)$t['id']; ?>">
                <button class="btn btn-sm btn-danger" onclick="return confirm('Delete this tip?')">Delete</button>
              </form>
            </td>
          </tr>
        <?php endforeach; ?>
      <?php endif; ?>
    </tbody>
  </table>
</div>

<!-- Add/Edit Modal -->
<div class="modal fade" id="tipModal" tabindex="-1" aria-hidden="true">
  <div class="modal-dialog modal-lg modal-dialog-centered">
    <div class="modal-content">
      <form id="tipForm" method="POST" action="../../process_tip.php">
        <input type="hidden" name="action" id="tip_action" value="create">
        <input type="hidden" name="tip_id" id="tip_id" value="">
        <div class="modal-header">
          <h5 class="modal-title" id="tipModalTitle">Add Tip</h5>
          <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
        </div>
        <div class="modal-body">
          <div class="mb-3">
            <label>Tip Text</label>
            <textarea name="tip_text" id="tip_text" class="form-control" rows="4" required></textarea>
          </div>
          <div class="row">
            <div class="col-md-6 mb-3">
              <label>Display Order (smaller shows first)</label>
              <input type="number" name="display_order" id="display_order" class="form-control" value="0">
            </div>
            <div class="col-md-6 mb-3">
              <label>Active</label>
              <select name="is_active" id="is_active" class="form-control">
                <option value="1">Active</option>
                <option value="0">Inactive</option>
              </select>
            </div>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn btn-secondary" type="button" data-bs-dismiss="modal">Cancel</button>
          <button class="btn btn-primary" type="submit">Save Tip</button>
        </div>
      </form>
    </div>
  </div>
</div>

<script>
// Wait for full window load so Bootstrap's JS (loaded in footer.php) is available.
window.addEventListener('load', function () {
  // Defensive check
  if (typeof bootstrap === 'undefined' || !bootstrap.Modal) {
    console.error('Bootstrap JS not available. Ensure bootstrap.bundle.min.js is included in your footer.');
    return;
  }

  var tipModalEl = document.getElementById('tipModal');
  var tipModal = bootstrap.Modal.getOrCreateInstance(tipModalEl);

  // Utility to open modal for "Add"
  function openAddModal() {
    document.getElementById('tipModalTitle').textContent = 'Add Tip';
    document.getElementById('tip_action').value = 'create';
    document.getElementById('tip_id').value = '';
    document.getElementById('tip_text').value = '';
    document.getElementById('display_order').value = '0';
    document.getElementById('is_active').value = '1';
    tipModal.show();
  }

  // Utility to open modal for "Edit" with data
  function openEditModal(tip) {
    document.getElementById('tipModalTitle').textContent = 'Edit Tip #' + tip.id;
    document.getElementById('tip_action').value = 'update';
    document.getElementById('tip_id').value = tip.id;
    document.getElementById('tip_text').value = tip.tip_text || '';
    document.getElementById('display_order').value = tip.display_order || 0;
    document.getElementById('is_active').value = tip.is_active ? '1' : '0';
    tipModal.show();
  }

  // Delegated click handler for add/edit/remove
  document.body.addEventListener('click', function (ev) {
    var t = ev.target;

    // Add Tip button
    if (t.matches('#addTipBtn') || t.closest && t.closest('#addTipBtn')) {
      ev.preventDefault();
      openAddModal();
      return;
    }

    // Edit button (could be inner element)
    var editBtn = t.matches('.editTipBtn') ? t : t.closest && t.closest('.editTipBtn');
    if (editBtn) {
      ev.preventDefault();
      var tr = editBtn.closest('tr');
      if (!tr) return;
      var data = tr.getAttribute('data-tip');
      if (!data) return;
      try {
        var tip = JSON.parse(data);
        openEditModal(tip);
      } catch (e) {
        console.error('Failed to parse tip data', e, data);
      }
      return;
    }

    // Mark remove is handled by the delete form native submit (no JS required)
  });

  // Optional: Clean up modal when hidden
  tipModalEl.addEventListener('hidden.bs.modal', function () {
    // clear values to avoid stale content
    document.getElementById('tip_id').value = '';
    document.getElementById('tip_text').value = '';
    document.getElementById('display_order').value = '0';
    document.getElementById('is_active').value = '1';
  });
});
</script>

<?php include '../../includes/footer.php'; ?>
