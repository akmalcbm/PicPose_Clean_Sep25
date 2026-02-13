<?php
session_start();
require '../../config.php';

if (!isset($_SESSION['admin'])) {
    header('Location: ../../login.php');
    exit();
}

if (!isset($conn) || !$conn) {
    $_SESSION['message'] = 'Database connection not available.';
    $_SESSION['message_type'] = 'danger';
    header('Location: manage_guides.php');
    exit();
}

$id = intval($_GET['id'] ?? 0);
if ($id <= 0) {
    $_SESSION['message'] = 'Invalid guide id.';
    $_SESSION['message_type'] = 'danger';
    header('Location: manage_guides.php');
    exit();
}

$guideTableName = 'guide_posts';
$existingCols = [];
$colsRes = @$conn->query("SHOW COLUMNS FROM `" . $conn->real_escape_string($guideTableName) . "`");
if ($colsRes) {
    while ($c = $colsRes->fetch_assoc()) {
        $existingCols[] = $c['Field'];
    }
}

$expectedCols = [
    'id','title','category_id','short_description','content','tags',
    'image_url1','image_url2','image_url3','images','videos','content_blocks',
    'status','priority','is_popular','is_featured','external_id','created_at','updated_at'
];

$selectCols = [];
foreach ($expectedCols as $col) {
    if (in_array($col, $existingCols, true)) $selectCols[] = "`$col`";
}
if (!in_array('`id`', $selectCols, true)) {
    array_unshift($selectCols, '`id`');
}

if (empty($selectCols)) {
    $_SESSION['message'] = 'Guide table not found or has no columns.';
    $_SESSION['message_type'] = 'danger';
    header('Location: manage_guides.php');
    exit();
}

$selectSql = implode(', ', $selectCols);
$sql = "SELECT {$selectSql} FROM `" . $conn->real_escape_string($guideTableName) . "` WHERE id = ? LIMIT 1";

$post = null;
if ($stmt = $conn->prepare($sql)) {
    $stmt->bind_param('i', $id);
    if ($stmt->execute()) {
        $res = $stmt->get_result();
        $post = $res ? $res->fetch_assoc() : null;
    } else {
        error_log("edit_guide_post: execute failed: " . $stmt->error . " SQL: " . $sql);
    }
    $stmt->close();
} else {
    error_log("edit_guide_post: prepare failed: " . $conn->error . " SQL: " . $sql);
}

if (!$post) {
    $_SESSION['message'] = 'Guide not found.';
    $_SESSION['message_type'] = 'warning';
    header('Location: manage_guides.php');
    exit();
}

foreach ($expectedCols as $col) {
    if (!array_key_exists($col, $post)) $post[$col] = null;
}

function normalize_media_path($path) {
    $path = trim((string)$path);
    if ($path === '') return '';
    if (stripos($path, 'http://') === 0 || stripos($path, 'https://') === 0) return $path;
    if (strpos($path, '/') === 0) return '/' . ltrim($path, '/');
    if (stripos($path, 'uploads/') !== false) return '/' . ltrim($path, '/');
    return '/uploads/' . ltrim($path, '/');
}

$tagsArray = [];
if (!empty($post['tags'])) {
    $decoded = json_decode($post['tags'], true);
    if (json_last_error() === JSON_ERROR_NONE && is_array($decoded)) {
        $tagsArray = $decoded;
    } else {
        $tagsArray = array_filter(array_map('trim', explode(',', (string)$post['tags'])));
    }
}

$tagsDisplay = '';
if (!empty($tagsArray)) {
    $tmp = array_map(function($t){
        $t = trim((string)$t);
        if ($t === '') return '';
        $t = ltrim($t, '#');
        return '#' . $t;
    }, $tagsArray);
    $tagsDisplay = implode(' ', array_filter($tmp));
}

$images = [];
foreach (['image_url1','image_url2','image_url3'] as $col) {
    if (!empty($post[$col])) $images[] = $post[$col];
}
if (!empty($post['images'])) {
    $dec = json_decode($post['images'], true);
    if (json_last_error() === JSON_ERROR_NONE && is_array($dec)) {
        foreach ($dec as $it) if ($it) $images[] = $it;
    } else {
        foreach (array_filter(array_map('trim', explode(',', (string)$post['images']))) as $it) if ($it) $images[] = $it;
    }
}
$images = array_map('normalize_media_path', array_values(array_unique(array_filter($images))));

$videos = [];
if (!empty($post['videos'])) {
    $dec = json_decode($post['videos'], true);
    if (json_last_error() === JSON_ERROR_NONE && is_array($dec)) {
        foreach ($dec as $it) if ($it) $videos[] = $it;
    } else {
        foreach (array_filter(array_map('trim', explode(',', (string)$post['videos']))) as $it) if ($it) $videos[] = $it;
    }
}
$videos = array_map('normalize_media_path', array_values(array_unique(array_filter($videos))));

$content_raw = isset($post['content']) ? html_entity_decode((string)$post['content']) : '';
$content_blocks_initial = [];
if (!empty($post['content_blocks'])) {
    $decoded_blocks = json_decode((string)$post['content_blocks'], true);
    if (json_last_error() === JSON_ERROR_NONE && is_array($decoded_blocks)) {
        $content_blocks_initial = $decoded_blocks;
    }
}

$catRes = false;
if ($conn->query("SHOW TABLES LIKE 'categories'")->num_rows > 0) {
    $catRes = $conn->query("SELECT id, name FROM categories ORDER BY name ASC");
}

if (empty($_SESSION['csrf_token'])) {
    try {
        $_SESSION['csrf_token'] = bin2hex(random_bytes(32));
    } catch (Exception $e) {
        $_SESSION['csrf_token'] = bin2hex(openssl_random_pseudo_bytes(32));
    }
}
$csrf = $_SESSION['csrf_token'];

include '../../includes/header.php';
?>

<style>
  .guide-editor-wrap {
    max-width: 1160px;
    margin: 0 auto;
    padding-bottom: 40px;
  }
  .guide-editor-title {
    margin: 16px 0;
    font-weight: 700;
  }
  .guide-editor-section {
    margin-bottom: 18px;
    border-radius: 14px;
  }
  .guide-editor-section .card-header {
    font-weight: 600;
    background: #f8fafc;
  }
  .guide-grid-2 {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 14px;
  }
  .guide-grid-3 {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 12px;
  }
  .existing-media-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
    gap: 10px;
  }
  .existing-media-grid .card img,
  .existing-media-grid .card video {
    width: 100%;
    height: 130px;
    object-fit: cover;
  }
  .block-editor-toolbar {
    position: sticky;
    top: 10px;
    z-index: 20;
    background: #fff;
    border: 1px solid #e5e7eb;
    border-radius: 12px;
    padding: 10px;
    margin-bottom: 12px;
  }
  .block-toolbar-row {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
  }
  .guide-block-item {
    border: 1px solid #e2e8f0;
    border-radius: 12px;
    margin-bottom: 10px;
    background: #fff;
    overflow: hidden;
  }
  .guide-block-item summary {
    list-style: none;
    cursor: pointer;
    padding: 10px 12px;
    background: #f8fafc;
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 10px;
  }
  .guide-block-item summary::-webkit-details-marker { display: none; }
  .block-head {
    display: flex;
    align-items: center;
    gap: 10px;
    min-width: 0;
    flex: 1;
  }
  .block-type-badge {
    font-size: 11px;
    font-weight: 700;
    text-transform: uppercase;
    letter-spacing: .4px;
    color: #0f172a;
    background: #e2e8f0;
    border-radius: 8px;
    padding: 4px 8px;
    flex-shrink: 0;
  }
  .block-preview {
    font-size: 13px;
    color: #475569;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .block-actions {
    display: flex;
    gap: 6px;
    flex-wrap: wrap;
    justify-content: flex-end;
  }
  .block-body {
    padding: 12px;
    border-top: 1px solid #edf2f7;
  }
  .block-body textarea {
    min-height: 110px;
  }
  .block-body .block-items {
    min-height: 140px;
  }
  .guide-save-bar {
    position: sticky;
    bottom: 10px;
    z-index: 15;
    background: rgba(255,255,255,.95);
    border: 1px solid #e5e7eb;
    border-radius: 12px;
    padding: 10px;
    backdrop-filter: blur(4px);
  }
  @media (max-width: 992px) {
    .guide-grid-2,
    .guide-grid-3 {
      grid-template-columns: 1fr;
    }
    .block-actions .btn {
      padding: 2px 6px;
      font-size: 11px;
    }
  }
</style>

<div class="container-fluid guide-editor-wrap">
  <div class="d-flex justify-content-between align-items-center mb-2">
    <h2 class="guide-editor-title mb-0">Edit Guide</h2>
    <a href="manage_guides.php" class="btn btn-outline-secondary">← Back</a>
  </div>

  <?php if(!empty($_SESSION['message'])): ?>
    <div class="alert alert-<?php echo htmlspecialchars($_SESSION['message_type'] ?? 'info'); ?>">
      <?php echo htmlspecialchars($_SESSION['message']); unset($_SESSION['message'], $_SESSION['message_type']); ?>
    </div>
  <?php endif; ?>

  <form method="POST" action="process_guide_post.php" enctype="multipart/form-data" id="editGuideForm">
    <input type="hidden" name="csrf_token" value="<?php echo htmlspecialchars($csrf); ?>">
    <input type="hidden" name="action" value="update">
    <input type="hidden" name="id" value="<?php echo intval($post['id']); ?>">

    <section class="card guide-editor-section">
      <div class="card-header">Basic Info</div>
      <div class="card-body">
        <div class="guide-grid-2">
          <div class="mb-3">
            <label class="form-label">Title</label>
            <input type="text" name="title" class="form-control form-control-lg" value="<?php echo htmlspecialchars($post['title'] ?? ''); ?>" required>
          </div>
          <div class="mb-3">
            <label class="form-label">Category</label>
            <select name="category_id" class="form-control form-control-lg" required>
              <option value="">Select Category</option>
              <?php if ($catRes && method_exists($catRes,'data_seek')) $catRes->data_seek(0);
              while ($c = ($catRes ? $catRes->fetch_assoc() : null)):
                if (!$c) break; ?>
                <option value="<?= intval($c['id']); ?>" <?= intval($c['id']) === intval($post['category_id'] ?? 0) ? 'selected' : ''; ?>><?= htmlspecialchars($c['name']); ?></option>
              <?php endwhile; ?>
            </select>
          </div>
          <div class="mb-3" style="grid-column: 1 / -1;">
            <label class="form-label">Short Description</label>
            <textarea name="short_description" class="form-control" maxlength="500" rows="3"><?php echo htmlspecialchars($post['short_description'] ?? ''); ?></textarea>
          </div>
          <div class="mb-3">
            <label class="form-label">Status</label>
            <select name="status" class="form-control">
              <option value="published" <?php echo ($post['status'] ?? '')==='published' ? 'selected' : ''; ?>>Published</option>
              <option value="draft" <?php echo ($post['status'] ?? '')==='draft' ? 'selected' : ''; ?>>Draft</option>
              <option value="archived" <?php echo ($post['status'] ?? '')==='archived' ? 'selected' : ''; ?>>Archived</option>
            </select>
          </div>
          <div class="mb-3">
            <label class="form-label">Priority</label>
            <input type="number" name="priority" class="form-control" value="<?php echo intval($post['priority'] ?? 0); ?>">
          </div>
          <div class="mb-3 d-flex align-items-center gap-4">
            <div class="form-check">
              <input class="form-check-input" type="checkbox" name="is_popular" value="1" id="is_popular" <?php echo !empty($post['is_popular']) ? 'checked' : ''; ?>>
              <label class="form-check-label" for="is_popular">Mark as Popular</label>
            </div>
            <div class="form-check">
              <input class="form-check-input" type="checkbox" name="is_featured" value="1" id="is_featured" <?php echo !empty($post['is_featured']) ? 'checked' : ''; ?>>
              <label class="form-check-label" for="is_featured">Featured</label>
            </div>
          </div>
          <div class="mb-3">
            <label class="form-label">External ID</label>
            <input type="text" name="external_id" class="form-control" value="<?php echo htmlspecialchars($post['external_id'] ?? ''); ?>">
          </div>
        </div>
      </div>
    </section>

    <section class="card guide-editor-section">
      <div class="card-header">Primary Media</div>
      <div class="card-body">
        <div class="guide-grid-3">
          <div class="mb-3">
            <label class="form-label">Replace Primary Image (optional)</label>
            <input type="file" name="image1" class="form-control" accept="image/*">
          </div>
          <div class="mb-3">
            <label class="form-label">Add More Guide Images</label>
            <input type="file" name="guide_images[]" class="form-control" accept="image/*" multiple>
            <small class="text-muted">New uploads are appended.</small>
          </div>
          <div class="mb-3">
            <label class="form-label">Add More Guide Videos</label>
            <input type="file" name="guide_videos[]" class="form-control" accept="video/*" multiple>
          </div>
        </div>

        <div class="mt-3">
          <label class="form-label">Existing Images</label>
          <div class="existing-media-grid" id="existingImagesRow">
            <?php if (empty($images)): ?>
              <div class="alert alert-secondary mb-0">No images uploaded for this guide.</div>
            <?php else: foreach ($images as $idx => $img):
                $img_for_src = htmlspecialchars($img, ENT_QUOTES);
                $img_for_input = ltrim($img, '/');
            ?>
              <div class="existing-image-item" data-img="<?php echo htmlspecialchars($img_for_input, ENT_QUOTES); ?>">
                <div class="card">
                  <img src="<?php echo $img_for_src; ?>" class="existing-image-thumb" data-img="<?php echo $img_for_src; ?>" alt="Image <?php echo $idx+1; ?>">
                  <div class="card-body p-2 text-center">
                    <small class="text-muted d-block mb-1">Image <?php echo $idx+1; ?></small>
                    <div class="d-flex justify-content-center gap-1 flex-wrap">
                      <button type="button" class="btn btn-sm btn-light open-image" data-img="<?php echo $img_for_src; ?>">Preview</button>
                      <a href="<?php echo $img_for_src; ?>" target="_blank" class="btn btn-sm btn-outline-secondary">Open</a>
                      <button type="button" class="btn btn-sm btn-danger mark-remove" data-img="<?php echo htmlspecialchars($img_for_input, ENT_QUOTES); ?>">Remove</button>
                    </div>
                  </div>
                </div>
                <input type="hidden" name="existing_images[]" value="<?php echo htmlspecialchars($img_for_input, ENT_QUOTES); ?>">
              </div>
            <?php endforeach; endif; ?>
          </div>
        </div>

        <div class="mt-3">
          <label class="form-label">Existing Videos</label>
          <div class="existing-media-grid" id="existingVideosRow">
            <?php if (empty($videos)): ?>
              <div class="alert alert-secondary mb-0">No videos uploaded for this guide.</div>
            <?php else: foreach ($videos as $idx => $v):
                $v_for_src = htmlspecialchars($v, ENT_QUOTES);
                $v_for_input = ltrim($v, '/');
            ?>
              <div class="existing-video-item" data-video="<?php echo htmlspecialchars($v_for_input, ENT_QUOTES); ?>">
                <div class="card">
                  <video src="<?php echo $v_for_src; ?>" controls></video>
                  <div class="card-body p-2 text-center">
                    <small class="text-muted d-block mb-1">Video <?php echo $idx+1; ?></small>
                    <div class="d-flex justify-content-center gap-1 flex-wrap">
                      <a href="<?php echo $v_for_src; ?>" target="_blank" class="btn btn-sm btn-outline-secondary">Open</a>
                      <button type="button" class="btn btn-sm btn-danger mark-remove-video" data-video="<?php echo htmlspecialchars($v_for_input, ENT_QUOTES); ?>">Remove</button>
                    </div>
                  </div>
                </div>
                <input type="hidden" name="existing_videos[]" value="<?php echo htmlspecialchars($v_for_input, ENT_QUOTES); ?>">
              </div>
            <?php endforeach; endif; ?>
          </div>
        </div>
      </div>
    </section>

    <section class="card guide-editor-section">
      <div class="card-header">Tags</div>
      <div class="card-body">
        <label class="form-label">Tags (use hashtags)</label>
        <input type="text" name="tags" id="tags" class="form-control" value="<?php echo htmlspecialchars($tagsDisplay); ?>" placeholder="#Photography #Lighting or comma separated (photography, lighting)">
        <small class="form-text text-muted">Accepted: hashtag format, comma-separated, or space-separated. Normalized before submit.</small>
      </div>
    </section>

    <section class="card guide-editor-section">
      <div class="card-header">Long Description (HTML)</div>
      <div class="card-body">
        <textarea id="content_editor" name="content" class="form-control" rows="16"><?php echo htmlspecialchars($content_raw); ?></textarea>
        <input type="hidden" id="content_editor_hidden" name="content_hidden">
      </div>
    </section>

    <section class="card guide-editor-section">
      <div class="card-header">Structured Content Blocks</div>
      <div class="card-body">
        <input type="hidden" name="content_blocks_json" id="content_blocks_json" value="">

        <div class="block-editor-toolbar">
          <div class="block-toolbar-row mb-2">
            <button type="button" class="btn btn-sm btn-outline-primary add-block-btn" data-type="hero">Hero</button>
            <button type="button" class="btn btn-sm btn-outline-primary add-block-btn" data-type="h1">H1</button>
            <button type="button" class="btn btn-sm btn-outline-primary add-block-btn" data-type="h2">H2</button>
            <button type="button" class="btn btn-sm btn-outline-primary add-block-btn" data-type="h3">H3</button>
            <button type="button" class="btn btn-sm btn-outline-primary add-block-btn" data-type="p">Paragraph</button>
            <button type="button" class="btn btn-sm btn-outline-primary add-block-btn" data-type="image">Image</button>
            <button type="button" class="btn btn-sm btn-outline-primary add-block-btn" data-type="video">Video</button>
            <button type="button" class="btn btn-sm btn-outline-primary add-block-btn" data-type="callout">Callout</button>
            <button type="button" class="btn btn-sm btn-outline-primary add-block-btn" data-type="ol">Ordered List</button>
            <button type="button" class="btn btn-sm btn-outline-primary add-block-btn" data-type="ul">Unordered List</button>
            <button type="button" class="btn btn-sm btn-outline-primary add-block-btn" data-type="divider">Divider</button>
            <button type="button" class="btn btn-sm btn-outline-secondary" id="importFromContentBtn">Import</button>
          </div>
          <div class="block-toolbar-row">
            <button type="button" class="btn btn-sm btn-light" id="expandAllBlocks">Expand all</button>
            <button type="button" class="btn btn-sm btn-light" id="collapseAllBlocks">Collapse all</button>
          </div>
        </div>

        <div id="contentBlocksContainer"></div>
      </div>
    </section>

    <div class="guide-save-bar">
      <button class="btn btn-success w-100" type="submit">Update Guide</button>
    </div>
  </form>
</div>

<div class="modal fade" id="imagePreviewModal" tabindex="-1" aria-hidden="true">
  <div class="modal-dialog modal-dialog-centered modal-lg">
    <div class="modal-content">
      <div class="modal-header"><h6 class="modal-title">Preview</h6><button type="button" class="btn-close" data-bs-dismiss="modal"></button></div>
      <div class="modal-body text-center">
        <img id="imagePreviewModalImg" src="" alt="Preview" style="max-width:100%; max-height:70vh; border-radius:6px;">
      </div>
      <div class="modal-footer"><button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Close</button></div>
    </div>
  </div>
</div>

<script>
document.addEventListener('DOMContentLoaded', function() {
  var contentFromServer = <?php echo json_encode($content_raw); ?>;
  var contentBlocksInitial = <?php echo json_encode($content_blocks_initial, JSON_UNESCAPED_UNICODE); ?>;
  var blocksContainer = document.getElementById('contentBlocksContainer');
  var blocksJsonEl = document.getElementById('content_blocks_json');
  var blockSeq = 1;

  if (window.CKEDITOR) {
    try { if (CKEDITOR.instances['content_editor']) CKEDITOR.instances['content_editor'].destroy(true); } catch(e){}
    try {
      CKEDITOR.replace('content_editor', { height: 420, removePlugins: 'elementspath', resize_enabled: false });
      CKEDITOR.instances['content_editor'].on('instanceReady', function() {
        try { CKEDITOR.instances['content_editor'].setData(contentFromServer); } catch(e){}
      });
    } catch (e) { console.warn('CKEditor init error', e); }
  }

  document.querySelectorAll('.open-image, .existing-image-thumb').forEach(function(btn) {
    btn.addEventListener('click', function() {
      var img = this.getAttribute('data-img');
      if (!img) return;
      var modal = bootstrap.Modal.getOrCreateInstance(document.getElementById('imagePreviewModal'));
      document.getElementById('imagePreviewModalImg').src = img;
      modal.show();
    });
  });

  document.querySelectorAll('.mark-remove').forEach(function(btn) {
    btn.addEventListener('click', function() {
      var img = this.getAttribute('data-img');
      if (!img) return;
      if (!confirm('Mark this image for removal?')) return;
      var parent = this.closest('.existing-image-item');
      if (parent) parent.style.display = 'none';
      var form = document.getElementById('editGuideForm');
      if (!form) return;
      var exists = Array.from(form.querySelectorAll('input[name="remove_images[]"]')).find(function(n){ return n.value === img; });
      if (!exists) {
        var input = document.createElement('input');
        input.type='hidden'; input.name='remove_images[]'; input.value=img;
        form.appendChild(input);
      }
      form.querySelectorAll('input[name="existing_images[]"]').forEach(function(k){ if (k.value === img) k.remove(); });
    });
  });

  document.querySelectorAll('.mark-remove-video').forEach(function(btn) {
    btn.addEventListener('click', function() {
      var v = this.getAttribute('data-video');
      if (!v) return;
      if (!confirm('Mark this video for removal?')) return;
      var parent = this.closest('.existing-video-item');
      if (parent) parent.style.display = 'none';
      var form = document.getElementById('editGuideForm');
      if (!form) return;
      var exists = Array.from(form.querySelectorAll('input[name="remove_videos[]"]')).find(function(n){ return n.value === v; });
      if (!exists) {
        var input = document.createElement('input');
        input.type='hidden'; input.name='remove_videos[]'; input.value=v;
        form.appendChild(input);
      }
      form.querySelectorAll('input[name="existing_videos[]"]').forEach(function(k){ if (k.value === v) k.remove(); });
    });
  });

  function escAttr(v) { return String(v || '').replace(/&/g, '&amp;').replace(/\"/g, '&quot;').replace(/</g, '&lt;').replace(/>/g, '&gt;'); }
  function escText(v) { return String(v || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;'); }

  function typeLabel(type) {
    var titleMap = {hero:'Hero',h1:'H1',h2:'H2',h3:'H3',p:'Paragraph',image:'Image',video:'Video',callout:'Callout',ol:'Ordered List',ul:'Unordered List',divider:'Divider'};
    return titleMap[type] || type;
  }

  function createBlock(type, data) {
    var initial = data || {};
    var token = (initial && initial.uploadToken) ? initial.uploadToken : ('edit_' + (blockSeq++));
    var body = '';

    if (type === 'hero' || type === 'image') {
      var key = type === 'hero' ? 'image' : 'url';
      body += '<div class="mb-3"><label class="form-label">Image URL / Path</label><input class="form-control form-control-lg block-url" value="' + escAttr(initial[key] || initial.url || initial.image || '') + '"></div>';
      body += '<div class="mb-3"><label class="form-label">Upload Image (optional)</label><input type="file" class="form-control" name="block_image_upload_' + token + '" accept="image/*"></div>';
      body += '<div class="guide-grid-2">';
      body += '<div class="mb-3"><label class="form-label">Caption</label><input class="form-control block-caption" value="' + escAttr(initial.caption || '') + '"></div>';
      body += '<div class="mb-3"><label class="form-label">Alt Text</label><input class="form-control block-alt" value="' + escAttr(initial.alt || '') + '"></div>';
      body += '</div>';
    } else if (type === 'video') {
      body += '<div class="mb-3"><label class="form-label">Video URL</label><input class="form-control form-control-lg block-url" value="' + escAttr(initial.url || '') + '"></div>';
      body += '<div class="guide-grid-2">';
      body += '<div class="mb-3"><label class="form-label">Provider</label><select class="form-control block-provider"><option value="mp4">mp4</option><option value="youtube">youtube</option><option value="vimeo">vimeo</option></select></div>';
      body += '<div class="mb-3"><label class="form-label">Caption</label><input class="form-control block-caption" value="' + escAttr(initial.caption || '') + '"></div>';
      body += '</div>';
    } else if (type === 'callout') {
      body += '<div class="mb-3"><label class="form-label">Title</label><input class="form-control form-control-lg block-title" value="' + escAttr(initial.title || 'Expert advice') + '"></div>';
      body += '<div class="mb-3"><label class="form-label">Text</label><textarea class="form-control block-text" rows="5">' + escText(initial.text || '') + '</textarea></div>';
    } else if (type === 'ol' || type === 'ul') {
      var items = Array.isArray(initial.items) ? initial.items.join('\n') : '';
      body += '<div class="mb-3"><label class="form-label">Items (one per line)</label><textarea class="form-control block-items" rows="7">' + escText(items) + '</textarea></div>';
    } else if (type === 'divider') {
      body += '<div class="text-muted">Divider block has no extra fields.</div>';
    } else {
      body += '<div class="mb-3"><label class="form-label">Text</label><textarea class="form-control block-text" rows="6">' + escText(initial.text || '') + '</textarea></div>';
    }

    var block = document.createElement('details');
    block.className = 'guide-block-item content-block-item';
    block.dataset.type = type;
    block.dataset.token = token;
    block.open = true;
    block.innerHTML =
      '<summary>' +
        '<div class="block-head">' +
          '<span class="block-type-badge">' + typeLabel(type) + '</span>' +
          '<span class="block-preview">No content yet</span>' +
        '</div>' +
        '<div class="block-actions">' +
          '<button type="button" class="btn btn-sm btn-light move-top" title="Move to top">⇤</button>' +
          '<button type="button" class="btn btn-sm btn-light move-up" title="Move up">↑</button>' +
          '<button type="button" class="btn btn-sm btn-light move-down" title="Move down">↓</button>' +
          '<button type="button" class="btn btn-sm btn-light move-bottom" title="Move to bottom">⇥</button>' +
          '<button type="button" class="btn btn-sm btn-danger remove-block">Delete</button>' +
        '</div>' +
      '</summary>' +
      '<div class="block-body">' + body + '</div>';

    if (type === 'video') {
      var provider = block.querySelector('.block-provider');
      if (provider) provider.value = initial.provider || 'mp4';
    }

    blocksContainer.appendChild(block);
    refreshBlockSummary(block);
  }

  function detectProvider(url) {
    var u = (url || '').toLowerCase();
    if (u.indexOf('youtube.com') >= 0 || u.indexOf('youtu.be') >= 0) return 'youtube';
    if (u.indexOf('vimeo.com') >= 0) return 'vimeo';
    return 'mp4';
  }

  function refreshBlockSummary(block) {
    var previewEl = block.querySelector('.block-preview');
    if (!previewEl) return;
    var type = block.dataset.type;
    var text = '';
    if (type === 'h1' || type === 'h2' || type === 'h3' || type === 'p' || type === 'callout') {
      var t = block.querySelector('.block-text');
      text = t ? (t.value || '').trim() : '';
    } else if (type === 'image' || type === 'hero' || type === 'video') {
      var u = block.querySelector('.block-url');
      text = u ? (u.value || '').trim() : '';
      if (!text) {
        var fileInput = block.querySelector('input[type="file"]');
        if (fileInput && fileInput.files && fileInput.files[0]) text = fileInput.files[0].name;
      }
    } else if (type === 'ol' || type === 'ul') {
      var items = block.querySelector('.block-items');
      text = items ? (items.value || '').split(/\r\n|\n|\r/).map(function(v){ return v.trim(); }).filter(Boolean).join(', ') : '';
    }
    previewEl.textContent = text ? text.slice(0, 50) : 'No content yet';
  }

  function serializeBlocks() {
    var out = [];
    blocksContainer.querySelectorAll('.content-block-item').forEach(function(item) {
      var type = item.dataset.type;
      var token = item.dataset.token;
      var block = { type: type };

      if (type === 'h1' || type === 'h2' || type === 'h3' || type === 'p') {
        var textEl = item.querySelector('.block-text');
        var text = textEl ? (textEl.value || '').trim() : '';
        if (!text) return;
        block.text = text;
      } else if (type === 'hero' || type === 'image') {
        var urlEl = item.querySelector('.block-url');
        var captionEl = item.querySelector('.block-caption');
        var altEl = item.querySelector('.block-alt');
        var url = urlEl ? (urlEl.value || '').trim() : '';
        var caption = captionEl ? (captionEl.value || '').trim() : '';
        var alt = altEl ? (altEl.value || '').trim() : '';
        if (url) {
          if (type === 'hero') block.image = url; else block.url = url;
        }
        block.uploadToken = token;
        if (caption) block.caption = caption;
        if (alt) block.alt = alt;
      } else if (type === 'video') {
        var vurlEl = item.querySelector('.block-url');
        var providerEl = item.querySelector('.block-provider');
        var captionEl = item.querySelector('.block-caption');
        var vurl = vurlEl ? (vurlEl.value || '').trim() : '';
        if (!vurl) return;
        block.url = vurl;
        block.provider = providerEl ? (providerEl.value || detectProvider(vurl)) : detectProvider(vurl);
        var vcap = captionEl ? (captionEl.value || '').trim() : '';
        if (vcap) block.caption = vcap;
      } else if (type === 'callout') {
        var titleEl = item.querySelector('.block-title');
        var textElCallout = item.querySelector('.block-text');
        var title = titleEl ? (titleEl.value || 'Expert advice').trim() : 'Expert advice';
        var ctext = textElCallout ? (textElCallout.value || '').trim() : '';
        if (!ctext) return;
        block.title = title || 'Expert advice';
        block.text = ctext;
      } else if (type === 'ol' || type === 'ul') {
        var itemsEl = item.querySelector('.block-items');
        var lines = (itemsEl ? (itemsEl.value || '') : '').split(/\r\n|\n|\r/).map(function(v){ return v.trim(); }).filter(Boolean);
        if (lines.length === 0) return;
        block.items = lines;
      }
      out.push(block);
    });
    return out;
  }

  function importFromPlainText() {
    var html = '';
    if (window.CKEDITOR && CKEDITOR.instances['content_editor']) {
      try { html = CKEDITOR.instances['content_editor'].getData() || ''; } catch(e) {}
    }
    if (!html) html = document.getElementById('content_editor').value || '';
    var temp = document.createElement('div');
    temp.innerHTML = html;
    var text = (temp.textContent || temp.innerText || '').trim();
    if (!text) { alert('No text found in content to import.'); return; }

    var parts = text.split(/\n\s*\n+/).map(function(v){ return v.trim(); }).filter(Boolean);
    if (parts.length === 0) { alert('No paragraphs found for import.'); return; }
    blocksContainer.innerHTML = '';
    parts.forEach(function(p){ createBlock('p', { text: p }); });
  }

  document.querySelectorAll('.add-block-btn').forEach(function(btn) {
    btn.addEventListener('click', function() { createBlock(btn.dataset.type || 'p', {}); });
  });

  var importBtn = document.getElementById('importFromContentBtn');
  if (importBtn) importBtn.addEventListener('click', importFromPlainText);

  var expandAll = document.getElementById('expandAllBlocks');
  if (expandAll) expandAll.addEventListener('click', function() {
    blocksContainer.querySelectorAll('.content-block-item').forEach(function(b){ b.open = true; });
  });

  var collapseAll = document.getElementById('collapseAllBlocks');
  if (collapseAll) collapseAll.addEventListener('click', function() {
    blocksContainer.querySelectorAll('.content-block-item').forEach(function(b){ b.open = false; });
  });

  blocksContainer.addEventListener('click', function(e) {
    var card = e.target.closest('.content-block-item');
    if (!card) return;
    if (e.target.closest('button')) e.preventDefault();

    if (e.target.classList.contains('remove-block')) {
      card.remove();
      return;
    }
    if (e.target.classList.contains('move-up') && card.previousElementSibling) {
      blocksContainer.insertBefore(card, card.previousElementSibling);
      return;
    }
    if (e.target.classList.contains('move-down') && card.nextElementSibling) {
      blocksContainer.insertBefore(card.nextElementSibling, card);
      return;
    }
    if (e.target.classList.contains('move-top')) {
      blocksContainer.insertBefore(card, blocksContainer.firstElementChild);
      return;
    }
    if (e.target.classList.contains('move-bottom')) {
      blocksContainer.appendChild(card);
      return;
    }
  });

  blocksContainer.addEventListener('input', function(e) {
    var card = e.target.closest('.content-block-item');
    if (card) refreshBlockSummary(card);
  });
  blocksContainer.addEventListener('change', function(e) {
    var card = e.target.closest('.content-block-item');
    if (card) refreshBlockSummary(card);
  });

  if (Array.isArray(contentBlocksInitial) && contentBlocksInitial.length) {
    contentBlocksInitial.forEach(function(b) {
      var t = (b && b.type) ? String(b.type).toLowerCase() : '';
      if (!t) return;
      createBlock(t, b);
    });
  }

  function normalizeTagsInput(raw) {
    if (!raw) return [];
    raw = raw.trim();
    var tagSet = [];
    var hashRe = /#([^\s#,]+)/gu;
    var m;
    while ((m = hashRe.exec(raw)) !== null) {
      if (m[1]) tagSet.push(m[1]);
    }
    if (tagSet.length === 0) {
      if (raw.indexOf(',') !== -1) {
        tagSet = raw.split(',').map(function(s){ return s.trim(); }).filter(Boolean);
      } else {
        tagSet = raw.split(/\s+/).map(function(s){ return s.trim().replace(/^#/, ''); }).filter(Boolean);
      }
    }
    tagSet = tagSet.map(function(t){
      return t.replace(/^[^\p{L}\p{N}_-]+|[^\p{L}\p{N}_-]+$/gu, '');
    }).filter(Boolean);

    var seen = {};
    var out = [];
    tagSet.forEach(function(t){
      var key = t.toLowerCase();
      if (!seen[key]) { seen[key] = true; out.push(t); }
    });
    return out;
  }

  var formEl = document.getElementById('editGuideForm');
  if (formEl) {
    formEl.addEventListener('submit', function(e) {
      if (window.CKEDITOR && CKEDITOR.instances['content_editor']) {
        try {
          CKEDITOR.instances['content_editor'].updateElement();
          document.getElementById('content_editor_hidden').value = CKEDITOR.instances['content_editor'].getData();
        } catch(err) {}
      }
      var title = formEl.querySelector('input[name="title"]').value.trim();
      var cat = formEl.querySelector('select[name="category_id"]').value;
      if (!title) { alert('Title is required'); e.preventDefault(); return false; }
      if (!cat) { alert('Category is required'); e.preventDefault(); return false; }

      var tagsEl = formEl.querySelector('input[name="tags"]');
      if (tagsEl) {
        var normalized = normalizeTagsInput(tagsEl.value);
        tagsEl.value = normalized.join(',');
      }

      blocksJsonEl.value = JSON.stringify(serializeBlocks());
      return true;
    });
  }
});
</script>

<?php include '../../includes/footer.php'; ?>
