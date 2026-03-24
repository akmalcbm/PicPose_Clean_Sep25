<?php
session_start();
require '../../config.php';
require_once '../../app/helpers/potd_helper.php';

if (!isset($_SESSION['admin'])) {
    header('Location: ../../login.php');
    exit();
}

if (empty($_SESSION['csrf_token'])) {
    $_SESSION['csrf_token'] = bin2hex(random_bytes(32));
}
$csrf = $_SESSION['csrf_token'];

$today = potd_today_date($conn);
$tablesReady = potd_db_table_exists($conn, 'prompt_of_day_entries') && potd_db_table_exists($conn, 'prompt_of_day_config');

$config = potd_default_config();
$liveOffer = null;
$livePrompt = null;
$entries = [];
$editableEntry = null;
$publishedPrompts = [];
$statusWarnings = [];

if ($tablesReady) {
    $config = potd_load_config($conn);

    $liveOffer = potd_resolve_effective_prompt_offer($conn, $today);
    if ($liveOffer && !empty($liveOffer['post_id'])) {
        $livePrompt = potd_prompt_preview_by_id($conn, (int)$liveOffer['post_id']);
    }

    $entriesSql = "
        SELECT
            e.*,
            p.title AS prompt_title,
            p.short_description,
            p.image_url1,
            p.image_url2,
            p.status AS prompt_status,
            UPPER(COALESCE(p.tier, 'FREE')) AS prompt_tier,
            p.is_featured,
            c.name AS category_name
        FROM prompt_of_day_entries e
        LEFT JOIN ai_posts p ON p.id = e.prompt_id
        LEFT JOIN categories c ON c.id = p.category_id
        ORDER BY
            e.is_active DESC,
            e.is_default DESC,
            CASE WHEN e.start_date IS NULL THEN 1 ELSE 0 END,
            e.start_date ASC,
            e.priority DESC,
            e.id DESC
    ";
    $entriesRes = $conn->query($entriesSql);
    if ($entriesRes) {
        while ($row = $entriesRes->fetch_assoc()) {
            $entries[] = $row;
        }
    }

    $allowPremium = !empty($config['allow_premium_prompts']) ? '' : "AND UPPER(COALESCE(p.tier, 'FREE')) <> 'PREMIUM'";
    $promptsSql = "
        SELECT
            p.id,
            p.title,
            p.short_description,
            p.image_url1,
            p.image_url2,
            UPPER(COALESCE(p.tier, 'FREE')) AS tier,
            p.is_featured,
            c.name AS category_name,
            p.status
        FROM ai_posts p
        LEFT JOIN categories c ON c.id = p.category_id
        WHERE p.status = 'published'
          {$allowPremium}
        ORDER BY p.is_featured DESC, p.priority DESC, p.created_at DESC
        LIMIT 1000
    ";
    $promptsRes = $conn->query($promptsSql);
    if ($promptsRes) {
        while ($row = $promptsRes->fetch_assoc()) {
            $publishedPrompts[] = $row;
        }
    }

    $editId = (int)($_GET['edit_id'] ?? 0);
    if ($editId > 0) {
        foreach ($entries as $entry) {
            if ((int)$entry['id'] === $editId) {
                $editableEntry = $entry;
                break;
            }
        }
    }

    $activeScheduleCount = 0;
    $stmt = $conn->prepare(" 
        SELECT COUNT(*) AS cnt
        FROM prompt_of_day_entries
        WHERE is_active = 1
          AND is_default = 0
          AND start_date IS NOT NULL
          AND start_date <= ?
          AND (end_date IS NULL OR end_date >= ?)
    ");
    if ($stmt) {
        $stmt->bind_param('ss', $today, $today);
        $stmt->execute();
        $res = $stmt->get_result();
        $row = $res ? $res->fetch_assoc() : null;
        $stmt->close();
        $activeScheduleCount = (int)($row['cnt'] ?? 0);
    }

    if ($activeScheduleCount > 1) {
        $statusWarnings[] = 'Multiple active schedule rows currently match today. Highest-priority row is used, but this should be cleaned up.';
    }

    $activeDefaultCount = 0;
    $defaultRes = $conn->query("SELECT COUNT(*) AS cnt FROM prompt_of_day_entries WHERE is_active = 1 AND is_default = 1");
    if ($defaultRes && ($defaultRow = $defaultRes->fetch_assoc())) {
        $activeDefaultCount = (int)($defaultRow['cnt'] ?? 0);
    }
    if ($activeDefaultCount > 1) {
        $statusWarnings[] = 'Multiple active default rows exist. Only the highest-priority default row is used.';
    }
}

function h(?string $value): string
{
    return htmlspecialchars((string)$value, ENT_QUOTES);
}

function image_preview_url(?string $path): string
{
    $p = trim((string)$path);
    if ($p === '') {
        return '';
    }
    if (preg_match('#^https?://#i', $p)) {
        return $p;
    }
    return '/' . ltrim($p, '/');
}

function entry_state(array $entry, string $today): string
{
    $isActive = ((int)($entry['is_active'] ?? 0)) === 1;
    $isDefault = ((int)($entry['is_default'] ?? 0)) === 1;
    $start = (string)($entry['start_date'] ?? '');
    $end = (string)($entry['end_date'] ?? '');

    if (!$isActive) {
        return 'inactive';
    }
    if ($isDefault) {
        return 'default';
    }
    if ($start !== '' && $start > $today) {
        return 'upcoming';
    }
    if ($end !== '' && $end < $today) {
        return 'past';
    }
    return 'active_today';
}

include '../../includes/header.php';
?>

<div class="container-fluid">
  <div class="d-flex flex-wrap justify-content-between align-items-center mb-3 gap-2">
    <div>
      <h2 class="mb-1">Prompt of the Day Management</h2>
      <div class="text-muted small">Date today: <strong><?php echo h($today); ?></strong></div>
    </div>
    <a href="manage_ai_posts.php" class="btn btn-outline-secondary">Back to AI Posts</a>
  </div>

  <?php if (!empty($_SESSION['message'])): ?>
    <div class="alert alert-<?php echo h($_SESSION['message_type'] ?? 'info'); ?>">
      <?php
      echo h($_SESSION['message']);
      unset($_SESSION['message'], $_SESSION['message_type']);
      ?>
    </div>
  <?php endif; ?>

  <?php if (!$tablesReady): ?>
    <div class="alert alert-danger">
      <h5 class="mb-2">Database migration required</h5>
      <p class="mb-2">Prompt of the Day management tables are missing. Run this migration first:</p>
      <code>/migrations/2026_03_24_prompt_of_day_management.sql</code>
    </div>
    <?php include '../../includes/footer.php'; ?>
    <?php exit(); ?>
  <?php endif; ?>

  <?php foreach ($statusWarnings as $warning): ?>
    <div class="alert alert-warning">
      <?php echo h($warning); ?>
    </div>
  <?php endforeach; ?>

  <div class="row g-3 mb-4">
    <div class="col-xl-6">
      <div class="card h-100 shadow-sm">
        <div class="card-body">
          <h5 class="card-title mb-3">Live Prompt Preview</h5>
          <?php if ($liveOffer && $livePrompt): ?>
            <?php
            $liveImage = image_preview_url($livePrompt['image_url1'] ?: $livePrompt['image_url2']);
            $liveTitle = trim((string)($liveOffer['title_override'] ?? ''));
            if ($liveTitle === '') {
                $liveTitle = (string)($livePrompt['title'] ?? 'Untitled prompt');
            }
            $liveSubtitle = trim((string)($liveOffer['subtitle_override'] ?? ''));
            $liveBadge = trim((string)($liveOffer['badge_text'] ?? ''));
            ?>
            <div class="d-flex gap-3 align-items-start">
              <div style="width:120px; flex:0 0 120px;">
                <?php if ($liveImage !== ''): ?>
                  <img src="<?php echo h($liveImage); ?>" alt="Prompt image" class="img-fluid rounded border" loading="lazy">
                <?php else: ?>
                  <div class="rounded border bg-light d-flex align-items-center justify-content-center text-muted" style="height:88px;">No image</div>
                <?php endif; ?>
              </div>
              <div class="flex-grow-1">
                <div class="d-flex flex-wrap gap-2 mb-2">
                  <span class="badge bg-success">Live Today</span>
                  <span class="badge bg-primary"><?php echo h((string)($liveOffer['source'] ?? 'UNKNOWN')); ?></span>
                  <span class="badge bg-dark"><?php echo h((string)($liveOffer['mode'] ?? 'NORMAL')); ?></span>
                  <?php if ((int)($liveOffer['discount_cost_points'] ?? 0) > 0): ?>
                    <span class="badge bg-info text-dark"><?php echo (int)$liveOffer['discount_cost_points']; ?> credits</span>
                  <?php endif; ?>
                </div>
                <h6 class="mb-1"><?php echo h($liveTitle); ?></h6>
                <?php if ($liveSubtitle !== ''): ?>
                  <div class="text-muted small mb-2"><?php echo h($liveSubtitle); ?></div>
                <?php else: ?>
                  <div class="text-muted small mb-2"><?php echo h((string)($livePrompt['short_description'] ?? '')); ?></div>
                <?php endif; ?>
                <div class="small">
                  <strong>Prompt ID:</strong> <?php echo (int)$livePrompt['id']; ?>
                  <span class="mx-2">|</span>
                  <strong>Tier:</strong> <?php echo h((string)($livePrompt['tier'] ?? 'FREE')); ?>
                  <span class="mx-2">|</span>
                  <strong>Category:</strong> <?php echo h((string)($livePrompt['category_name'] ?? 'Uncategorized')); ?>
                </div>
                <?php if ($liveBadge !== ''): ?>
                  <div class="small mt-2"><strong>Badge:</strong> <?php echo h($liveBadge); ?></div>
                <?php endif; ?>
              </div>
            </div>
          <?php else: ?>
            <div class="text-muted">No effective Prompt of the Day is currently available. Add a schedule/default entry or enable fallback.</div>
          <?php endif; ?>
        </div>
      </div>
    </div>

    <div class="col-xl-6">
      <div class="card h-100 shadow-sm">
        <div class="card-body">
          <h5 class="card-title mb-3">Fallback Configuration</h5>
          <form method="POST" action="process_prompt_of_day.php" class="row g-3">
            <input type="hidden" name="csrf_token" value="<?php echo h($csrf); ?>">
            <input type="hidden" name="action" value="save_config">

            <div class="col-12">
              <div class="form-check form-switch">
                <input class="form-check-input" type="checkbox" role="switch" id="allow_featured_fallback" name="allow_featured_fallback" value="1" <?php echo !empty($config['allow_featured_fallback']) ? 'checked' : ''; ?>>
                <label class="form-check-label" for="allow_featured_fallback">Allow featured prompt fallback when schedule/default is missing</label>
              </div>
            </div>

            <div class="col-12">
              <div class="form-check form-switch">
                <input class="form-check-input" type="checkbox" role="switch" id="enable_legacy_daily_fallback" name="enable_legacy_daily_fallback" value="1" <?php echo !empty($config['enable_legacy_daily_fallback']) ? 'checked' : ''; ?>>
                <label class="form-check-label" for="enable_legacy_daily_fallback">Allow legacy <code>daily_featured_prompts</code> fallback</label>
              </div>
            </div>

            <div class="col-12">
              <div class="form-check form-switch">
                <input class="form-check-input" type="checkbox" role="switch" id="allow_premium_prompts" name="allow_premium_prompts" value="1" <?php echo !empty($config['allow_premium_prompts']) ? 'checked' : ''; ?>>
                <label class="form-check-label" for="allow_premium_prompts">Allow premium prompts in Prompt of the Day</label>
              </div>
            </div>

            <div class="col-md-6">
              <label class="form-label">Featured fallback mode</label>
              <select class="form-select" name="featured_fallback_mode">
                <?php $fallbackMode = strtoupper((string)($config['featured_fallback_mode'] ?? 'NORMAL')); ?>
                <option value="NORMAL" <?php echo $fallbackMode === 'NORMAL' ? 'selected' : ''; ?>>NORMAL</option>
                <option value="DISCOUNT" <?php echo $fallbackMode === 'DISCOUNT' ? 'selected' : ''; ?>>DISCOUNT</option>
                <option value="FREE" <?php echo $fallbackMode === 'FREE' ? 'selected' : ''; ?>>FREE</option>
              </select>
            </div>

            <div class="col-md-6">
              <label class="form-label">Featured fallback discount (credits)</label>
              <input type="number" min="0" max="100000" class="form-control" name="featured_fallback_discount_cost_points" value="<?php echo (int)($config['featured_fallback_discount_cost_points'] ?? 0); ?>">
            </div>

            <div class="col-12">
              <label class="form-label">Default badge text</label>
              <input type="text" maxlength="80" class="form-control" name="default_badge_text" value="<?php echo h((string)($config['default_badge_text'] ?? '')); ?>" placeholder="Today's Pick">
            </div>

            <div class="col-12">
              <button type="submit" class="btn btn-primary">Save Configuration</button>
            </div>
          </form>
        </div>
      </div>
    </div>
  </div>

  <?php
  $formEntry = $editableEntry ?: [
      'id' => 0,
      'prompt_id' => '',
      'title_override' => '',
      'subtitle_override' => '',
      'badge_text' => '',
      'start_date' => '',
      'end_date' => '',
      'mode' => 'NORMAL',
      'discount_cost_points' => 0,
      'priority' => 0,
      'is_default' => 0,
      'is_active' => 1,
  ];
  ?>

  <div class="card shadow-sm mb-4">
    <div class="card-body">
      <div class="d-flex justify-content-between align-items-center mb-3">
        <h5 class="card-title mb-0"><?php echo (int)$formEntry['id'] > 0 ? 'Edit POTD Entry #' . (int)$formEntry['id'] : 'Create New POTD Entry'; ?></h5>
        <?php if ((int)$formEntry['id'] > 0): ?>
          <a href="prompt_of_day_management.php" class="btn btn-outline-secondary btn-sm">Cancel Edit</a>
        <?php endif; ?>
      </div>

      <form method="POST" action="process_prompt_of_day.php" class="row g-3" id="potd-entry-form">
        <input type="hidden" name="csrf_token" value="<?php echo h($csrf); ?>">
        <input type="hidden" name="action" value="save_entry">
        <input type="hidden" name="entry_id" value="<?php echo (int)$formEntry['id']; ?>">

        <div class="col-lg-7">
          <label class="form-label">Select prompt (published only)</label>
          <select class="form-select" name="prompt_id" id="prompt_id" required>
            <option value="">Select prompt...</option>
            <?php foreach ($publishedPrompts as $prompt): ?>
              <?php
              $pid = (int)$prompt['id'];
              $selected = ((int)$formEntry['prompt_id'] === $pid) ? 'selected' : '';
              $img = image_preview_url($prompt['image_url1'] ?: $prompt['image_url2']);
              $tier = strtoupper((string)($prompt['tier'] ?? 'FREE'));
              $title = (string)($prompt['title'] ?? 'Untitled prompt');
              $short = (string)($prompt['short_description'] ?? '');
              $cat = (string)($prompt['category_name'] ?? 'Uncategorized');
              ?>
              <option
                value="<?php echo $pid; ?>"
                <?php echo $selected; ?>
                data-title="<?php echo h($title); ?>"
                data-short="<?php echo h($short); ?>"
                data-tier="<?php echo h($tier); ?>"
                data-category="<?php echo h($cat); ?>"
                data-featured="<?php echo !empty($prompt['is_featured']) ? '1' : '0'; ?>"
                data-image="<?php echo h($img); ?>"
              >
                #<?php echo $pid; ?> - <?php echo h($title); ?> [<?php echo h($tier); ?>]
              </option>
            <?php endforeach; ?>
          </select>
          <div class="form-text">Draft/blocked/archived prompts are excluded automatically.</div>
        </div>

        <div class="col-lg-5">
          <div class="border rounded p-2 h-100" id="prompt-preview-card">
            <div class="small text-muted mb-1">Prompt preview</div>
            <div id="prompt-preview-empty" class="text-muted">Select a prompt to preview.</div>
            <div id="prompt-preview-content" class="d-none">
              <div class="d-flex gap-2 align-items-start">
                <img id="preview-image" src="" alt="Prompt" class="rounded border" style="width:72px;height:72px;object-fit:cover;">
                <div class="small">
                  <div class="fw-semibold" id="preview-title"></div>
                  <div class="text-muted" id="preview-category"></div>
                  <div class="mt-1">
                    <span class="badge bg-secondary" id="preview-tier"></span>
                    <span class="badge bg-info text-dark d-none" id="preview-featured">Featured</span>
                  </div>
                </div>
              </div>
              <div class="small mt-2 text-muted" id="preview-short"></div>
            </div>
          </div>
        </div>

        <div class="col-md-4">
          <div class="form-check">
            <input class="form-check-input" type="checkbox" value="1" id="is_default" name="is_default" <?php echo ((int)$formEntry['is_default'] === 1) ? 'checked' : ''; ?>>
            <label class="form-check-label" for="is_default">Use as default fallback entry</label>
          </div>
        </div>

        <div class="col-md-4">
          <div class="form-check">
            <input class="form-check-input" type="checkbox" value="1" id="is_active" name="is_active" <?php echo ((int)$formEntry['is_active'] === 1) ? 'checked' : ''; ?>>
            <label class="form-check-label" for="is_active">Active</label>
          </div>
        </div>

        <div class="col-md-4">
          <label class="form-label">Priority</label>
          <input type="number" min="-9999" max="9999" class="form-control" name="priority" value="<?php echo (int)$formEntry['priority']; ?>">
          <div class="form-text">Higher priority wins if overlap exists.</div>
        </div>

        <div class="col-md-6 schedule-field">
          <label class="form-label">Start date</label>
          <input type="date" class="form-control" name="start_date" id="start_date" value="<?php echo h((string)$formEntry['start_date']); ?>">
        </div>

        <div class="col-md-6 schedule-field">
          <label class="form-label">End date (optional)</label>
          <input type="date" class="form-control" name="end_date" id="end_date" value="<?php echo h((string)$formEntry['end_date']); ?>">
        </div>

        <div class="col-md-4">
          <label class="form-label">Access mode</label>
          <?php $entryMode = strtoupper((string)$formEntry['mode']); ?>
          <select class="form-select" name="mode" id="entry_mode">
            <option value="NORMAL" <?php echo $entryMode === 'NORMAL' ? 'selected' : ''; ?>>NORMAL</option>
            <option value="DISCOUNT" <?php echo $entryMode === 'DISCOUNT' ? 'selected' : ''; ?>>DISCOUNT</option>
            <option value="FREE" <?php echo $entryMode === 'FREE' ? 'selected' : ''; ?>>FREE</option>
          </select>
        </div>

        <div class="col-md-4">
          <label class="form-label">Discount cost (credits)</label>
          <input type="number" min="0" max="100000" class="form-control" id="discount_cost_points" name="discount_cost_points" value="<?php echo (int)$formEntry['discount_cost_points']; ?>">
        </div>

        <div class="col-md-4">
          <label class="form-label">Badge text (optional)</label>
          <input type="text" maxlength="80" class="form-control" name="badge_text" value="<?php echo h((string)$formEntry['badge_text']); ?>" placeholder="Today's Pick">
        </div>

        <div class="col-md-6">
          <label class="form-label">Title override (optional)</label>
          <input type="text" maxlength="255" class="form-control" name="title_override" value="<?php echo h((string)$formEntry['title_override']); ?>" placeholder="Custom POTD title">
        </div>

        <div class="col-md-6">
          <label class="form-label">Subtitle override (optional)</label>
          <input type="text" maxlength="255" class="form-control" name="subtitle_override" value="<?php echo h((string)$formEntry['subtitle_override']); ?>" placeholder="Custom subtitle shown on rewards card">
        </div>

        <div class="col-12">
          <button type="submit" class="btn btn-success"><?php echo (int)$formEntry['id'] > 0 ? 'Update Entry' : 'Create Entry'; ?></button>
        </div>
      </form>
    </div>
  </div>

  <?php
  $primaryRows = [];
  $secondaryRows = [];
  $liveEntryId = (int)($liveOffer['entry_id'] ?? 0);

  foreach ($entries as $entry) {
      $state = entry_state($entry, $today);
      if (in_array($state, ['active_today', 'upcoming', 'default'], true)) {
          $primaryRows[] = [$entry, $state];
      } else {
          $secondaryRows[] = [$entry, $state];
      }
  }
  ?>

  <div class="row g-3">
    <div class="col-12">
      <div class="card shadow-sm">
        <div class="card-body">
          <h5 class="card-title">Current and Upcoming Entries</h5>
          <div class="table-responsive">
            <table class="table table-sm align-middle">
              <thead>
                <tr>
                  <th>ID</th>
                  <th>Status</th>
                  <th>Prompt</th>
                  <th>Schedule</th>
                  <th>Mode</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                <?php if (empty($primaryRows)): ?>
                  <tr><td colspan="6" class="text-center text-muted py-3">No current/upcoming entries yet.</td></tr>
                <?php else: ?>
                  <?php foreach ($primaryRows as $bundle): ?>
                    <?php
                    [$entry, $state] = $bundle;
                    $eid = (int)$entry['id'];
                    $isLive = ($liveEntryId > 0 && $liveEntryId === $eid);
                    $promptTitle = trim((string)($entry['prompt_title'] ?? ''));
                    if ($promptTitle === '') {
                        $promptTitle = '[Prompt missing/unpublished]';
                    }
                    $tier = strtoupper((string)($entry['prompt_tier'] ?? 'FREE'));
                    $scheduleText = ((int)$entry['is_default'] === 1)
                        ? 'Default fallback'
                        : ((string)$entry['start_date'] . ((string)$entry['end_date'] !== '' ? ' to ' . (string)$entry['end_date'] : ' onward'));
                    ?>
                    <tr>
                      <td>#<?php echo $eid; ?></td>
                      <td>
                        <?php if ($isLive): ?><span class="badge bg-success">LIVE</span><?php endif; ?>
                        <?php if ($state === 'upcoming'): ?><span class="badge bg-primary">UPCOMING</span><?php endif; ?>
                        <?php if ($state === 'default'): ?><span class="badge bg-info text-dark">DEFAULT</span><?php endif; ?>
                        <?php if ($state === 'active_today' && !$isLive): ?><span class="badge bg-warning text-dark">ACTIVE</span><?php endif; ?>
                      </td>
                      <td>
                        <div class="fw-semibold"><?php echo h($promptTitle); ?></div>
                        <div class="small text-muted">
                          Prompt #<?php echo (int)$entry['prompt_id']; ?> | <?php echo h($tier); ?> | <?php echo h((string)($entry['category_name'] ?? 'Uncategorized')); ?>
                        </div>
                      </td>
                      <td><?php echo h($scheduleText); ?></td>
                      <td>
                        <span class="badge bg-dark"><?php echo h((string)$entry['mode']); ?></span>
                        <?php if ((int)$entry['discount_cost_points'] > 0): ?>
                          <span class="badge bg-secondary"><?php echo (int)$entry['discount_cost_points']; ?> credits</span>
                        <?php endif; ?>
                      </td>
                      <td>
                        <div class="d-flex flex-wrap gap-1">
                          <a class="btn btn-sm btn-outline-primary" href="prompt_of_day_management.php?edit_id=<?php echo $eid; ?>">Edit</a>

                          <form method="POST" action="process_prompt_of_day.php" class="d-inline">
                            <input type="hidden" name="csrf_token" value="<?php echo h($csrf); ?>">
                            <input type="hidden" name="action" value="toggle_entry">
                            <input type="hidden" name="entry_id" value="<?php echo $eid; ?>">
                            <input type="hidden" name="next_active" value="<?php echo ((int)$entry['is_active'] === 1) ? '0' : '1'; ?>">
                            <button class="btn btn-sm <?php echo ((int)$entry['is_active'] === 1) ? 'btn-outline-warning' : 'btn-outline-success'; ?>" type="submit">
                              <?php echo ((int)$entry['is_active'] === 1) ? 'Deactivate' : 'Activate'; ?>
                            </button>
                          </form>

                          <form method="POST" action="process_prompt_of_day.php" class="d-inline" onsubmit="return confirm('Delete this Prompt of the Day entry?');">
                            <input type="hidden" name="csrf_token" value="<?php echo h($csrf); ?>">
                            <input type="hidden" name="action" value="delete_entry">
                            <input type="hidden" name="entry_id" value="<?php echo $eid; ?>">
                            <button class="btn btn-sm btn-outline-danger" type="submit">Delete</button>
                          </form>
                        </div>
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

    <div class="col-12">
      <div class="card shadow-sm">
        <div class="card-body">
          <h5 class="card-title">Past and Inactive History</h5>
          <div class="table-responsive">
            <table class="table table-sm align-middle">
              <thead>
                <tr>
                  <th>ID</th>
                  <th>Status</th>
                  <th>Prompt</th>
                  <th>Schedule</th>
                  <th>Mode</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                <?php if (empty($secondaryRows)): ?>
                  <tr><td colspan="6" class="text-center text-muted py-3">No past/inactive entries.</td></tr>
                <?php else: ?>
                  <?php foreach ($secondaryRows as $bundle): ?>
                    <?php
                    [$entry, $state] = $bundle;
                    $eid = (int)$entry['id'];
                    $promptTitle = trim((string)($entry['prompt_title'] ?? ''));
                    if ($promptTitle === '') {
                        $promptTitle = '[Prompt missing/unpublished]';
                    }
                    $scheduleText = ((int)$entry['is_default'] === 1)
                        ? 'Default fallback'
                        : ((string)$entry['start_date'] . ((string)$entry['end_date'] !== '' ? ' to ' . (string)$entry['end_date'] : ' onward'));
                    ?>
                    <tr>
                      <td>#<?php echo $eid; ?></td>
                      <td>
                        <?php if ($state === 'past'): ?><span class="badge bg-secondary">PAST</span><?php endif; ?>
                        <?php if ($state === 'inactive'): ?><span class="badge bg-light text-dark border">INACTIVE</span><?php endif; ?>
                      </td>
                      <td>
                        <div class="fw-semibold"><?php echo h($promptTitle); ?></div>
                        <div class="small text-muted">Prompt #<?php echo (int)$entry['prompt_id']; ?></div>
                      </td>
                      <td><?php echo h($scheduleText); ?></td>
                      <td>
                        <span class="badge bg-dark"><?php echo h((string)$entry['mode']); ?></span>
                        <?php if ((int)$entry['discount_cost_points'] > 0): ?>
                          <span class="badge bg-secondary"><?php echo (int)$entry['discount_cost_points']; ?> credits</span>
                        <?php endif; ?>
                      </td>
                      <td>
                        <div class="d-flex flex-wrap gap-1">
                          <a class="btn btn-sm btn-outline-primary" href="prompt_of_day_management.php?edit_id=<?php echo $eid; ?>">Edit</a>

                          <form method="POST" action="process_prompt_of_day.php" class="d-inline">
                            <input type="hidden" name="csrf_token" value="<?php echo h($csrf); ?>">
                            <input type="hidden" name="action" value="toggle_entry">
                            <input type="hidden" name="entry_id" value="<?php echo $eid; ?>">
                            <input type="hidden" name="next_active" value="<?php echo ((int)$entry['is_active'] === 1) ? '0' : '1'; ?>">
                            <button class="btn btn-sm <?php echo ((int)$entry['is_active'] === 1) ? 'btn-outline-warning' : 'btn-outline-success'; ?>" type="submit">
                              <?php echo ((int)$entry['is_active'] === 1) ? 'Deactivate' : 'Activate'; ?>
                            </button>
                          </form>

                          <form method="POST" action="process_prompt_of_day.php" class="d-inline" onsubmit="return confirm('Delete this Prompt of the Day entry?');">
                            <input type="hidden" name="csrf_token" value="<?php echo h($csrf); ?>">
                            <input type="hidden" name="action" value="delete_entry">
                            <input type="hidden" name="entry_id" value="<?php echo $eid; ?>">
                            <button class="btn btn-sm btn-outline-danger" type="submit">Delete</button>
                          </form>
                        </div>
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
</div>

<script>
(function () {
  var select = document.getElementById('prompt_id');
  var previewEmpty = document.getElementById('prompt-preview-empty');
  var previewContent = document.getElementById('prompt-preview-content');
  var previewImage = document.getElementById('preview-image');
  var previewTitle = document.getElementById('preview-title');
  var previewCategory = document.getElementById('preview-category');
  var previewTier = document.getElementById('preview-tier');
  var previewFeatured = document.getElementById('preview-featured');
  var previewShort = document.getElementById('preview-short');

  var isDefaultCheckbox = document.getElementById('is_default');
  var scheduleFields = document.querySelectorAll('.schedule-field input');
  var startDateInput = document.getElementById('start_date');
  var modeInput = document.getElementById('entry_mode');
  var discountInput = document.getElementById('discount_cost_points');

  function refreshPromptPreview() {
    if (!select) return;
    var option = select.options[select.selectedIndex];
    if (!option || !option.value) {
      previewEmpty.classList.remove('d-none');
      previewContent.classList.add('d-none');
      return;
    }

    previewTitle.textContent = option.dataset.title || '';
    previewCategory.textContent = (option.dataset.category || 'Uncategorized') + ' | Prompt #' + option.value;
    previewTier.textContent = option.dataset.tier || 'FREE';
    previewShort.textContent = option.dataset.short || '';

    var image = option.dataset.image || '';
    if (image) {
      previewImage.src = image;
      previewImage.classList.remove('d-none');
    } else {
      previewImage.src = '';
      previewImage.classList.add('d-none');
    }

    if ((option.dataset.featured || '0') === '1') {
      previewFeatured.classList.remove('d-none');
    } else {
      previewFeatured.classList.add('d-none');
    }

    previewEmpty.classList.add('d-none');
    previewContent.classList.remove('d-none');
  }

  function refreshScheduleState() {
    if (!isDefaultCheckbox) return;
    var disableSchedule = isDefaultCheckbox.checked;
    scheduleFields.forEach(function (field) {
      field.disabled = disableSchedule;
      field.required = !disableSchedule && field.id === 'start_date';
      if (disableSchedule) {
        field.value = '';
      }
    });
  }

  function refreshDiscountState() {
    if (!modeInput || !discountInput) return;
    var isDiscount = (modeInput.value || 'NORMAL') === 'DISCOUNT';
    discountInput.disabled = !isDiscount;
    if (!isDiscount) {
      discountInput.value = '0';
    }
  }

  if (select) {
    select.addEventListener('change', refreshPromptPreview);
  }
  if (isDefaultCheckbox) {
    isDefaultCheckbox.addEventListener('change', refreshScheduleState);
  }
  if (modeInput) {
    modeInput.addEventListener('change', refreshDiscountState);
  }

  refreshPromptPreview();
  refreshScheduleState();
  refreshDiscountState();

  if (startDateInput && !startDateInput.value && (!isDefaultCheckbox || !isDefaultCheckbox.checked)) {
    startDateInput.value = '<?php echo h($today); ?>';
  }
})();
</script>

<?php include '../../includes/footer.php'; ?>
