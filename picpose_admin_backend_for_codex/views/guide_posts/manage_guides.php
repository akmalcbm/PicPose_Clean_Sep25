<?php
session_start();
require '../../config.php';
if (!isset($_SESSION['admin'])) { header('Location: ../../login.php'); exit(); }
if (empty($_SESSION['csrf_token'])) $_SESSION['csrf_token'] = bin2hex(random_bytes(32));
$csrf = $_SESSION['csrf_token'];
include '../../includes/header.php';

/* ===========================
   SEARCH & PAGINATION SETUP
=========================== */
$q = trim($_GET['q'] ?? '');
$page = max(1, intval($_GET['page'] ?? 1));
$perPage = 15;
$offset = ($page - 1) * $perPage;

// normalize hashtag search
$qForBind = $q !== '' ? str_replace('#', '', $q) : '';

$where = "WHERE 1=1";
$params = [];
$types = '';

if ($q !== '') {
    $where .= " AND (
        p.title LIKE CONCAT('%', ?, '%') OR
        p.tags LIKE CONCAT('%', ?, '%') OR
        p.content LIKE CONCAT('%', ?, '%')
    )";
    $params[] = $qForBind;
    $params[] = $qForBind;
    $params[] = $qForBind;
    $types .= 'sss';
}

/* ===========================
   TOTAL COUNT
=========================== */
$totalSql = "SELECT COUNT(1) AS cnt FROM guide_posts p $where";
$stmt = $conn->prepare($totalSql);
if ($types) $stmt->bind_param($types, ...$params);
$stmt->execute();
$total = (int)($stmt->get_result()->fetch_assoc()['cnt'] ?? 0);
$stmt->close();

/* ===========================
   LIST QUERY
=========================== */
$listSql = "
SELECT p.*, c.name AS category_name
FROM guide_posts p
LEFT JOIN categories c ON c.id = p.category_id
$where
ORDER BY p.priority DESC, p.created_at DESC
LIMIT ? OFFSET ?
";

$stmt = $conn->prepare($listSql);
if ($types) {
    $stmt->bind_param($types . 'ii', ...array_merge($params, [$perPage, $offset]));
} else {
    $stmt->bind_param('ii', $perPage, $offset);
}
$stmt->execute();
$result = $stmt->get_result();

/* ===========================
   IMAGE HELPERS
=========================== */
function normalize_image_path($path) {
    $path = trim((string)$path);
    if ($path === '') return '';
    if (preg_match('~^https?://~', $path)) return $path;
    return '/' . ltrim($path, '/');
}

function collect_images_from_row($row) {
    $images = [];
    foreach (['image_url1','image_url2','image_url3'] as $c) {
        if (!empty($row[$c])) $images[] = $row[$c];
    }
    if (!empty($row['images'])) {
        $arr = json_decode($row['images'], true);
        if (is_array($arr)) $images = array_merge($images, $arr);
    }
    return array_map('normalize_image_path', array_unique(array_filter($images)));
}
?>

<div class="container">

  <div class="d-flex justify-content-between align-items-center mb-3">
    <h2>Manage Guides</h2>
    <a href="add_guide_post.php" class="btn btn-success">➕ Add Guide</a>
  </div>

  <form class="mb-3">
    <div class="input-group">
      <input type="text" name="q" class="form-control"
             placeholder="Search title, content, tags (support #Tag)"
             value="<?= htmlspecialchars($q) ?>">
      <button class="btn btn-outline-secondary">Search</button>
    </div>
  </form>

  <table class="table table-bordered table-striped">
    <thead>
      <tr>
        <th>#</th>
        <th>Title / Short</th>
        <th>Category</th>
        <th>Tags</th>
        <th>Image</th>
        <th>Status</th>
        <th>Priority</th>
        <th>Actions</th>
      </tr>
    </thead>
    <tbody>
<?php
$i = $offset + 1;
while ($row = $result->fetch_assoc()):
$images = collect_images_from_row($row);
$thumb = $images[0] ?? '';
?>
<tr>
  <td><?= $i++ ?></td>
  <td>
    <strong><?= htmlspecialchars($row['title']) ?></strong><br>
    <small><?= htmlspecialchars(substr(strip_tags($row['content']), 0, 140)) ?></small>
  </td>
  <td><?= htmlspecialchars($row['category_name'] ?? '—') ?></td>
  <td>
<?php
if (!empty($row['tags'])) {
    $tags = json_decode($row['tags'], true);
    if (!is_array($tags)) $tags = explode(',', $row['tags']);
    foreach ($tags as $t) {
        $t = trim(ltrim($t, '#'));
        echo '<a href="?q=%23'.urlencode($t).'" class="badge bg-secondary me-1">#'.$t.'</a>';
    }
} else {
    echo '<span class="text-muted">—</span>';
}
?>
  </td>
  <td>
<?php if ($thumb): ?>
<img src="<?= htmlspecialchars($thumb) ?>" style="width:64px;height:48px;object-fit:cover">
<?php else: ?>
<span class="text-muted">No image</span>
<?php endif; ?>
  </td>
  <td><?= htmlspecialchars($row['status']) ?></td>
  <td><?= (int)$row['priority'] ?></td>
  <td>
    <a href="edit_guide_post.php?id=<?= $row['id'] ?>" class="btn btn-sm btn-primary">Edit</a>
    <form method="post" action="process_guide_post.php" style="display:inline">
      <input type="hidden" name="csrf_token" value="<?= htmlspecialchars($csrf) ?>">
      <input type="hidden" name="action" value="delete">
      <input type="hidden" name="id" value="<?= $row['id'] ?>">
      <button class="btn btn-sm btn-danger" onclick="return confirm('Delete guide?')">Delete</button>
    </form>
  </td>
</tr>
<?php endwhile; ?>
    </tbody>
  </table>

<?php
/* ===========================
   IMPROVED PAGINATION
=========================== */
$totalPages = max(1, ceil($total / $perPage));
$maxPagesToShow = 5;

if ($totalPages > 1):

$startItem = $offset + 1;
$endItem = min($offset + $perPage, $total);

$startPage = max(1, $page - floor($maxPagesToShow / 2));
$endPage = min($totalPages, $startPage + $maxPagesToShow - 1);
?>

<div class="text-center text-muted small mb-2">
Showing <strong><?= $startItem ?>–<?= $endItem ?></strong> of
<strong><?= $total ?></strong> guides
(Page <?= $page ?> of <?= $totalPages ?>)
</div>

<nav>
<ul class="pagination pagination-md justify-content-center flex-wrap">

<li class="page-item <?= $page==1?'disabled':'' ?>">
<a class="page-link" href="?<?= http_build_query(array_merge($_GET,['page'=>1])) ?>">First</a>
</li>

<li class="page-item <?= $page<=1?'disabled':'' ?>">
<a class="page-link" href="?<?= http_build_query(array_merge($_GET,['page'=>$page-1])) ?>">&laquo;</a>
</li>

<?php if ($startPage > 1): ?>
<li class="page-item"><a class="page-link" href="?<?= http_build_query(array_merge($_GET,['page'=>1])) ?>">1</a></li>
<?php if ($startPage > 2): ?>
<li class="page-item disabled"><span class="page-link">…</span></li>
<?php endif; endif; ?>

<?php for ($p=$startPage;$p<=$endPage;$p++): ?>
<li class="page-item <?= $p==$page?'active':'' ?>">
<a class="page-link" href="?<?= http_build_query(array_merge($_GET,['page'=>$p])) ?>"><?= $p ?></a>
</li>
<?php endfor; ?>

<?php if ($endPage < $totalPages): ?>
<?php if ($endPage < $totalPages-1): ?>
<li class="page-item disabled"><span class="page-link">…</span></li>
<?php endif; ?>
<li class="page-item"><a class="page-link" href="?<?= http_build_query(array_merge($_GET,['page'=>$totalPages])) ?>"><?= $totalPages ?></a></li>
<?php endif; ?>

<li class="page-item <?= $page>=$totalPages?'disabled':'' ?>">
<a class="page-link" href="?<?= http_build_query(array_merge($_GET,['page'=>$page+1])) ?>">&raquo;</a>
</li>

<li class="page-item <?= $page==$totalPages?'disabled':'' ?>">
<a class="page-link" href="?<?= http_build_query(array_merge($_GET,['page'=>$totalPages])) ?>">Last</a>
</li>

</ul>
</nav>
<?php endif; ?>

</div>

<?php
$stmt->close();
include '../../includes/footer.php';
?>
