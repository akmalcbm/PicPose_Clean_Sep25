<?php
session_start();
require '../../config.php';
if (!isset($_SESSION['admin'])) { header('Location: ../../login.php'); exit(); }

if (empty($_SESSION['csrf_token'])) $_SESSION['csrf_token'] = bin2hex(random_bytes(32));
$csrf = $_SESSION['csrf_token'];

function premium_pack_make_image_url(?string $primaryPath, ?string $fallbackPath = null): string
{
    $path = trim((string)$primaryPath);
    if ($path === '') {
        $path = trim((string)$fallbackPath);
    }
    if ($path === '') {
        return '';
    }
    if (preg_match('#^https?://#i', $path)) {
        return $path;
    }

    $path = ltrim($path, '/');
    if ($path === '') {
        return '';
    }
    if (strpos($path, 'uploads/') !== 0) {
        $uploadsPos = strpos($path, 'uploads/');
        if ($uploadsPos !== false) {
            $path = substr($path, $uploadsPos);
        } else {
            $path = 'uploads/' . $path;
        }
    }
    return '/' . $path;
}

function premium_pack_short_text(?string $text, int $limit = 160): string
{
    $value = trim((string)$text);
    if ($value === '') {
        return '';
    }
    if (function_exists('mb_strlen') && mb_strlen($value, 'UTF-8') > $limit) {
        return rtrim(mb_substr($value, 0, $limit - 1, 'UTF-8')) . '...';
    }
    if (strlen($value) > $limit) {
        return rtrim(substr($value, 0, $limit - 1)) . '...';
    }
    return $value;
}

function premium_pack_render_prompt_item(array $row, array $options = []): string
{
    $mode = (string)($options['mode'] ?? 'readonly'); // readonly | select
    $selected = !empty($options['selected']);
    $inputName = (string)($options['input_name'] ?? 'post_ids[]');
    $inputId = (string)($options['input_id'] ?? ('post_' . (int)($row['id'] ?? 0)));
    $placeholder = (string)($options['placeholder'] ?? '');
    if ($placeholder === '') {
        $placeholder = 'data:image/svg+xml;utf8,' . rawurlencode(
            '<svg xmlns="http://www.w3.org/2000/svg" width="72" height="72" viewBox="0 0 72 72">'
            . '<rect width="72" height="72" rx="10" fill="#E9ECEF"/>'
            . '<path d="M14 50l14-16 10 10 6-7 14 13v4H14z" fill="#ADB5BD"/>'
            . '<circle cx="27" cy="24" r="5" fill="#CED4DA"/>'
            . '<text x="36" y="67" text-anchor="middle" fill="#6C757D" font-size="8" font-family="Arial, sans-serif">No Image</text>'
            . '</svg>'
        );
    }

    $postId = (int)($row['id'] ?? 0);
    $title = htmlspecialchars((string)($row['title'] ?? 'Untitled Prompt'), ENT_QUOTES, 'UTF-8');
    $description = premium_pack_short_text($row['short_description'] ?? '', 170);
    $descriptionEsc = htmlspecialchars($description, ENT_QUOTES, 'UTF-8');
    $tierRaw = strtoupper((string)($row['tier'] ?? ''));
    $tier = in_array($tierRaw, ['FREE', 'PREMIUM'], true) ? $tierRaw : '';
    $category = trim((string)($row['category_name'] ?? ''));
    $categoryEsc = htmlspecialchars($category, ENT_QUOTES, 'UTF-8');

    $thumb = trim((string)($row['thumb_url'] ?? ''));
    if ($thumb === '') {
        $thumb = $placeholder;
    }
    $thumbEsc = htmlspecialchars($thumb, ENT_QUOTES, 'UTF-8');
    $placeholderEsc = htmlspecialchars($placeholder, ENT_QUOTES, 'UTF-8');
    $onError = htmlspecialchars("this.onerror=null;this.src='{$placeholder}';", ENT_QUOTES, 'UTF-8');

    ob_start();
    if ($mode === 'select') {
        ?>
        <label class="prompt-item prompt-item-select <?php echo $selected ? 'is-selected' : ''; ?>" for="<?php echo htmlspecialchars($inputId, ENT_QUOTES, 'UTF-8'); ?>">
          <img
            src="<?php echo $thumbEsc; ?>"
            alt="<?php echo $title; ?>"
            class="prompt-thumb"
            loading="lazy"
            decoding="async"
            onerror="<?php echo $onError; ?>"
            data-fallback-src="<?php echo $placeholderEsc; ?>"
          >
          <div class="prompt-meta">
            <div class="prompt-title-row">
              <div class="prompt-title"><?php echo $title; ?></div>
              <?php if ($tier !== ''): ?>
                <span class="badge <?php echo $tier === 'PREMIUM' ? 'text-bg-warning' : 'text-bg-secondary'; ?> prompt-tier-badge"><?php echo htmlspecialchars($tier, ENT_QUOTES, 'UTF-8'); ?></span>
              <?php endif; ?>
            </div>
            <div class="prompt-desc">
              <?php if ($descriptionEsc !== ''): ?>
                <?php echo $descriptionEsc; ?>
              <?php else: ?>
                <span class="text-muted">No description available.</span>
              <?php endif; ?>
            </div>
            <?php if ($categoryEsc !== ''): ?>
              <div class="prompt-category"><?php echo $categoryEsc; ?></div>
            <?php endif; ?>
          </div>
          <input
            class="form-check-input prompt-check js-prompt-check"
            type="checkbox"
            name="<?php echo htmlspecialchars($inputName, ENT_QUOTES, 'UTF-8'); ?>"
            value="<?php echo $postId; ?>"
            id="<?php echo htmlspecialchars($inputId, ENT_QUOTES, 'UTF-8'); ?>"
            <?php echo $selected ? 'checked' : ''; ?>
          >
        </label>
        <?php
    } else {
        ?>
        <div class="prompt-item prompt-item-readonly">
          <img
            src="<?php echo $thumbEsc; ?>"
            alt="<?php echo $title; ?>"
            class="prompt-thumb"
            loading="lazy"
            decoding="async"
            onerror="<?php echo $onError; ?>"
            data-fallback-src="<?php echo $placeholderEsc; ?>"
          >
          <div class="prompt-meta">
            <div class="prompt-title-row">
              <div class="prompt-title"><?php echo $title; ?></div>
              <?php if ($tier !== ''): ?>
                <span class="badge <?php echo $tier === 'PREMIUM' ? 'text-bg-warning' : 'text-bg-secondary'; ?> prompt-tier-badge"><?php echo htmlspecialchars($tier, ENT_QUOTES, 'UTF-8'); ?></span>
              <?php endif; ?>
            </div>
            <div class="prompt-desc">
              <?php if ($descriptionEsc !== ''): ?>
                <?php echo $descriptionEsc; ?>
              <?php else: ?>
                <span class="text-muted">No description available.</span>
              <?php endif; ?>
            </div>
            <?php if ($categoryEsc !== ''): ?>
              <div class="prompt-category"><?php echo $categoryEsc; ?></div>
            <?php endif; ?>
          </div>
        </div>
        <?php
    }
    return (string)ob_get_clean();
}

$editPackId = (int)($_GET['edit'] ?? 0);
$promptSearch = trim((string)($_GET['prompt_q'] ?? ''));
$promptTier = strtoupper(trim((string)($_GET['prompt_tier'] ?? '')));
if (!in_array($promptTier, ['FREE', 'PREMIUM'], true)) {
    $promptTier = '';
}
$promptCategoryId = (int)($_GET['prompt_category_id'] ?? 0);

$promptThumbPlaceholder = 'data:image/svg+xml;utf8,' . rawurlencode(
    '<svg xmlns="http://www.w3.org/2000/svg" width="72" height="72" viewBox="0 0 72 72">'
    . '<rect width="72" height="72" rx="10" fill="#E9ECEF"/>'
    . '<path d="M14 50l14-16 10 10 6-7 14 13v4H14z" fill="#ADB5BD"/>'
    . '<circle cx="27" cy="24" r="5" fill="#CED4DA"/>'
    . '<text x="36" y="67" text-anchor="middle" fill="#6C757D" font-size="8" font-family="Arial, sans-serif">No Image</text>'
    . '</svg>'
);

$availableCategories = [];
$categoryRes = $conn->query("
    SELECT
        c.id,
        c.name,
        COUNT(p.id) AS total_prompts
    FROM categories c
    INNER JOIN ai_posts p ON p.category_id = c.id
    WHERE p.status = 'published'
    GROUP BY c.id, c.name
    ORDER BY c.name ASC
");
if ($categoryRes) {
    while ($categoryRow = $categoryRes->fetch_assoc()) {
        $availableCategories[] = $categoryRow;
    }
}

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
        SELECT
            p.id,
            p.title,
            p.short_description,
            p.image_url1,
            p.image_url2,
            p.tier,
            c.name AS category_name
        FROM premium_pack_items ppi
        INNER JOIN ai_posts p ON p.id = ppi.post_id
        LEFT JOIN categories c ON c.id = p.category_id
        WHERE ppi.pack_id = ?
        ORDER BY p.priority DESC, p.created_at DESC
    ");
    if ($itemsStmt) {
        $itemsStmt->bind_param('i', $editPackId);
        $itemsStmt->execute();
        $itemsRes = $itemsStmt->get_result();
        while ($row = ($itemsRes ? $itemsRes->fetch_assoc() : null)) {
            $selectedPromptIds[] = (int)$row['id'];
            $row['thumb_url'] = premium_pack_make_image_url($row['image_url1'] ?? '', $row['image_url2'] ?? '');
            $selectedPrompts[(int)$row['id']] = $row;
        }
        $itemsStmt->close();
    }
}

$promptRows = $selectedPrompts;
$promptSql = "
    SELECT
        p.id,
        p.title,
        p.short_description,
        p.image_url1,
        p.image_url2,
        p.tier,
        c.name AS category_name
    FROM ai_posts p
    LEFT JOIN categories c ON c.id = p.category_id
    WHERE p.status = 'published'
";
$params = [];
$types = '';
if ($promptSearch !== '') {
    $promptSql .= " AND (p.title LIKE CONCAT('%', ?, '%') OR p.short_description LIKE CONCAT('%', ?, '%'))";
    $params[] = $promptSearch;
    $params[] = $promptSearch;
    $types .= 'ss';
}
if ($promptCategoryId > 0) {
    $promptSql .= " AND p.category_id = ?";
    $params[] = $promptCategoryId;
    $types .= 'i';
}
if ($promptTier !== '') {
    $promptSql .= " AND p.tier = ?";
    $params[] = $promptTier;
    $types .= 's';
}
$promptSql .= " ORDER BY p.priority DESC, p.created_at DESC LIMIT 80";
$promptStmt = $conn->prepare($promptSql);
if ($promptStmt) {
    if ($types !== '') {
        $promptStmt->bind_param($types, ...$params);
    }
    $promptStmt->execute();
    $promptRes = $promptStmt->get_result();
    while ($row = ($promptRes ? $promptRes->fetch_assoc() : null)) {
        $row['thumb_url'] = premium_pack_make_image_url($row['image_url1'] ?? '', $row['image_url2'] ?? '');
        $promptRows[(int)$row['id']] = $row;
    }
    $promptStmt->close();
}

$packPromptDetails = [];
$packIds = [];
foreach ($packs as $packRow) {
    $packIds[] = (int)$packRow['id'];
}
if (!empty($packIds)) {
    $placeholders = implode(',', array_fill(0, count($packIds), '?'));
    $types = str_repeat('i', count($packIds));
    $packPromptSql = "
        SELECT
            ppi.pack_id,
            p.id,
            p.title,
            p.short_description,
            p.image_url1,
            p.image_url2,
            p.tier,
            c.name AS category_name
        FROM premium_pack_items ppi
        INNER JOIN ai_posts p ON p.id = ppi.post_id
        LEFT JOIN categories c ON c.id = p.category_id
        WHERE ppi.pack_id IN ($placeholders)
        ORDER BY ppi.pack_id ASC, p.priority DESC, p.created_at DESC
    ";
    $packPromptStmt = $conn->prepare($packPromptSql);
    if ($packPromptStmt) {
        $packPromptStmt->bind_param($types, ...$packIds);
        $packPromptStmt->execute();
        $packPromptRes = $packPromptStmt->get_result();
        while ($promptRow = ($packPromptRes ? $packPromptRes->fetch_assoc() : null)) {
            $packId = (int)$promptRow['pack_id'];
            if (!isset($packPromptDetails[$packId])) {
                $packPromptDetails[$packId] = [];
            }
            $promptRow['thumb_url'] = premium_pack_make_image_url($promptRow['image_url1'] ?? '', $promptRow['image_url2'] ?? '');
            $packPromptDetails[$packId][] = $promptRow;
        }
        $packPromptStmt->close();
    }
}

$selectedPromptIdMap = [];
foreach ($selectedPromptIds as $selectedPromptId) {
    $selectedPromptIdMap[(int)$selectedPromptId] = true;
}

$selectedPromptCount = count($selectedPromptIdMap);
$clearFilterUrl = $editPackId > 0 ? ('premium_packs.php?edit=' . (int)$editPackId) : 'premium_packs.php';

include '../../includes/header.php';
?>

<style>
  .premium-prompts-toolbar .form-label { margin-bottom: 0.3rem; }
  .prompt-selection-scroll {
    max-height: 460px;
    overflow: auto;
    padding-right: 2px;
  }
  .prompt-item {
    display: flex;
    align-items: flex-start;
    gap: 12px;
    border: 1px solid #dee2e6;
    border-radius: 12px;
    padding: 10px 12px;
    background: #fff;
    transition: border-color .2s ease, background-color .2s ease, box-shadow .2s ease;
  }
  .prompt-item + .prompt-item {
    margin-top: 8px;
  }
  .prompt-item-select {
    cursor: pointer;
  }
  .prompt-item-select:hover {
    border-color: rgba(13, 110, 253, 0.45);
    background: #f5f9ff;
  }
  .prompt-item.is-selected {
    border-color: #0d6efd;
    background: #ebf3ff;
    box-shadow: 0 0 0 1px rgba(13, 110, 253, 0.18);
  }
  .prompt-thumb {
    width: 64px;
    height: 64px;
    border-radius: 10px;
    object-fit: cover;
    flex: 0 0 64px;
    background: #e9ecef;
  }
  .prompt-meta {
    min-width: 0;
    flex: 1;
  }
  .prompt-title-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    margin-bottom: 4px;
  }
  .prompt-title {
    font-weight: 600;
    line-height: 1.25;
    color: #212529;
    word-break: break-word;
  }
  .prompt-tier-badge {
    flex: 0 0 auto;
    font-size: 0.68rem;
    letter-spacing: .2px;
  }
  .prompt-desc {
    font-size: 0.84rem;
    color: #6c757d;
    line-height: 1.35;
    margin-bottom: 4px;
  }
  .prompt-category {
    font-size: 0.76rem;
    color: #495057;
  }
  .prompt-check {
    margin-top: 20px;
    flex: 0 0 auto;
  }
  .pack-preview {
    display: flex;
    align-items: center;
    gap: 6px;
    flex-wrap: wrap;
  }
  .pack-mini-thumb {
    width: 28px;
    height: 28px;
    border-radius: 6px;
    object-fit: cover;
    border: 1px solid #dce1e6;
    background: #e9ecef;
  }
  .pack-modal-list {
    max-height: 52vh;
    overflow: auto;
  }
  @media (max-width: 575px) {
    .prompt-thumb {
      width: 54px;
      height: 54px;
      flex-basis: 54px;
    }
    .prompt-check {
      margin-top: 16px;
    }
  }
</style>

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
          <form method="GET" class="mb-3 premium-prompts-toolbar">
            <?php if ($editPackId > 0): ?>
              <input type="hidden" name="edit" value="<?php echo $editPackId; ?>">
            <?php endif; ?>
            <label class="form-label">Search Published Prompts</label>
            <div class="row g-2">
              <div class="col-md-6">
                <input type="text" name="prompt_q" class="form-control" placeholder="Search by title or short prompt" value="<?php echo htmlspecialchars($promptSearch); ?>">
              </div>
              <div class="col-md-3">
                <select name="prompt_tier" class="form-select">
                  <option value="">All Tiers</option>
                  <option value="FREE" <?php echo $promptTier === 'FREE' ? 'selected' : ''; ?>>Free</option>
                  <option value="PREMIUM" <?php echo $promptTier === 'PREMIUM' ? 'selected' : ''; ?>>Premium</option>
                </select>
              </div>
              <div class="col-md-3">
                <select name="prompt_category_id" class="form-select">
                  <option value="0">All Categories</option>
                  <?php foreach ($availableCategories as $category): ?>
                    <option value="<?php echo (int)$category['id']; ?>" <?php echo (int)$promptCategoryId === (int)$category['id'] ? 'selected' : ''; ?>>
                      <?php echo htmlspecialchars((string)$category['name']); ?>
                    </option>
                  <?php endforeach; ?>
                </select>
              </div>
            </div>
            <div class="d-flex gap-2 mt-2">
              <button type="submit" class="btn btn-outline-primary">Apply Filters</button>
              <a href="<?php echo htmlspecialchars($clearFilterUrl); ?>" class="btn btn-outline-secondary">Clear</a>
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
              <div class="d-flex justify-content-between align-items-center mb-2">
                <label class="form-label mb-0">Included Prompts</label>
                <small class="text-muted" id="selectedPromptCount"><?php echo (int)$selectedPromptCount; ?> selected</small>
              </div>
              <div class="border rounded p-2 prompt-selection-scroll">
                <?php if (empty($promptRows)): ?>
                  <div class="text-muted small">No published prompts found.</div>
                <?php else: ?>
                  <?php foreach ($promptRows as $row): $postId = (int)$row['id']; ?>
                    <?php
                      echo premium_pack_render_prompt_item($row, [
                          'mode' => 'select',
                          'selected' => isset($selectedPromptIdMap[$postId]),
                          'input_name' => 'post_ids[]',
                          'input_id' => 'post_' . $postId,
                          'placeholder' => $promptThumbPlaceholder,
                      ]);
                    ?>
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
                  <th>Preview</th>
                  <th>Status</th>
                  <th>Revenue</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
              <?php if (empty($packs)): ?>
                <tr>
                  <td colspan="8" class="text-center text-muted">No premium packs created yet.</td>
                </tr>
              <?php else: ?>
                <?php foreach ($packs as $pack): ?>
                  <?php
                    $packId = (int)$pack['id'];
                    $packItems = $packPromptDetails[$packId] ?? [];
                    $packItemCount = count($packItems);
                  ?>
                  <tr>
                    <td><?php echo $packId; ?></td>
                    <td>
                      <strong><?php echo htmlspecialchars((string)$pack['name']); ?></strong>
                      <?php if (!empty($pack['description'])): ?>
                        <br><small class="text-muted"><?php echo htmlspecialchars(substr((string)$pack['description'], 0, 120)); ?></small>
                      <?php endif; ?>
                    </td>
                    <td><?php echo (int)$pack['price_points']; ?> pts</td>
                    <td><?php echo (int)$pack['item_count']; ?></td>
                    <td>
                      <?php if ($packItemCount === 0): ?>
                        <small class="text-muted">No prompts</small>
                      <?php else: ?>
                        <div class="pack-preview">
                          <?php foreach (array_slice($packItems, 0, 4) as $previewItem): ?>
                            <?php $previewThumb = trim((string)($previewItem['thumb_url'] ?? '')) !== '' ? (string)$previewItem['thumb_url'] : $promptThumbPlaceholder; ?>
                            <img
                              src="<?php echo htmlspecialchars($previewThumb, ENT_QUOTES, 'UTF-8'); ?>"
                              alt="Prompt thumbnail"
                              class="pack-mini-thumb"
                              loading="lazy"
                              onerror="this.onerror=null;this.src='<?php echo htmlspecialchars($promptThumbPlaceholder, ENT_QUOTES, 'UTF-8'); ?>';"
                            >
                          <?php endforeach; ?>
                          <?php if ($packItemCount > 4): ?>
                            <span class="badge text-bg-light border">+<?php echo $packItemCount - 4; ?></span>
                          <?php endif; ?>
                        </div>
                      <?php endif; ?>
                    </td>
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
                      <button
                        type="button"
                        class="btn btn-sm btn-outline-secondary js-view-pack"
                        data-pack-id="<?php echo $packId; ?>"
                        data-pack-name="<?php echo htmlspecialchars((string)$pack['name'], ENT_QUOTES, 'UTF-8'); ?>"
                        data-item-count="<?php echo $packItemCount; ?>"
                      >
                        View
                      </button>
                      <a href="premium_packs.php?edit=<?php echo $packId; ?>" class="btn btn-sm btn-primary">Edit</a>
                      <button
                        type="button"
                        class="btn btn-sm btn-danger js-delete-pack"
                        data-pack-id="<?php echo $packId; ?>"
                        data-pack-name="<?php echo htmlspecialchars((string)$pack['name'], ENT_QUOTES, 'UTF-8'); ?>"
                        data-item-count="<?php echo $packItemCount; ?>"
                      >
                        Delete
                      </button>
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

<div class="d-none" aria-hidden="true">
  <?php foreach ($packs as $pack): ?>
    <?php
      $packId = (int)$pack['id'];
      $packItems = $packPromptDetails[$packId] ?? [];
    ?>
    <template id="pack-prompts-template-<?php echo $packId; ?>">
      <?php if (empty($packItems)): ?>
        <div class="text-muted small">No prompts included in this pack.</div>
      <?php else: ?>
        <?php foreach ($packItems as $promptItem): ?>
          <?php echo premium_pack_render_prompt_item($promptItem, ['mode' => 'readonly', 'placeholder' => $promptThumbPlaceholder]); ?>
        <?php endforeach; ?>
      <?php endif; ?>
    </template>
  <?php endforeach; ?>
</div>

<div class="modal fade" id="packPromptsModal" tabindex="-1" aria-labelledby="packPromptsModalLabel" aria-hidden="true">
  <div class="modal-dialog modal-lg modal-dialog-scrollable">
    <div class="modal-content">
      <div class="modal-header">
        <h5 class="modal-title" id="packPromptsModalLabel">Pack Prompts</h5>
        <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
      </div>
      <div class="modal-body pack-modal-list" id="packPromptsModalBody">
        <div class="text-muted small">No prompt details available.</div>
      </div>
    </div>
  </div>
</div>

<div class="modal fade" id="deletePackModal" tabindex="-1" aria-labelledby="deletePackModalLabel" aria-hidden="true">
  <div class="modal-dialog modal-lg modal-dialog-scrollable">
    <div class="modal-content">
      <form method="POST" action="premium_packs_process.php">
        <div class="modal-header">
          <h5 class="modal-title" id="deletePackModalLabel">Delete Premium Pack</h5>
          <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
        </div>
        <div class="modal-body">
          <input type="hidden" name="csrf_token" value="<?php echo htmlspecialchars($csrf); ?>">
          <input type="hidden" name="action" value="delete">
          <input type="hidden" name="pack_id" id="deletePackIdInput" value="">

          <p class="mb-1">Are you sure you want to delete <strong id="deletePackName"></strong>?</p>
          <p class="text-muted small mb-3" id="deletePackMeta">This action cannot be undone.</p>

          <div class="border rounded p-2 pack-modal-list" id="deletePackPromptPreview">
            <div class="text-muted small">No prompts included in this pack.</div>
          </div>
        </div>
        <div class="modal-footer">
          <button type="button" class="btn btn-outline-secondary" data-bs-dismiss="modal">Cancel</button>
          <button type="submit" class="btn btn-danger">Delete Pack</button>
        </div>
      </form>
    </div>
  </div>
</div>

<script>
document.addEventListener('DOMContentLoaded', function () {
  var checks = Array.prototype.slice.call(document.querySelectorAll('.js-prompt-check'));
  var selectedCountEl = document.getElementById('selectedPromptCount');

  function syncSelectedState() {
    var selectedCount = 0;
    checks.forEach(function (checkbox) {
      var item = checkbox.closest('.prompt-item-select');
      if (!item) return;
      if (checkbox.checked) {
        item.classList.add('is-selected');
        selectedCount++;
      } else {
        item.classList.remove('is-selected');
      }
    });
    if (selectedCountEl) {
      selectedCountEl.textContent = selectedCount + ' selected';
    }
  }

  checks.forEach(function (checkbox) {
    checkbox.addEventListener('change', syncSelectedState);
  });
  syncSelectedState();

  var viewModalEl = document.getElementById('packPromptsModal');
  var viewModalBody = document.getElementById('packPromptsModalBody');
  var viewModalTitle = document.getElementById('packPromptsModalLabel');

  var deleteModalEl = document.getElementById('deletePackModal');
  var deletePackIdInput = document.getElementById('deletePackIdInput');
  var deletePackName = document.getElementById('deletePackName');
  var deletePackMeta = document.getElementById('deletePackMeta');
  var deletePackPromptPreview = document.getElementById('deletePackPromptPreview');

  function getPromptTemplateHtml(packId) {
    var template = document.getElementById('pack-prompts-template-' + packId);
    if (!template) {
      return '<div class="text-muted small">No prompt details available.</div>';
    }
    return template.innerHTML;
  }

  document.querySelectorAll('.js-view-pack').forEach(function (button) {
    button.addEventListener('click', function () {
      var packId = parseInt(button.getAttribute('data-pack-id') || '0', 10);
      var packName = button.getAttribute('data-pack-name') || 'Pack';
      var itemCount = parseInt(button.getAttribute('data-item-count') || '0', 10);

      if (viewModalTitle) {
        viewModalTitle.textContent = packName + ' (' + itemCount + ' prompts)';
      }
      if (viewModalBody) {
        viewModalBody.innerHTML = getPromptTemplateHtml(packId);
      }
      if (window.bootstrap && viewModalEl) {
        window.bootstrap.Modal.getOrCreateInstance(viewModalEl).show();
      }
    });
  });

  document.querySelectorAll('.js-delete-pack').forEach(function (button) {
    button.addEventListener('click', function () {
      var packId = parseInt(button.getAttribute('data-pack-id') || '0', 10);
      var packName = button.getAttribute('data-pack-name') || 'this pack';
      var itemCount = parseInt(button.getAttribute('data-item-count') || '0', 10);

      if (deletePackIdInput) deletePackIdInput.value = String(packId);
      if (deletePackName) deletePackName.textContent = packName;
      if (deletePackMeta) {
        deletePackMeta.textContent = itemCount > 0
          ? itemCount + ' prompts are currently included in this pack.'
          : 'No prompts are currently included in this pack.';
      }
      if (deletePackPromptPreview) {
        deletePackPromptPreview.innerHTML = getPromptTemplateHtml(packId);
      }
      if (window.bootstrap && deleteModalEl) {
        window.bootstrap.Modal.getOrCreateInstance(deleteModalEl).show();
      }
    });
  });
});
</script>

<?php include '../../includes/footer.php'; ?>
