<?php
// views/ai_posts/manage_ai_posts.php
session_start();
require '../../config.php';
if (!isset($_SESSION['admin'])) { header('Location: ../../login.php'); exit(); }

include '../../includes/header.php';
if (empty($_SESSION['csrf_token'])) $_SESSION['csrf_token'] = bin2hex(random_bytes(32));
$csrf = $_SESSION['csrf_token'];

// search & pagination
$q = trim($_GET['q'] ?? '');
$page = max(1, intval($_GET['page'] ?? 1));
$perPage = 15;
$offset = ($page - 1) * $perPage;
$filter = $_GET['filter'] ?? 'all'; // all | published | blocked | draft | archived | popular | featured | trending | premium | free

// Normalize search: allow users to search using hashtags like "#PicPose".
$qForBind = $q !== '' ? str_replace('#', '', $q) : '';

$where = "WHERE 1=1";
$params = [];
$types = '';

if ($q !== '') {
    // Search in title, tags, prompt_text
    $where .= " AND (p.title LIKE CONCAT('%', ?, '%') OR p.tags LIKE CONCAT('%', ?, '%') OR p.prompt_text LIKE CONCAT('%', ?, '%'))";
    $params[] = $qForBind; 
    $params[] = $qForBind; 
    $params[] = $qForBind;
    $types .= 'sss';
}

// Filter logic for popular / featured / trending
$orderBy = "p.priority DESC, p.created_at DESC"; // default ordering

if ($filter === 'popular') {
    $where .= " AND p.is_popular = 1";
} elseif ($filter === 'featured') {
    $where .= " AND p.is_featured = 1";
} elseif ($filter === 'premium') {
    $where .= " AND p.tier = 'PREMIUM'";
} elseif ($filter === 'free') {
    $where .= " AND p.tier = 'FREE'";
} elseif ($filter === 'published') {
    $where .= " AND p.status = 'published'";
} elseif ($filter === 'blocked') {
    $where .= " AND p.status = 'blocked'";
} elseif ($filter === 'draft') {
    $where .= " AND p.status = 'draft'";
} elseif ($filter === 'archived') {
    $where .= " AND p.status = 'archived'";
} elseif ($filter === 'trending') {
    // 🔥 Weighted Trending Score (same as API)
    // Only posts from the last 30 days are considered trending
    $where .= " AND p.status = 'published' 
                AND p.created_at >= DATE_SUB(NOW(), INTERVAL 30 DAY)";
    $orderBy = "((COALESCE(p.likes,0) * 3) + (COALESCE(p.favorites,0) * 5) + (COALESCE(p.views,0) * 1)) DESC, 
                p.created_at DESC";
}



// total
$totalSql = "SELECT COUNT(1) AS cnt FROM ai_posts p $where";
$stmt = $conn->prepare($totalSql);
if ($stmt === false) {
    error_log("manage_ai_posts prepare total failed: " . $conn->error);
    $_SESSION['message'] = 'Server error.';
    $_SESSION['message_type'] = 'danger';
    header('Location: manage_ai_posts.php'); exit();
}
if ($types) $stmt->bind_param($types, ...$params);
$stmt->execute();
$res = $stmt->get_result()->fetch_assoc();
$total = intval($res['cnt'] ?? 0);
$stmt->close();

// list
$listSql = "SELECT p.*, c.name AS category_name 
            FROM ai_posts p 
            LEFT JOIN categories c ON c.id = p.category_id 
            $where 
            ORDER BY $orderBy 
            LIMIT ? OFFSET ?";
$stmt = $conn->prepare($listSql);
if ($stmt === false) {
    error_log("manage_ai_posts prepare list failed: " . $conn->error);
    $_SESSION['message'] = 'Server error.';
    $_SESSION['message_type'] = 'danger';
    header('Location: manage_ai_posts.php'); exit();
}
if ($types) {
    $bindTypes = $types . 'ii';
    $params2 = array_merge($params, [$perPage, $offset]);
    $stmt->bind_param($bindTypes, ...$params2);
} else {
    $stmt->bind_param('ii', $perPage, $offset);
}
$stmt->execute();
$result = $stmt->get_result();

// helpers
function normalize_image_path($path) {
    $path = trim((string)$path);
    if ($path === '') return '';
    if (stripos($path, 'http://') === 0 || stripos($path, 'https://') === 0) return $path;
    if (strpos($path, '/') === 0) return '/' . ltrim($path, '/');
    if (stripos($path, 'uploads/') !== false) return '/' . ltrim($path, '/');
    return '/uploads/' . ltrim($path, '/');
}
function collect_images_from_row($row) {
    $images = [];
    foreach (['image_url1','image_url2'] as $col) {
        if (!empty($row[$col])) $images[] = $row[$col];
    }
    $images = array_values(array_unique(array_filter($images)));
    $images = array_map('normalize_image_path', $images);
    return $images;
}

// --- Optimized Quick Counts for Filter Buttons (Single Query for Performance) ---
$allCount = 0;
$publishedCount = 0;
$blockedCount = 0;
$draftCount = 0;
$archivedCount = 0;
$popularCount = 0;
$featuredCount = 0;
$trendingCount = 0;
$premiumCount = 0;
$freeCount = 0;

try {
    $res = $conn->query("
        SELECT 
            COUNT(*) AS allCount,
            SUM(CASE WHEN status = 'published' THEN 1 ELSE 0 END) AS publishedCount,
            SUM(CASE WHEN status = 'blocked' THEN 1 ELSE 0 END) AS blockedCount,
            SUM(CASE WHEN status = 'draft' THEN 1 ELSE 0 END) AS draftCount,
            SUM(CASE WHEN status = 'archived' THEN 1 ELSE 0 END) AS archivedCount,
            SUM(CASE WHEN status = 'published' AND is_popular = 1 THEN 1 ELSE 0 END) AS popularCount,
            SUM(CASE WHEN status = 'published' AND is_featured = 1 THEN 1 ELSE 0 END) AS featuredCount,
            SUM(CASE WHEN tier = 'PREMIUM' THEN 1 ELSE 0 END) AS premiumCount,
            SUM(CASE WHEN tier = 'FREE' THEN 1 ELSE 0 END) AS freeCount,
            SUM(
                CASE 
                    WHEN status = 'published'
                    AND created_at >= DATE_SUB(NOW(), INTERVAL 30 DAY)
                    AND ((COALESCE(likes,0)*3) + (COALESCE(favorites,0)*5) + (COALESCE(views,0)*1)) > 0
                    THEN 1 ELSE 0 
                END
            ) AS trendingCount
        FROM ai_posts
    ");
    if ($res && $r = $res->fetch_assoc()) {
        $allCount = (int)$r['allCount'];
        $publishedCount = (int)$r['publishedCount'];
        $blockedCount = (int)$r['blockedCount'];
        $draftCount = (int)$r['draftCount'];
        $archivedCount = (int)$r['archivedCount'];
        $popularCount = (int)$r['popularCount'];
        $featuredCount = (int)$r['featuredCount'];
        $trendingCount = (int)$r['trendingCount'];
        $premiumCount = (int)$r['premiumCount'];
        $freeCount = (int)$r['freeCount'];
    }
} catch (Throwable $ex) {
    error_log('Filter count error: ' . $ex->getMessage());
}




?>

<div class="container">
  <div class="d-flex flex-wrap justify-content-between align-items-center mb-3 gap-2">
    <div class="d-flex align-items-center gap-2">
      <h2 class="mb-0">Manage AI Prompts</h2>
      <div class="btn-group ms-2" role="group" aria-label="Filter">
        <?php
        $baseQuery = [];
        if ($q !== '') $baseQuery['q'] = $q;
        ?>
        <a href="?<?php echo http_build_query(array_merge($baseQuery, ['filter' => 'all'])); ?>"
           class="btn btn-sm <?php echo $filter === 'all' ? 'btn-primary' : 'btn-outline-primary'; ?>">
           All (<?= $allCount ?>)
        </a>

        <a href="?<?php echo http_build_query(array_merge($baseQuery, ['filter' => 'published'])); ?>"
           class="btn btn-sm <?php echo $filter === 'published' ? 'btn-success' : 'btn-outline-success'; ?>">
           Published (<?= $publishedCount ?>)
        </a>

        <a href="?<?php echo http_build_query(array_merge($baseQuery, ['filter' => 'blocked'])); ?>"
           class="btn btn-sm <?php echo $filter === 'blocked' ? 'btn-danger' : 'btn-outline-danger'; ?>">
           Blocked (<?= $blockedCount ?>)
        </a>

        <a href="?<?php echo http_build_query(array_merge($baseQuery, ['filter' => 'draft'])); ?>"
           class="btn btn-sm <?php echo $filter === 'draft' ? 'btn-secondary' : 'btn-outline-secondary'; ?>">
           Draft (<?= $draftCount ?>)
        </a>

        <a href="?<?php echo http_build_query(array_merge($baseQuery, ['filter' => 'archived'])); ?>"
           class="btn btn-sm <?php echo $filter === 'archived' ? 'btn-dark' : 'btn-outline-dark'; ?>">
           Archived (<?= $archivedCount ?>)
        </a>
        
        <a href="?<?php echo http_build_query(array_merge($baseQuery, ['filter' => 'popular'])); ?>"
           class="btn btn-sm <?php echo $filter === 'popular' ? 'btn-primary' : 'btn-outline-primary'; ?>"
           data-bs-toggle="tooltip" title="Manually marked as trending">
           🔥 Popular (<?= $popularCount ?>)
        </a>
        
        <a href="?<?php echo http_build_query(array_merge($baseQuery, ['filter' => 'featured'])); ?>"
           class="btn btn-sm <?php echo $filter === 'featured' ? 'btn-primary' : 'btn-outline-primary'; ?>"
           data-bs-toggle="tooltip" title="Featured manually by admin">
           ⭐ Featured (<?= $featuredCount ?>)
        </a>

        <a href="?<?php echo http_build_query(array_merge($baseQuery, ['filter' => 'premium'])); ?>"
           class="btn btn-sm <?php echo $filter === 'premium' ? 'btn-warning' : 'btn-outline-warning'; ?>">
           🔒 Premium (<?= $premiumCount ?>)
        </a>

        <a href="?<?php echo http_build_query(array_merge($baseQuery, ['filter' => 'free'])); ?>"
           class="btn btn-sm <?php echo $filter === 'free' ? 'btn-success' : 'btn-outline-success'; ?>">
           Free (<?= $freeCount ?>)
        </a>
        
        <a href="?<?php echo http_build_query(array_merge($baseQuery, ['filter' => 'trending'])); ?>"
        class="btn btn-sm <?php echo $filter === 'trending' ? 'btn-primary' : 'btn-outline-primary'; ?>"
        data-bs-toggle="tooltip" title="Weighted engagement (likes ×3, favorites ×5, views ×1) in last 30 days">
        📈 Trending (<?= $trendingCount ?>)
        </a>

      </div>
    </div>
    <a href="add_ai_post.php" class="btn btn-success">➕ Add AI Prompt</a>
  </div>

  <form class="mb-3" method="GET" action="">
    <input type="hidden" name="filter" value="<?php echo htmlspecialchars($filter); ?>">
    <div class="input-group">
      <input type="text" name="q" class="form-control" placeholder="Search title, prompt, tags (support #Tag)" value="<?php echo htmlspecialchars($q); ?>">
      <button class="btn btn-outline-secondary" type="submit">Search</button>
    </div>
  </form>

  <?php if(!empty($_SESSION['message'])): ?>
    <div class="alert alert-<?php echo htmlspecialchars($_SESSION['message_type'] ?? 'info'); ?>">
      <?php echo htmlspecialchars($_SESSION['message']); unset($_SESSION['message'], $_SESSION['message_type']); ?>
    </div>
  <?php endif; ?>

  <?php if ($filter === 'blocked'): ?>
    <div class="d-flex justify-content-between align-items-center mb-2">
      <div class="small text-muted">Select blocked prompts and republish to make them visible in the current app.</div>
      <button type="button" id="publishSelectedBtn" class="btn btn-sm btn-success">
        Publish Selected
      </button>
    </div>
  <?php endif; ?>

  <div class="card mb-3">
    <div class="card-body">
      <div class="row g-2 align-items-end">
        <div class="col-md-3">
          <label class="form-label mb-1">Bulk Action</label>
          <select id="bulkTierMode" class="form-select form-select-sm">
            <option value="">Select action...</option>
            <option value="set_free">Set FREE</option>
            <option value="set_premium">Set PREMIUM</option>
            <option value="set_premium_cost">Set PREMIUM + cost</option>
            <option value="set_premium_pack">Set premium pack</option>
          </select>
        </div>
        <div class="col-md-2" id="bulkCostWrap" style="display:none;">
          <label class="form-label mb-1">Cost</label>
          <input type="number" min="1" id="bulkCostInput" class="form-control form-control-sm" placeholder="200">
        </div>
        <div class="col-md-3" id="bulkPackWrap" style="display:none;">
          <label class="form-label mb-1">Pack</label>
          <input type="text" maxlength="40" id="bulkPackInput" class="form-control form-control-sm" placeholder="portrait_pro">
        </div>
        <div class="col-md-2">
          <button type="button" id="applyBulkTierBtn" class="btn btn-sm btn-primary w-100">Apply</button>
        </div>
      </div>
      <div class="small text-muted mt-2">Select rows using checkboxes, then apply a bulk tier action.</div>
    </div>
  </div>

  <table class="table table-striped table-bordered">
    <thead>
      <tr>
        <th style="width:40px;">
          <input type="checkbox" id="selectAllRows">
        </th>
        <th>#</th>
        <th>Title / Short</th>
        <th>Category</th>
        <th>Tags</th>
        <th>Image</th>
        <th>Tier</th>
        <th>Unlock Cost</th>
        <th>Status</th>
        <th>Priority</th>
        <th>Actions</th>
      </tr>
    </thead>
    <tbody>
      <?php $i = $offset + 1; while ($row = $result->fetch_assoc()):
          $images = collect_images_from_row($row);
          $thumb = $images[0] ?? '';
          $imagesAttr = htmlspecialchars(json_encode($images, JSON_HEX_APOS|JSON_HEX_QUOT), ENT_QUOTES);
          $tier = strtoupper((string)($row['tier'] ?? 'FREE'));
          $unlockCost = (int)($row['premium_unlock_cost_points'] ?? 0);
          if ($tier === 'PREMIUM' && $unlockCost <= 0) $unlockCost = 200;

          // prepare tags array from DB
          $tagsOutHtml = '';
          if (!empty($row['tags'])) {
              $dec = json_decode($row['tags'], true);
              if (json_last_error() === JSON_ERROR_NONE && is_array($dec)) {
                  $tagsArr = $dec;
              } else {
                  $tagsArr = array_filter(array_map('trim', explode(',', $row['tags'])));
              }

              foreach ($tagsArr as $t) {
                  $t = trim((string)$t);
                  if ($t === '') continue;
                  $tClean = ltrim($t, '#');
                  $link = '?q=' . rawurlencode('#' . $tClean);
                  $tagsOutHtml .= '<a href="' . htmlspecialchars($link, ENT_QUOTES) . '" class="badge bg-secondary text-decoration-none me-1">#' . htmlspecialchars($tClean) . '</a>';
              }
          }
      ?>
<tr>
  <td>
    <input type="checkbox" class="row-checkbox <?php echo (($row['status'] ?? '') === 'blocked') ? 'blocked-checkbox' : ''; ?>" value="<?php echo intval($row['id']); ?>">
  </td>
  <td><?php echo $i++; ?></td>
  <td>
    <strong><?php echo htmlspecialchars($row['title']); ?></strong>

    <?php if (!empty($row['is_popular'])): ?>
      <span class="badge bg-warning text-dark ms-1">🔥 Popular</span>
    <?php endif; ?>

    <?php if (!empty($row['is_featured'])): ?>
      <span class="badge bg-info text-dark ms-1">⭐ Featured</span>
    <?php endif; ?>

    <?php if ($tier === 'PREMIUM'): ?>
      <span class="badge bg-warning text-dark ms-1">🔒 Premium</span>
      <span class="badge bg-light text-dark ms-1">Cost: <?= $unlockCost ?> pts</span>
    <?php endif; ?>

    <?php
      // Compute total engagement
      $likes = (int)($row['likes'] ?? 0);
      $favorites = (int)($row['favorites'] ?? 0);
      $views = (int)($row['views'] ?? 0);
$trendingScore = ($likes * 3) + ($favorites * 5) + ($views * 1);

if ($trendingScore > 0):
    $progressValue = min(100, $trendingScore);
?>
  <span class="badge bg-danger text-light ms-1">
    📈 Score <?= $trendingScore ?>
  </span>

      <div class="progress mt-1" style="height: 6px; max-width: 120px;">
        <div class="progress-bar bg-danger" role="progressbar" 
             style="width: <?= $progressValue ?>%;" 
             aria-valuenow="<?= $progressValue ?>" aria-valuemin="0" aria-valuemax="100">
        </div>
      </div>
    <?php endif; ?>

    <br>
    <small><?php echo htmlspecialchars(substr($row['short_description'] ?? $row['prompt_text'], 0, 140)); ?></small>
  </td>

  <td><?php echo htmlspecialchars($row['category_name'] ?? '—'); ?></td>
  <td><?php echo $tagsOutHtml !== '' ? $tagsOutHtml : '<span class="text-muted">—</span>'; ?></td>

  <td style="width:90px;">
    <?php if ($thumb): ?>
      <button type="button" class="btn p-0 post-thumb" 
              data-images="<?php echo $imagesAttr; ?>" 
              data-title="<?php echo htmlspecialchars($row['title'], ENT_QUOTES); ?>" 
              style="border:none;background:transparent;">
        <img src="<?php echo htmlspecialchars($thumb); ?>" 
             style="width:64px;height:48px;object-fit:cover;border-radius:4px;">
      </button>
    <?php else: ?>
      <div class="text-muted">No image</div>
    <?php endif; ?>
  </td>

  <td>
    <?php if ($tier === 'PREMIUM'): ?>
      <span class="badge bg-warning text-dark">PREMIUM</span>
    <?php else: ?>
      <span class="badge bg-success">FREE</span>
    <?php endif; ?>
  </td>

  <td>
    <?php if ($tier === 'PREMIUM'): ?>
      <?= $unlockCost ?> pts
    <?php else: ?>
      <span class="text-muted">—</span>
    <?php endif; ?>
  </td>

  <td>
    <?php
      $status = strtolower((string)($row['status'] ?? ''));
      $statusClass = 'bg-secondary';
      if ($status === 'published') $statusClass = 'bg-success';
      elseif ($status === 'blocked') $statusClass = 'bg-danger';
      elseif ($status === 'draft') $statusClass = 'bg-warning text-dark';
      elseif ($status === 'archived') $statusClass = 'bg-dark';
    ?>
    <span class="badge <?php echo $statusClass; ?>"><?php echo htmlspecialchars($row['status']); ?></span>
  </td>
  <td><?php echo intval($row['priority']); ?></td>

  <td style="white-space:nowrap;">
    <a href="edit_ai_post.php?id=<?php echo $row['id']; ?>" class="btn btn-sm btn-primary">Edit</a>
    <?php if (($row['status'] ?? '') === 'blocked'): ?>
      <form method="POST" action="process_ai_post.php" style="display:inline-block;" onsubmit="return confirm('Republish this blocked prompt?');">
        <input type="hidden" name="action" value="publish_single">
        <input type="hidden" name="id" value="<?php echo intval($row['id']); ?>">
        <input type="hidden" name="csrf_token" value="<?php echo htmlspecialchars($csrf); ?>">
        <button type="submit" class="btn btn-sm btn-success">Publish</button>
      </form>
    <?php endif; ?>
    <form method="POST" action="process_ai_post.php" style="display:inline-block;" onsubmit="return confirm('Delete this AI Prompt?');">
      <input type="hidden" name="action" value="delete">
      <input type="hidden" name="id" value="<?php echo $row['id']; ?>">
      <input type="hidden" name="csrf_token" value="<?php echo htmlspecialchars($csrf); ?>">
      <button type="submit" class="btn btn-sm btn-danger">Delete</button>
    </form>
  </td>
</tr>

      <?php endwhile; ?>
    </tbody>
  </table>


  
  <?php
$totalPages = max(1, ceil($total / $perPage));
$maxPagesToShow = 7;

if ($totalPages > 1):

    // Calculate current item range
    $startItem = $offset + 1;
    $endItem   = min($offset + $perPage, $total);

    // Page window logic
    $startPage = max(1, $page - floor($maxPagesToShow / 3));
    $endPage   = min($totalPages, $startPage + $maxPagesToShow - 1);

    if (($endPage - $startPage + 1) < $maxPagesToShow) {
        $startPage = max(1, $endPage - $maxPagesToShow + 1);
    }
?>

<!-- 🔍 Info Text -->
<div class="text-center text-muted small mb-2">
  Showing <strong><?= $startItem ?>–<?= $endItem ?></strong> of 
  <strong><?= $total ?></strong> prompts 
  (Page <?= $page ?> of <?= $totalPages ?>)
</div>

<nav aria-label="AI Prompts Pagination">
  <ul class="pagination pagination-md justify-content-center flex-wrap">

    <!-- First -->
    <li class="page-item <?= $page == 1 ? 'disabled' : '' ?>">
      <a class="page-link" href="?<?= http_build_query(array_merge($_GET, ['page' => 1])) ?>">
        First
      </a>
    </li>

    <!-- Previous -->
    <li class="page-item <?= $page <= 1 ? 'disabled' : '' ?>">
      <a class="page-link" href="?<?= http_build_query(array_merge($_GET, ['page' => $page - 1])) ?>">
        &laquo;
      </a>
    </li>

    <!-- Leading Ellipsis -->
    <?php if ($startPage > 1): ?>
      <li class="page-item">
        <a class="page-link" href="?<?= http_build_query(array_merge($_GET, ['page' => 1])) ?>">1</a>
      </li>
      <?php if ($startPage > 2): ?>
        <li class="page-item disabled"><span class="page-link">…</span></li>
      <?php endif; ?>
    <?php endif; ?>

    <!-- Page Numbers -->
    <?php for ($p = $startPage; $p <= $endPage; $p++): ?>
      <li class="page-item <?= $p == $page ? 'active' : '' ?>">
        <a class="page-link" href="?<?= http_build_query(array_merge($_GET, ['page' => $p])) ?>">
          <?= $p ?>
        </a>
      </li>
    <?php endfor; ?>

    <!-- Trailing Ellipsis -->
    <?php if ($endPage < $totalPages): ?>
      <?php if ($endPage < $totalPages - 1): ?>
        <li class="page-item disabled"><span class="page-link">…</span></li>
      <?php endif; ?>
      <li class="page-item">
        <a class="page-link" href="?<?= http_build_query(array_merge($_GET, ['page' => $totalPages])) ?>">
          <?= $totalPages ?>
        </a>
      </li>
    <?php endif; ?>

    <!-- Next -->
    <li class="page-item <?= $page >= $totalPages ? 'disabled' : '' ?>">
      <a class="page-link" href="?<?= http_build_query(array_merge($_GET, ['page' => $page + 1])) ?>">
        &raquo;
      </a>
    </li>

    <!-- Last -->
    <li class="page-item <?= $page == $totalPages ? 'disabled' : '' ?>">
      <a class="page-link" href="?<?= http_build_query(array_merge($_GET, ['page' => $totalPages])) ?>">
        Last
      </a>
    </li>

  </ul>
</nav>
<?php endif; ?>




<!-- Images Modal -->
<div class="modal fade" id="imagesDialog" tabindex="-1" aria-hidden="true">
  <div class="modal-dialog modal-dialog-centered modal-lg">
    <div class="modal-content">
      <div class="modal-header">
        <h6 class="modal-title">Images</h6>
        <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
      </div>
      <div class="modal-body">
        <div id="dialogPostTitle" class="mb-2 small text-muted"></div>
        <div id="dialogPreview" class="text-center mb-3">
          <img id="dialogPreviewImg" src="" alt="" style="max-width:100%; max-height:60vh; border-radius:6px; display:none;">
          <div id="dialogNoImage" class="alert alert-secondary" style="display:none;">No images available.</div>
        </div>
        <div id="dialogThumbs" class="d-flex flex-wrap gap-2 justify-content-start"></div>
      </div>
      <div class="modal-footer">
        <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Close</button>
      </div>
    </div>
  </div>
</div>

<form method="POST" action="process_ai_post.php" id="bulkPublishForm" style="display:none;">
  <input type="hidden" name="action" value="publish_selected">
  <input type="hidden" name="ids" id="bulkPublishIds" value="">
  <input type="hidden" name="csrf_token" value="<?php echo htmlspecialchars($csrf); ?>">
</form>

<form method="POST" action="process_ai_post.php" id="bulkTierForm" style="display:none;">
  <input type="hidden" name="action" value="bulk_update_tier">
  <input type="hidden" name="tier" id="bulkTierValue" value="">
  <input type="hidden" name="cost" id="bulkTierCostValue" value="">
  <input type="hidden" name="pack" id="bulkTierPackValue" value="">
  <input type="hidden" name="csrf_token" value="<?php echo htmlspecialchars($csrf); ?>">
  <div id="bulkTierIdsContainer"></div>
</form>

<script>
document.addEventListener('DOMContentLoaded', function() {
  var selectAll = document.getElementById('selectAllRows');
  var publishBtn = document.getElementById('publishSelectedBtn');
  var bulkTierMode = document.getElementById('bulkTierMode');
  var bulkCostWrap = document.getElementById('bulkCostWrap');
  var bulkPackWrap = document.getElementById('bulkPackWrap');
  var bulkCostInput = document.getElementById('bulkCostInput');
  var bulkPackInput = document.getElementById('bulkPackInput');
  var applyBulkTierBtn = document.getElementById('applyBulkTierBtn');

  if (selectAll) {
    selectAll.addEventListener('change', function() {
      document.querySelectorAll('.row-checkbox').forEach(function(chk) {
        chk.checked = selectAll.checked;
      });
    });
  }

  function getSelectedRowIds() {
    return Array.from(document.querySelectorAll('.row-checkbox:checked'))
      .map(function(chk) { return chk.value; })
      .filter(Boolean);
  }

  function refreshBulkFieldVisibility() {
    var mode = bulkTierMode ? bulkTierMode.value : '';
    if (bulkCostWrap) bulkCostWrap.style.display = (mode === 'set_premium_cost') ? '' : 'none';
    if (bulkPackWrap) bulkPackWrap.style.display = (mode === 'set_premium_pack') ? '' : 'none';
  }

  if (bulkTierMode) {
    bulkTierMode.addEventListener('change', refreshBulkFieldVisibility);
    refreshBulkFieldVisibility();
  }

  if (applyBulkTierBtn) {
    applyBulkTierBtn.addEventListener('click', function() {
      var mode = bulkTierMode ? bulkTierMode.value : '';
      if (!mode) {
        alert('Please choose a bulk action.');
        return;
      }

      var ids = getSelectedRowIds();
      if (ids.length === 0) {
        alert('Please select at least one prompt.');
        return;
      }

      var tier = (mode === 'set_free') ? 'FREE' : 'PREMIUM';
      var cost = '';
      var pack = '';

      if (mode === 'set_premium_cost') {
        var parsedCost = parseInt((bulkCostInput ? bulkCostInput.value : '').trim(), 10);
        if (isNaN(parsedCost) || parsedCost <= 0) parsedCost = 200;
        cost = String(parsedCost);
      } else if (mode === 'set_premium') {
        cost = '200';
      }

      if (mode === 'set_premium_pack') {
        pack = (bulkPackInput ? bulkPackInput.value : '').trim();
      }

      if (!confirm('Apply this bulk tier update to ' + ids.length + ' prompt(s)?')) return;

      var tierField = document.getElementById('bulkTierValue');
      var costField = document.getElementById('bulkTierCostValue');
      var packField = document.getElementById('bulkTierPackValue');
      var idsContainer = document.getElementById('bulkTierIdsContainer');
      if (!tierField || !costField || !packField || !idsContainer) return;

      tierField.value = tier;
      costField.value = cost;
      packField.value = pack;
      idsContainer.innerHTML = '';

      ids.forEach(function(id) {
        var input = document.createElement('input');
        input.type = 'hidden';
        input.name = 'ids[]';
        input.value = id;
        idsContainer.appendChild(input);
      });

      document.getElementById('bulkTierForm').submit();
    });
  }

  if (publishBtn) {
    publishBtn.addEventListener('click', function() {
      var ids = Array.from(document.querySelectorAll('.blocked-checkbox:checked'))
        .map(function(chk) { return chk.value; })
        .filter(Boolean);

      if (ids.length === 0) {
        alert('Please select at least one blocked prompt.');
        return;
      }
      if (!confirm('Republish selected blocked prompt(s)?')) return;

      var idsField = document.getElementById('bulkPublishIds');
      if (!idsField) return;
      idsField.value = ids.join(',');
      document.getElementById('bulkPublishForm').submit();
    });
  }

  document.querySelectorAll('.post-thumb').forEach(function(btn) {
    btn.addEventListener('click', function() {
      var images = [];
      try { images = JSON.parse(this.getAttribute('data-images') || '[]'); } catch(e) { images = []; }
      var title = this.getAttribute('data-title') || '';
      var modalEl = document.getElementById('imagesDialog');
      var modal = bootstrap.Modal.getOrCreateInstance(modalEl);
      var previewImg = document.getElementById('dialogPreviewImg');
      var noImage = document.getElementById('dialogNoImage');
      var thumbs = document.getElementById('dialogThumbs');
      var postTitle = document.getElementById('dialogPostTitle');
      postTitle.textContent = title;

      thumbs.innerHTML = '';
      previewImg.style.display = 'none';
      previewImg.src = '';
      noImage.style.display = 'none';

      if (!images || images.length === 0) {
        noImage.style.display = 'block';
      } else {
        previewImg.src = images[0];
        previewImg.style.display = 'inline-block';
        images.forEach(function(url, idx) {
          var img = document.createElement('img');
          img.src = url;
          img.alt = 'img-' + idx;
          img.style.width = '90px';
          img.style.height = '62px';
          img.style.objectFit = 'cover';
          img.style.borderRadius = '6px';
          img.style.border = '1px solid #ddd';
          img.style.cursor = 'pointer';
          img.addEventListener('click', function(){ previewImg.src = url; });
          thumbs.appendChild(img);
        });
      }
      modal.show();
    });
  });

  var imagesDialog = document.getElementById('imagesDialog');
  imagesDialog.addEventListener('hidden.bs.modal', function() {
    document.getElementById('dialogThumbs').innerHTML = '';
    var previewImg = document.getElementById('dialogPreviewImg');
    previewImg.style.display = 'none';
    previewImg.src = '';
    document.getElementById('dialogNoImage').style.display = 'none';
    document.getElementById('dialogPostTitle').textContent = '';
  });
});
</script>

<?php
$stmt->close();
include '../../includes/footer.php';
?>
