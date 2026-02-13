<?php
// Determine favicon path (prefer configured constant, then BASE_URL, then root /favicon.ico)
$favicon = '/favicon.ico';
if (defined('FAVICON_URL') && !empty(FAVICON_URL)) {
    $favicon = FAVICON_URL;
} elseif (isset($BASE_URL) && !empty($BASE_URL)) {
    $favicon = rtrim($BASE_URL, '/') . '/favicon.ico';
} elseif (isset($BASE_HREF) && !empty($BASE_HREF)) {
    // fallback to admin-folder relative path
    $favicon = rtrim($BASE_HREF, '/') . '/favicon.ico';
}
?>
<img src="<?php echo htmlspecialchars($favicon, ENT_QUOTES); ?>"
     alt="PicPose logo"
     onerror="this.onerror=null;this.src='<?php echo htmlspecialchars($BASE_HREF ?? '/picpose_admin', ENT_QUOTES); ?>/assets/no-image.png';"
     loading="lazy" width="40" height="40" style="object-fit:contain;">