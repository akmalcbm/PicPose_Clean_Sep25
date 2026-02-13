<?php
// includes/header.php - PicPose Admin (mobile-friendly sidebar toggle)
?>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <title>PicPose Admin Panel</title>

    <!-- Bootstrap 5 -->
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">

<?php
// ✅ Auto-detect protocol + host
$protocol = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') ? 'https' : 'http';
$host = $_SERVER['HTTP_HOST'];

// ✅ Always point to root admin asset folder (not relative to current view)
$cssPath = $protocol . '://' . $host . '/picpose_admin/assets/css/style.css';

// ✅ Optional safety: check if the file actually exists on the server
if (!file_exists($_SERVER['DOCUMENT_ROOT'] . '/assets/css/style.css')) {
    // fallback to root assets if admin folder is missing (for dev environments)
    $cssPath = $protocol . '://' . $host . '/assets/css/style.css';
}
?>

<link rel="stylesheet" href="<?= htmlspecialchars($cssPath) ?>" type="text/css">
<!-- Using stylesheet: <?= htmlspecialchars($cssPath) ?> -->

</head>
<body>
<a class="visually-hidden-focusable" href="#main-content">Skip to content</a>

<!-- Overlay for mobile sidebar (toggled by JS) -->
<div id="sidebar-overlay" class="sidebar-overlay" tabindex="-1" aria-hidden="true"></div>

<?php
// ✅ START layout wrapper
echo '<div class="d-flex">';

// ✅ Include sidebar using absolute path — works from any nested folder
$sidebarCandidates = [
    __DIR__ . '/sidebar.php',
    __DIR__ . '/../includes/sidebar.php',
    __DIR__ . '/../sidebar.php',
    dirname(__DIR__) . '/includes/sidebar.php'
];

$sidebarIncluded = false;
foreach ($sidebarCandidates as $path) {
    if (file_exists($path)) {
        include $path;
        $sidebarIncluded = true;
        break;
    }
}
if (!$sidebarIncluded) {
    echo '<!-- sidebar not found -->';
}

// ✅ Open main content (closed in footer)
echo '<main class="flex-grow-1 p-4 bg-light" id="main-content" style="min-height:100vh;">';

// ✅ Mobile topbar (visible only on mobile)
echo '<div class="mobile-topbar d-lg-none d-flex align-items-center mb-3">';
echo '  <button class="btn btn-sm btn-primary sidebar-toggle" aria-label="Toggle sidebar" aria-controls="sidebar" aria-expanded="false">☰</button>';
echo '  <div class="ms-2 fw-semibold">PicPose Admin</div>';
echo '</div>';
?>
