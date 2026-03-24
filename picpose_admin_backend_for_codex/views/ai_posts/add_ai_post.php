<?php
// views/ai_posts/add_ai_post.php
session_start();
require '../../config.php';
if (!isset($_SESSION['admin'])) { header('Location: ../../login.php'); exit(); }

// fetch categories
$catRes = $conn->query("SELECT id, name FROM categories ORDER BY name ASC");

// CSRF token
if (empty($_SESSION['csrf_token'])) $_SESSION['csrf_token'] = bin2hex(random_bytes(32));
$csrf = $_SESSION['csrf_token'];

include '../../includes/header.php';
?>

<div class="container">
  <h2>Add AI Prompt</h2>

  <?php if(!empty($_SESSION['message'])): ?>
    <div class="alert alert-<?php echo htmlspecialchars($_SESSION['message_type'] ?? 'info'); ?>">
      <?php echo htmlspecialchars($_SESSION['message']); unset($_SESSION['message'], $_SESSION['message_type']); ?>
    </div>
  <?php endif; ?>

  <form method="POST" action="process_ai_post.php" enctype="multipart/form-data" id="addAIForm">
    <input type="hidden" name="csrf_token" value="<?php echo htmlspecialchars($csrf); ?>">
    <input type="hidden" name="action" value="create">

    <div class="row">
      <div class="col-md-6">

        <div class="mb-3">
          <label>Title</label>
          <input type="text" name="title" id="title" class="form-control" required>
        </div>

        <div class="mb-3">
          <label>Category</label>
          <select name="category_id" id="category_id" class="form-control" required>
            <option value="">Select Category</option>
            <?php while($c = $catRes->fetch_assoc()): ?>
              <option value="<?= intval($c['id']); ?>"><?= htmlspecialchars($c['name']); ?></option>
            <?php endwhile; ?>
          </select>
        </div>

        <div class="mb-3">
          <label>Short Prompt (multi-line allowed — use steps like "Step 1", "Step 2")</label>
          <!-- changed from single-line input to textarea so admins can store newline-separated steps -->
          <textarea name="short_description" id="short_description" class="form-control" rows="4" maxlength="500" required placeholder="Step 1: ...&#10;Step 2: ..."></textarea>
          <small class="form-text text-muted">You can enter multiple lines. New lines will be preserved when saved.</small>
        </div>

        <div class="mb-3">
          <label>Full Prompt</label>
          <textarea name="prompt_text" id="prompt_text" class="form-control" rows="8" required></textarea>
        </div>

        <div class="mb-3">
          <label>Tags (use hashtags, e.g. <small>#PicPose #Men #Women</small>)</label>
          <input type="text" name="tags" id="tags" class="form-control" placeholder="#PicPose #Men #Women or comma separated">
          <small class="form-text text-muted">You can enter tags as hashtags separated by spaces (preferred) or as comma-separated values. Hashtags will be normalized and saved as comma-separated tag names (without the #) before submitting.</small>
        </div>

        <div class="mb-3">
          <label>Primary Image (required)</label>
          <input type="file" name="image1" id="image1" class="form-control" accept="image/*" required>
        </div>

        <div class="mb-3">
          <label>Secondary Image (optional)</label>
          <input type="file" name="image2" id="image2" class="form-control" accept="image/*">
        </div>

      </div>

      <div class="col-md-6">

        <div class="mb-3">
          <label>Status</label>
          <select name="status" class="form-control">
            <option value="published">Published</option>
            <option value="blocked">Blocked</option>
            <option value="draft">Draft</option>
            <option value="archived">Archived</option>
          </select>
        </div>

        <div class="mb-3">
          <label>Priority</label>
          <input type="number" name="priority" class="form-control" value="0">
        </div>

        <div class="form-check mb-3">
          <input class="form-check-input" type="checkbox" name="is_popular" id="is_popular" value="1">
          <label class="form-check-label" for="is_popular">Mark as Popular (Trending)</label>
        </div>

        <div class="form-check mb-3">
          <input class="form-check-input" type="checkbox" name="is_featured" id="is_featured" value="1">
          <label class="form-check-label" for="is_featured">Mark as Featured</label>
        </div>

        <div class="form-check mb-3">
          <input class="form-check-input" type="checkbox" name="is_premium" id="is_premium" value="1">
          <label class="form-check-label" for="is_premium">Premium Prompt</label>
        </div>

        <div id="premiumOptions" style="display:none;">
          <div class="mb-3">
            <label>Unlock Cost Points</label>
            <input type="number" min="1" name="premium_unlock_cost_points" id="premium_unlock_cost_points" class="form-control" placeholder="200">
            <small class="form-text text-muted">Default is 200 when Premium is enabled.</small>
          </div>

          <div class="mb-3">
            <label>Premium Pack (optional)</label>
            <input type="text" maxlength="40" name="premium_pack" id="premium_pack" class="form-control" placeholder="e.g. portrait_pro">
          </div>

          <div class="form-check mb-2">
            <input class="form-check-input" type="checkbox" name="is_visible_in_general_feed" id="is_visible_in_general_feed" value="1" checked>
            <label class="form-check-label" for="is_visible_in_general_feed">Visible in General Feed</label>
          </div>

          <div class="mb-2"><strong>Unlock Methods</strong></div>
          <div class="form-check mb-1">
            <input class="form-check-input" type="checkbox" name="credit_unlock_enabled" id="credit_unlock_enabled" value="1" checked>
            <label class="form-check-label" for="credit_unlock_enabled">Credits Unlock</label>
          </div>
          <div class="form-check mb-1">
            <input class="form-check-input" type="checkbox" name="reward_unlock_enabled" id="reward_unlock_enabled" value="1">
            <label class="form-check-label" for="reward_unlock_enabled">Rewarded Ad Unlock</label>
          </div>
          <div class="form-check mb-1">
            <input class="form-check-input" type="checkbox" name="subscriber_unlock_enabled" id="subscriber_unlock_enabled" value="1">
            <label class="form-check-label" for="subscriber_unlock_enabled">Subscriber Access</label>
          </div>
          <div class="form-check mb-3">
            <input class="form-check-input" type="checkbox" name="token_unlock_enabled" id="token_unlock_enabled" value="1">
            <label class="form-check-label" for="token_unlock_enabled">Token Unlock (enable only if token flow is live)</label>
          </div>
        </div>

        <div class="mb-3">
          <label>External ID (optional)</label>
          <input type="text" name="external_id" class="form-control">
        </div>

        <div class="mb-3">
          <button type="submit" class="btn btn-success w-100">Save AI Prompt</button>
        </div>

      </div>
    </div>
  </form>
</div>

<script>
document.addEventListener('DOMContentLoaded', function() {
  if (window.CKEDITOR) {
    try {
      if (CKEDITOR.instances['prompt_text']) CKEDITOR.instances['prompt_text'].destroy(true);
      CKEDITOR.replace('prompt_text', { height: 300, removePlugins: 'elementspath', resize_enabled: false });
    } catch(e){ console.warn(e); }
  }

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
  var visibleInFeedEl = document.getElementById('is_visible_in_general_feed');
  var creditUnlockEl = document.getElementById('credit_unlock_enabled');
  var rewardUnlockEl = document.getElementById('reward_unlock_enabled');
  var tokenUnlockEl = document.getElementById('token_unlock_enabled');
  var subscriberUnlockEl = document.getElementById('subscriber_unlock_enabled');
  var premiumPackEl = document.getElementById('premium_pack');

  function togglePremiumOptions() {
    if (!premiumCheckbox || !premiumOptions) return;
    var enabled = !!premiumCheckbox.checked;
    premiumOptions.style.display = enabled ? '' : 'none';
    if (enabled && premiumCostEl) {
      var current = parseInt((premiumCostEl.value || '').trim(), 10);
      if (!premiumCostEl.value || isNaN(current) || current <= 0) {
        premiumCostEl.value = '200';
      }

      // Keep at least one direct unlock path by default when turning on premium.
      if (creditUnlockEl && rewardUnlockEl && tokenUnlockEl && subscriberUnlockEl) {
        var anyDirect = creditUnlockEl.checked || rewardUnlockEl.checked || tokenUnlockEl.checked || subscriberUnlockEl.checked;
        if (!anyDirect) creditUnlockEl.checked = true;
      }
      if (visibleInFeedEl && !visibleInFeedEl.checked) {
        visibleInFeedEl.checked = true;
      }
    }
  }

  if (premiumCheckbox) {
    premiumCheckbox.addEventListener('change', togglePremiumOptions);
    togglePremiumOptions();
  }

  document.getElementById('addAIForm').addEventListener('submit', function(e) {
    if (window.CKEDITOR && CKEDITOR.instances['prompt_text']) CKEDITOR.instances['prompt_text'].updateElement();

    const title = document.getElementById('title').value.trim();
    const cat = document.getElementById('category_id').value;
    // allow short description to contain newlines; we still trim leading/trailing whitespace
    const shortDesc = document.getElementById('short_description').value.trim();
    const promptVal = document.getElementById('prompt_text').value.trim();
    if (!title || !cat || !shortDesc || !promptVal) {
      alert('Please fill required fields.');
      e.preventDefault(); return false;
    }

    const havePrimary = document.getElementById('image1').files.length > 0;
    if (!havePrimary) { alert('Primary image required.'); e.preventDefault(); return false; }

    // Process tags: allow hashtags like "#PicPose #Men", or comma separated "Picpose, Men"
    var tagsEl = document.getElementById('tags');
    if (tagsEl) {
      var normalized = normalizeTagsInput(tagsEl.value);
      // Set the tags input to comma-separated list (without #) so backend receives old expected format
      tagsEl.value = normalized.join(',');
    }

    if (premiumCheckbox && premiumCheckbox.checked && premiumCostEl) {
      var premiumCost = parseInt((premiumCostEl.value || '').trim(), 10);
      if (!premiumCostEl.value || isNaN(premiumCost) || premiumCost <= 0) {
        premiumCostEl.value = '200';
      }

      var anyDirectMethod = (creditUnlockEl && creditUnlockEl.checked)
        || (rewardUnlockEl && rewardUnlockEl.checked)
        || (tokenUnlockEl && tokenUnlockEl.checked)
        || (subscriberUnlockEl && subscriberUnlockEl.checked);
      var hasPack = premiumPackEl && premiumPackEl.value && premiumPackEl.value.trim() !== '';

      // Pack-only prompts should not leak into general browsing.
      if (!anyDirectMethod && hasPack && visibleInFeedEl) {
        visibleInFeedEl.checked = false;
      }

      // Avoid creating dead-end premium prompts with no unlock path.
      if (!anyDirectMethod && !hasPack && creditUnlockEl) {
        creditUnlockEl.checked = true;
      }
    }

    return true;
  });
});
</script>

<?php include '../../includes/footer.php'; ?>
