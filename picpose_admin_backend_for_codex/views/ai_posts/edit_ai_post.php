<?php
// views/ai_posts/edit_ai_post.php
session_start();
require '../../config.php';
if (!isset($_SESSION['admin'])) { header('Location: ../../login.php'); exit(); }

// validate id
$id = intval($_GET['id'] ?? 0);
if ($id <= 0) {
    $_SESSION['message'] = 'Invalid post id.';
    $_SESSION['message_type'] = 'danger';
    header('Location: manage_ai_posts.php');
    exit;
}

// Fetch post from ai_posts
$post = null;
$sql = "SELECT id, title, category_id, short_description, prompt_text, tags, 
               image_url1, image_url2, status, priority, is_popular, is_featured, external_id, created_at,
               tier, premium_unlock_cost_points, premium_pack
        FROM ai_posts
        WHERE id = ? LIMIT 1";

if ($stmt = $conn->prepare($sql)) {
    $stmt->bind_param('i', $id);
    if ($stmt->execute()) {
        $res = $stmt->get_result();
        $post = $res ? $res->fetch_assoc() : null;
    } else {
        error_log("edit_ai_post: execute failed: " . $stmt->error);
    }
    $stmt->close();
}

if (!$post) {
    $_SESSION['message'] = 'AI Prompt not found.';
    $_SESSION['message_type'] = 'warning';
    header('Location: manage_ai_posts.php');
    exit;
}

// fetch categories
$catRes = $conn->query("SELECT id, name FROM categories ORDER BY name ASC");

// prepare tags string
$tagsArray = [];
if (!empty($post['tags'])) {
    $decoded = json_decode($post['tags'], true);
    if (json_last_error() === JSON_ERROR_NONE && is_array($decoded)) {
        $tagsArray = $decoded;
    } else {
        // If tags stored as comma-separated string, split that.
        $tagsArray = array_filter(array_map('trim', explode(',', $post['tags'])));
    }
}
// For UX, display tags as hashtags (preferred). We'll convert back to comma-separated on submit in JS.
$tagsDisplay = '';
if (!empty($tagsArray)) {
    $tmp = array_map(function($t){
        $t = trim((string)$t);
        if ($t === '') return '';
        $t = ltrim($t, '#');
        return '#' . $t;
    }, $tagsArray);
    $tmp = array_filter($tmp);
    $tagsDisplay = implode(' ', $tmp); // "#Tag1 #Tag2"
}

// helpers
function normalize_image_path($path) {
    $path = trim((string)$path);
    if ($path === '') return '';
    if (stripos($path, 'http://') === 0 || stripos($path, 'https://') === 0) return $path;
    if (strpos($path, '/') === 0) return '/' . ltrim($path, '/');
    if (stripos($path, 'uploads/') !== false) return '/' . ltrim($path, '/');
    return '/uploads/' . ltrim($path, '/');
}
function collect_images_from_post($postRow) {
    $images = [];
    foreach (['image_url1','image_url2'] as $col) {
        if (!empty($postRow[$col])) $images[] = $postRow[$col];
    }
    $images = array_values(array_unique(array_filter($images)));
    return array_map('normalize_image_path', $images);
}
$images = collect_images_from_post($post);

// CSRF token
if (empty($_SESSION['csrf_token'])) $_SESSION['csrf_token'] = bin2hex(random_bytes(32));
$csrf = $_SESSION['csrf_token'];

include '../../includes/header.php';
?>

<div class="container">
  <div class="d-flex justify-content-between align-items-center mb-3">
    <h2>Edit AI Prompt</h2>
    <a href="manage_ai_posts.php" class="btn btn-outline-secondary">← Back</a>
  </div>

  <?php if(!empty($_SESSION['message'])): ?>
    <div class="alert alert-<?php echo htmlspecialchars($_SESSION['message_type'] ?? 'info'); ?>">
      <?php echo htmlspecialchars($_SESSION['message']); unset($_SESSION['message'], $_SESSION['message_type']); ?>
    </div>
  <?php endif; ?>

  <form method="POST" action="process_ai_post.php" enctype="multipart/form-data" id="editAiForm">
    <input type="hidden" name="csrf_token" value="<?php echo htmlspecialchars($csrf); ?>">
    <input type="hidden" name="action" value="update">
    <input type="hidden" name="id" value="<?php echo intval($post['id']); ?>">

    <div class="row">
      <div class="col-md-6">
        <div class="mb-3">
          <label>Title</label>
          <input type="text" name="title" class="form-control" value="<?php echo htmlspecialchars($post['title'] ?? ''); ?>" required>
        </div>

        <div class="mb-3">
          <label>Category</label>
          <select name="category_id" class="form-control" required>
            <option value="">Select Category</option>
            <?php
            if ($catRes && method_exists($catRes,'data_seek')) $catRes->data_seek(0);
            while ($c = ($catRes ? $catRes->fetch_assoc() : null)):
              if (!$c) break;
            ?>
              <option value="<?= intval($c['id']); ?>" <?= (intval($c['id']) === intval($post['category_id'] ?? 0)) ? 'selected' : ''; ?>>
                <?= htmlspecialchars($c['name']); ?>
              </option>
            <?php endwhile; ?>
          </select>
        </div>

        <div class="mb-3">
          <label>Short Prompt (multi-line allowed — use steps like "Step 1", "Step 2")</label>
          <!-- changed from single-line input to textarea so existing newlines are preserved and editors can enter steps -->
          <textarea id="short_description" name="short_description" class="form-control" rows="4" maxlength="500" placeholder="Step 1: ...&#10;Step 2: ..."><?php echo htmlspecialchars($post['short_description'] ?? ''); ?></textarea>
          <small class="form-text text-muted">You can enter multiple lines. New lines will be preserved when saving.</small>
        </div>

        <div class="mb-3">
          <label>Full Prompt (rich text)</label>
          <textarea name="prompt_text" id="prompt_text" class="form-control" rows="8"><?php echo htmlspecialchars($post['prompt_text'] ?? ''); ?></textarea>
        </div>

        <div class="mb-3">
          <label>Tags (use hashtags, e.g. <small>#PicPose #Men #Women</small>)</label>
          <input type="text" name="tags" class="form-control" value="<?php echo htmlspecialchars($tagsDisplay); ?>" placeholder="#PicPose #Men #Women or comma separated">
          <small class="form-text text-muted">You can enter tags as hashtags separated by spaces (preferred) or as comma-separated values. Hashtags will be normalized and saved as comma-separated tag names (without the #) before submitting.</small>
        </div>

        <div class="mb-3">
          <label>Replace Primary Image (optional)</label>
          <input type="file" name="image1" class="form-control" accept="image/*">
        </div>

        <div class="mb-3">
          <label>Replace Secondary Image (optional)</label>
          <input type="file" name="image2" class="form-control" accept="image/*">
        </div>

        <!-- existing images preview + remove -->
        <div class="mb-3">
          <label>Existing Images</label>
          <div class="row g-2" id="existingImagesRow">
            <?php if (empty($images)): ?>
              <div class="col-12"><div class="alert alert-secondary">No images uploaded for this post.</div></div>
            <?php else: foreach ($images as $idx => $img): $safe = htmlspecialchars($img, ENT_QUOTES); ?>
              <div class="col-3 mb-2 existing-image-item" data-img="<?= $safe; ?>">
                <div class="card">
                  <img src="<?= $safe; ?>" class="card-img-top existing-image-thumb open-image" style="height:120px;object-fit:cover;cursor:pointer;" data-img="<?= $safe; ?>">
                  <div class="card-body p-2 text-center">
                    <small class="text-muted">Image <?= $idx+1; ?></small>
                    <div class="mt-1 d-flex justify-content-center gap-1">
                      <button type="button" class="btn btn-sm btn-light open-image" data-img="<?= $safe; ?>">Preview</button>
                      <a href="<?= $safe; ?>" target="_blank" class="btn btn-sm btn-outline-secondary">Open</a>
                      <button type="button" class="btn btn-sm btn-danger mark-remove" data-img="<?= $safe; ?>">Remove</button>
                    </div>
                  </div>
                </div>
                <input type="hidden" name="existing_images[]" value="<?= $safe; ?>">
              </div>
            <?php endforeach; endif; ?>
          </div>
          <small class="text-muted">Click Remove to mark image for deletion when saving.</small>
        </div>

      </div>

      <div class="col-md-6">
        <div class="mb-3">
          <label>Status</label>
          <select name="status" class="form-control">
            <option value="published" <?php echo ($post['status'] ?? '')=='published' ? 'selected' : ''; ?>>Published</option>
            <option value="blocked" <?php echo ($post['status'] ?? '')=='blocked' ? 'selected' : ''; ?>>Blocked</option>
            <option value="draft" <?php echo ($post['status'] ?? '')=='draft' ? 'selected' : ''; ?>>Draft</option>
            <option value="archived" <?php echo ($post['status'] ?? '')=='archived' ? 'selected' : ''; ?>>Archived</option>
          </select>
        </div>

        <div class="mb-3">
          <label>Priority</label>
          <input type="number" name="priority" class="form-control" value="<?php echo intval($post['priority'] ?? 0); ?>">
        </div>

        <div class="form-check mb-3">
          <input class="form-check-input" type="checkbox" name="is_popular" value="1" id="is_popular" <?php echo !empty($post['is_popular']) ? 'checked' : ''; ?>>
          <label class="form-check-label" for="is_popular">Mark as Popular (Trending)</label>
        </div>

        <div class="form-check mb-3">
          <input class="form-check-input" type="checkbox" name="is_featured" value="1" id="is_featured" <?php echo !empty($post['is_featured']) ? 'checked' : ''; ?>>
          <label class="form-check-label" for="is_featured">Mark as Featured</label>
        </div>

        <div class="form-check mb-3">
          <input class="form-check-input" type="checkbox" name="is_premium" value="1" id="is_premium" <?php echo (strtoupper((string)($post['tier'] ?? 'FREE')) === 'PREMIUM') ? 'checked' : ''; ?>>
          <label class="form-check-label" for="is_premium">Premium Prompt</label>
        </div>

        <div id="premiumOptions" style="<?php echo (strtoupper((string)($post['tier'] ?? 'FREE')) === 'PREMIUM') ? '' : 'display:none;'; ?>">
          <div class="mb-3">
            <label>Unlock Cost Points</label>
            <input type="number" min="1" name="premium_unlock_cost_points" id="premium_unlock_cost_points" class="form-control" value="<?php echo intval($post['premium_unlock_cost_points'] ?? 0); ?>" placeholder="200">
            <small class="form-text text-muted">Default is 200 when Premium is enabled.</small>
          </div>

          <div class="mb-3">
            <label>Premium Pack (optional)</label>
            <input type="text" maxlength="40" name="premium_pack" id="premium_pack" class="form-control" value="<?php echo htmlspecialchars($post['premium_pack'] ?? ''); ?>" placeholder="e.g. portrait_pro">
          </div>
        </div>

        <div class="mb-3">
          <label>External ID</label>
          <input type="text" name="external_id" class="form-control" value="<?php echo htmlspecialchars($post['external_id'] ?? ''); ?>">
        </div>

        <div class="mb-3">
          <button class="btn btn-primary w-100" type="submit">Save Changes</button>
        </div>
      </div>
    </div>
  </form>
</div>

<!-- Image Preview Modal -->
<div class="modal fade" id="imagePreviewModal" tabindex="-1" aria-hidden="true">
  <div class="modal-dialog modal-dialog-centered modal-lg">
    <div class="modal-content">
      <div class="modal-body text-center">
        <img id="imagePreviewModalImg" src="" alt="Preview" style="max-width:100%; max-height:70vh; border-radius:6px; object-fit:contain;">
      </div>
      <div class="modal-footer">
        <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Close</button>
      </div>
    </div>
  </div>
</div>

<script>
document.addEventListener('DOMContentLoaded', function() {
  if (window.CKEDITOR) {
    try { if (CKEDITOR.instances['prompt_text']) CKEDITOR.instances['prompt_text'].destroy(true); } catch(e){}
    try { CKEDITOR.replace('prompt_text', { height: 260, removePlugins: 'elementspath', resize_enabled: false }); } catch(e) {}
  }

  function openPreview(imgUrl) {
    if (!imgUrl) return;
    var modal = bootstrap.Modal.getOrCreateInstance(document.getElementById('imagePreviewModal'));
    var imgEl = document.getElementById('imagePreviewModalImg');
    imgEl.src = imgUrl;
    modal.show();
  }

  document.querySelectorAll('.open-image').forEach(function(btn) {
    btn.addEventListener('click', function() {
      var img = this.getAttribute('data-img') || this.dataset.img;
      if (!img) return;
      openPreview(img);
    });
  });

  document.querySelectorAll('.mark-remove').forEach(function(btn) {
    btn.addEventListener('click', function() {
      var img = this.getAttribute('data-img') || this.dataset.img;
      if (!img) return;
      if (!confirm('Mark this image for removal? It will be removed when you save the post.')) return;
      var parent = this.closest('.existing-image-item');
      if (parent) parent.style.display = 'none';
      var form = document.getElementById('editAiForm');
      if (!form) return;
      var existing = Array.from(form.querySelectorAll('input[name="remove_images[]"]')).find(function(n){ return n.value === img; });
      if (!existing) {
        var input = document.createElement('input');
        input.type = 'hidden';
        input.name = 'remove_images[]';
        input.value = img;
        form.appendChild(input);
      }
      var keepInputs = form.querySelectorAll('input[name="existing_images[]"]');
      keepInputs.forEach(function(k){ if (k.value === img) k.remove(); });
    });
  });

  // Tag normalization function (same behavior as on create page)
  function normalizeTagsInput(raw) {
    if (!raw) return [];
    raw = raw.trim();

    // First try to capture hashtags like #Tag
    var tagSet = [];
    var hashRe = /#([^\s#,]+)/g;
    var m;
    while ((m = hashRe.exec(raw)) !== null) {
      if (m[1]) tagSet.push(m[1]);
    }

    // If no hashtags found, check for comma-separated values
    if (tagSet.length === 0) {
      if (raw.indexOf(',') !== -1) {
        tagSet = raw.split(',').map(function(s){ return s.trim(); }).filter(Boolean);
      } else {
        // fallback: split by whitespace (space-separated words)
        tagSet = raw.split(/\s+/).map(function(s){ return s.trim().replace(/^#/, ''); }).filter(Boolean);
      }
    }

    // Clean tags: remove surrounding punctuation, keep alphanum, dash, underscore
    tagSet = tagSet.map(function(t){
      // remove leading/trailing non-word chars
      return t.replace(/^[^\w-]+|[^\w-]+$/g, '');
    }).filter(Boolean);

    // Remove duplicates while preserving order
    var seen = {};
    var out = [];
    tagSet.forEach(function(t){
      var key = t.toLowerCase();
      if (!seen[key]) { seen[key] = true; out.push(t); }
    });
    return out;
  }

  var premiumCheckbox = document.getElementById('is_premium');
  var premiumOptions = document.getElementById('premiumOptions');
  var premiumCostEl = document.getElementById('premium_unlock_cost_points');

  function togglePremiumOptions() {
    if (!premiumCheckbox || !premiumOptions) return;
    var enabled = !!premiumCheckbox.checked;
    premiumOptions.style.display = enabled ? '' : 'none';
    if (enabled && premiumCostEl) {
      var current = parseInt((premiumCostEl.value || '').trim(), 10);
      if (!premiumCostEl.value || isNaN(current) || current <= 0) {
        premiumCostEl.value = '200';
      }
    }
  }

  if (premiumCheckbox) {
    premiumCheckbox.addEventListener('change', togglePremiumOptions);
    togglePremiumOptions();
  }

  var formEl = document.getElementById('editAiForm');
  if (formEl) {
    formEl.addEventListener('submit', function(e) {
      if (window.CKEDITOR && CKEDITOR.instances['prompt_text']) {
        try { CKEDITOR.instances['prompt_text'].updateElement(); } catch(e) {}
      }

      // Basic required field checks
      var title = formEl.querySelector('input[name="title"]').value.trim();
      var cat = formEl.querySelector('select[name="category_id"]').value;
      // short description: trim leading/trailing but preserve internal newlines
      var shortDesc = formEl.querySelector('textarea[name="short_description"]').value.trim();
      if (!title) { alert('Title is required'); e.preventDefault(); return false; }
      if (!cat) { alert('Category is required'); e.preventDefault(); return false; }
      if (!shortDesc) { alert('Short prompt is required'); e.preventDefault(); return false; }

      // Normalize tags input: accepts "#Tag1 #Tag2" or "Tag1, Tag2" or "Tag1 Tag2"
      var tagsEl = formEl.querySelector('input[name="tags"]');
      if (tagsEl) {
        var normalized = normalizeTagsInput(tagsEl.value);
        // convert to comma-separated values without '#', which matches backend's expected format
        tagsEl.value = normalized.join(',');
      }

      if (premiumCheckbox && premiumCheckbox.checked && premiumCostEl) {
        var premiumCost = parseInt((premiumCostEl.value || '').trim(), 10);
        if (!premiumCostEl.value || isNaN(premiumCost) || premiumCost <= 0) {
          premiumCostEl.value = '200';
        }
      }

      return true;
    });
  }
});
</script>

<?php include '../../includes/footer.php'; ?>
