<?php
// includes/sidebar.php (final)
// Uses configured constants from config.php (FAVICON_URL, BASE_PATH, BASE_URL)
// This version ensures root picpose_icon.png is used, robust detection and fallbacks.

// Ensure config is loaded (so BASE_PATH / FAVICON_URL are available)
if (!isset($conn)) {
    $maybeConfig = __DIR__ . '/../config.php';
    if (file_exists($maybeConfig)) {
        @include_once $maybeConfig;
    }
}

// Helper to escape attributes
function e_attr($v) { return htmlspecialchars($v ?? '', ENT_QUOTES); }

// Determine current script and page
$scriptName = $_SERVER['SCRIPT_NAME'] ?? '';
$current_path = $scriptName;
$current_page = basename($current_path);

// ---- SECTION DETECTION (FIX FOR ACTIVE STATE) ----
$isAdsSection = (strpos($current_path, '/pages/ads/') !== false);

// Dashboard should be active ONLY on root index.php
$isDashboard = (
    $current_page === 'index.php'
    && !$isAdsSection
);


// Detect protocol and host
$protocol = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') ? 'https' : 'http';
$host = $_SERVER['HTTP_HOST'] ?? ($_SERVER['SERVER_NAME'] ?? 'localhost');
$rootUrl = rtrim($protocol . '://' . $host, '/'); // example: https://picpose.iamakmal.in

// Determine base href (root-relative prefix for internal links).
$detectedBasePath = '';
if (defined('BASE_PATH') && BASE_PATH !== null && BASE_PATH !== '') {
    $detectedBasePath = rtrim((string)BASE_PATH, '/');
}
if ($detectedBasePath === '/picpose_admin') {
    $BASE_HREF = '';
} else {
    $BASE_HREF = $detectedBasePath;
    if ($BASE_HREF !== '' && substr($BASE_HREF, 0, 1) !== '/') {
        $BASE_HREF = '/' . $BASE_HREF;
    }
}

// Build absolute base URL (fallback)
if (defined('BASE_URL') && BASE_URL) {
    $BASE_URL = rtrim((string)BASE_URL, '/');
} else {
    // prefer rootUrl (without extra base path) for absolute references
    $BASE_URL = $rootUrl . ($BASE_HREF !== '' ? $BASE_HREF : '');
    $BASE_URL = rtrim($BASE_URL, '/');
}

// ---------------- FAVICON / ICON LOGIC ----------------
// 1) If FAVICON_URL defined -> use it (and convert root-relative to absolute).
// 2) Else prefer root picpose_icon.png (absolute: $rootUrl/picpose_icon.png) if file exists.
// 3) Else fallback to root favicon.ico (absolute).
if (defined('FAVICON_URL') && FAVICON_URL) {
    $favicon_src = (string)FAVICON_URL;
    if (strpos($favicon_src, '/') === 0) {
        // root-relative path like "/images/x.png"
        $favicon_src = $rootUrl . $favicon_src;
    } elseif (strpos($favicon_src, 'http://') === false && strpos($favicon_src, 'https://') === false) {
        // relative path (convert to base url)
        $favicon_src = rtrim($BASE_URL, '/') . '/' . ltrim($favicon_src, '/');
    }
} else {
    // prefer picpose_icon.png in DOCUMENT_ROOT
    $docRoot = rtrim($_SERVER['DOCUMENT_ROOT'], '/');
    $iconServerPath = $docRoot . '/picpose_icon.png';
    if (file_exists($iconServerPath)) {
        $favicon_src = $rootUrl . '/picpose_icon.png';
    } else {
        // fallback to favicon.ico (if present)
        $icoServerPath = $docRoot . '/favicon.ico';
        if (file_exists($icoServerPath)) {
            $favicon_src = $rootUrl . '/favicon.ico';
        } else {
            // Last fallback: try BASE_URL + /assets/no-image.png to avoid broken image
            $favicon_src = rtrim($BASE_URL, '/') . '/assets/no-image.png';
        }
    }
}
// -----------------------------------------------------

// tip count (safe read)
$tipCount = 0;
if (isset($conn) && $conn instanceof mysqli) {
    try {
        $res = $conn->query("SELECT COUNT(1) AS cnt FROM daily_tips");
        if ($res) {
            $row = $res->fetch_assoc();
            $tipCount = (int)($row['cnt'] ?? 0);
        }
    } catch (Throwable $e) {
        $tipCount = 0;
        error_log("sidebar: daily_tips count error: " . $e->getMessage());
    }
}

// --- Pending Support Query Count ---
$pendingSupportCount = 0;
if (isset($conn) && $conn instanceof mysqli) {
    try {
        $res = $conn->query("SELECT COUNT(*) AS cnt FROM support_queries WHERE status='Pending'");
        if ($res) {
            $row = $res->fetch_assoc();
            $pendingSupportCount = (int)($row['cnt'] ?? 0);
        }
    } catch (Throwable $e) {
        error_log("sidebar: support_queries count error: " . $e->getMessage());
    }
}

// section detection for "active" state (use root-relative checks)
$postsAiPath = ($BASE_HREF !== '' ? $BASE_HREF : '') . '/views/ai_posts/';
$postsGuidePath = ($BASE_HREF !== '' ? $BASE_HREF : '') . '/views/guide_posts/';
$isPostsSection = (strpos($current_path, $postsAiPath) !== false) || (strpos($current_path, $postsGuidePath) !== false);
$isAiSection = (strpos($current_path, $postsAiPath) !== false);
$isGuideSection = (strpos($current_path, $postsGuidePath) !== false);

$isAiManage = $isAiSection && (strpos($current_page, 'manage') !== false);
$isAiAdd = $isAiSection && ($current_page === 'add_ai_post.php');
$isGuideManage = $isGuideSection && (strpos($current_page, 'manage') !== false);
$isGuideAdd = $isGuideSection && ($current_page === 'add_guide_post.php');

$monetizationPath = ($BASE_HREF !== '' ? $BASE_HREF : '') . '/views/monetization/';
$isMonetizationSection = (strpos($current_path, $monetizationPath) !== false);
$isMonetizationWallets = $isMonetizationSection && ($current_page === 'user_wallets.php');
$isMonetizationLedger = $isMonetizationSection && ($current_page === 'user_ledger.php');
$isMonetizationUnlocks = $isMonetizationSection && ($current_page === 'user_unlocks.php');
$isMonetizationAdjust = $isMonetizationSection && ($current_page === 'adjust_points.php');
$isMonetizationStreak = $isMonetizationSection && ($current_page === 'streak_config.php');
$isMonetizationPacks = $isMonetizationSection && ($current_page === 'premium_packs.php');
?>
<style>
/* Sidebar (inline override kept minimal to avoid future conflicts) */
.sidebar {
  min-width: 240px;
  max-width: 300px;
  background: linear-gradient(180deg,#0f1724 0%,#0b1220 100%);
  color:#e6eef8;
  border-right:1px solid rgba(255,255,255,0.03);
  display:flex;
  flex-direction:column;
  /* KEY FIXES ↓ */
  height:auto;            /* let it grow with page height */
  min-height:100vh;       /* at least viewport tall */
  overflow:visible;       /* no internal scroll on desktop */
  padding:1.25rem;
  box-sizing:border-box;
}
.sidebar .brand { display:flex; align-items:center; gap:12px; padding-bottom:.5rem; border-bottom:1px solid rgba(255,255,255,0.03); margin-bottom:1rem; }
.sidebar .brand img { width:36px; height:36px; object-fit:cover; border-radius:6px; }
.sidebar .nav-link, .sidebar .btn-post { color:#d7e6fb; padding:.55rem .6rem; border-radius:8px; display:flex; align-items:center; gap:.6rem; text-decoration:none; border:1px solid transparent; }
.sidebar .nav-link:hover, .sidebar .btn-post:hover { background:rgba(255,255,255,0.03); color:#fff; text-decoration:none; }
.sidebar .nav-link.active, .sidebar .btn-post.nav-link.active { background:linear-gradient(90deg,#2563eb,#7c3aed); color:white; box-shadow:0 6px 18px rgba(124,58,237,0.08); border-color:rgba(255,255,255,0.04); }
.sidebar .section-card { background:rgba(255,255,255,0.02); border:1px solid rgba(255,255,255,0.03); padding:.6rem; border-radius:10px; }
.small-muted { font-size:.78rem; color:rgba(255,255,255,0.55); }
.sidebar footer { padding-top:1rem; font-size:.82rem; color:rgba(255,255,255,0.6); }
.badge-tip { font-size:.72rem; margin-left:auto; }

/* Mobile behavior: fixed, scrollable */
@media (max-width:991px) {
  .sidebar {
    position:fixed;
    left:0; top:0; bottom:0;
    transform:translateX(-110%);
    transition:transform .22s ease;
    z-index:1060;
    width:280px;
    /* mobile needs its own scroll */
    overflow-y:auto;
  }
  .sidebar.open { transform:translateX(0); }
}
</style>

<div id="sidebar" class="sidebar" role="navigation" aria-label="Main sidebar">
    <div class="brand">
        <img src="<?php echo e_attr($favicon_src); ?>"
             alt="PicPose logo"
             onerror="this.onerror=null;this.src='<?php echo e_attr(rtrim($rootUrl, '/')) . '/assets/no-image.png'; ?>';"
             loading="lazy" width="40" height="40" style="object-fit:contain;">
        <div>
            <div style="font-weight:600; font-size:1.05rem;">PicPose</div>
            <div class="small-muted">Admin Panel</div>
        </div>
    </div>

    <nav class="mb-3" aria-label="Main navigation">
            <div class="mb-2">
            <a href="<?php echo e_attr(($BASE_HREF === '' ? '' : $BASE_HREF) . '/index.php'); ?>"
            class="nav-link d-flex <?php echo $isDashboard ? 'active' : ''; ?>">
                <span>📊</span>
                <span style="flex:1">Dashboard</span>
            </a>
        </div>


        <div class="mb-2">
            <a href="<?php echo e_attr(($BASE_HREF === '' ? '' : $BASE_HREF) . '/users.php'); ?>" class="nav-link d-flex <?php echo ($current_page === 'users.php') ? 'active' : ''; ?>">
                <span>👥</span><span style="flex:1">Users</span>
            </a>
        </div>

        <div class="mb-2">
            <div class="d-flex align-items-center justify-content-between mb-2">
                <div class="d-flex align-items-center">
                    <span style="margin-right:.5rem">💰</span>
                    <strong style="font-size:.98rem">Monetization</strong>
                </div>
            </div>
            <div class="section-card">
                <div class="d-grid gap-1">
                    <a href="<?php echo e_attr(($BASE_HREF === '' ? '' : $BASE_HREF) . '/views/monetization/user_wallets.php'); ?>" class="btn btn-post <?php echo $isMonetizationWallets ? 'nav-link active' : 'btn-outline-light text-white'; ?>">👛 User Wallets</a>
                    <a href="<?php echo e_attr(($BASE_HREF === '' ? '' : $BASE_HREF) . '/views/monetization/streak_config.php'); ?>" class="btn btn-post <?php echo $isMonetizationStreak ? 'nav-link active' : 'btn-outline-light text-white'; ?>">📅 Streak Config</a>
                    <a href="<?php echo e_attr(($BASE_HREF === '' ? '' : $BASE_HREF) . '/views/monetization/premium_packs.php'); ?>" class="btn btn-post <?php echo $isMonetizationPacks ? 'nav-link active' : 'btn-outline-light text-white'; ?>">📦 Premium Packs</a>
                    <a href="<?php echo e_attr(($BASE_HREF === '' ? '' : $BASE_HREF) . '/views/monetization/user_ledger.php'); ?>" class="btn btn-post <?php echo $isMonetizationLedger ? 'nav-link active' : 'btn-outline-light text-white'; ?>">🧾 Ledger</a>
                    <a href="<?php echo e_attr(($BASE_HREF === '' ? '' : $BASE_HREF) . '/views/monetization/user_unlocks.php'); ?>" class="btn btn-post <?php echo $isMonetizationUnlocks ? 'nav-link active' : 'btn-outline-light text-white'; ?>">🔓 Unlocks</a>
                    <a href="<?php echo e_attr(($BASE_HREF === '' ? '' : $BASE_HREF) . '/views/monetization/adjust_points.php'); ?>" class="btn btn-post <?php echo $isMonetizationAdjust ? 'nav-link active' : 'btn-outline-light text-white'; ?>">🛠 Adjust Points</a>
                </div>
            </div>
        </div>

        <div class="mb-2">
            <div class="d-flex align-items-center justify-content-between mb-2">
                <div class="d-flex align-items-center">
                    <span style="margin-right:.5rem">📰</span>
                    <strong style="font-size:.98rem">Posts</strong>
                    <?php if ($isPostsSection): ?><small class="small-muted" style="margin-left:.6rem;"><?php echo $isAiSection ? 'AI' : ($isGuideSection ? 'Guide' : ''); ?></small><?php endif; ?>
                </div>
            </div>

            <div class="section-card">
                <div class="d-grid gap-1 mb-2">
                    <a href="<?php echo e_attr(($BASE_HREF === '' ? '' : $BASE_HREF) . '/views/ai_posts/manage_ai_posts.php'); ?>" class="btn btn-post <?php echo $isAiManage ? 'nav-link active' : 'btn-outline-light text-white'; ?>">🧠 Manage AI Posts</a>
                    <a href="<?php echo e_attr(($BASE_HREF === '' ? '' : $BASE_HREF) . '/views/ai_posts/add_ai_post.php'); ?>" class="btn btn-post <?php echo $isAiAdd ? 'nav-link active' : 'btn-outline-light text-white'; ?>">➕ Add AI Post</a>
                </div>

                <hr style="opacity:.04;margin:.5rem 0;">

                <div class="d-grid gap-1">
                    <a href="<?php echo e_attr(($BASE_HREF === '' ? '' : $BASE_HREF) . '/views/guide_posts/manage_guides.php'); ?>" class="btn btn-post <?php echo $isGuideManage ? 'nav-link active' : 'btn-outline-light text-white'; ?>">📚 Manage Guide Posts</a>
                    <a href="<?php echo e_attr(($BASE_HREF === '' ? '' : $BASE_HREF) . '/views/guide_posts/add_guide_post.php'); ?>" class="btn btn-post <?php echo $isGuideAdd ? 'nav-link active' : 'btn-outline-light text-white'; ?>">➕ Add Guide Post</a>
                </div>
            </div>
        </div>

        <div class="mb-2">
            <a href="<?php echo e_attr(($BASE_HREF === '' ? '' : $BASE_HREF) . '/manage_categories.php'); ?>" class="nav-link d-flex <?php echo ($current_page === 'manage_categories.php') ? 'active' : ''; ?>">
               <span>📂</span><span style="flex:1">Manage Categories</span>
            </a>
        </div>

        <div class="mb-2">
            <a href="<?php echo e_attr(($BASE_HREF === '' ? '' : $BASE_HREF) . '/views/tips/manage_tips.php'); ?>" class="nav-link d-flex <?php echo ($current_page === 'manage_tips.php' || $current_page === 'add_edit_tip.php') ? 'active' : ''; ?>">
               <span>💡</span><span style="flex:1">Daily Tips</span>
               <?php if ($tipCount > 0): ?><span class="badge bg-info badge-tip"><?php echo (int)$tipCount; ?></span><?php endif; ?>
            </a>
        </div>

        <div class="mb-2">
            <a href="<?php echo e_attr(($BASE_HREF === '' ? '' : $BASE_HREF) . '/send_notification.php'); ?>" class="nav-link d-flex <?php echo ($current_page === 'send_notification.php') ? 'active' : ''; ?>">
               <span>🔔</span><span style="flex:1">Notifications</span>
            </a>
        </div>
        
        <?php
        // Detect Ads section (for active state)
        $isAdsSection = (strpos($current_path, '/pages/ads/') !== false);
        ?>

        <div class="mb-2">
            <a href="<?php echo e_attr(($BASE_HREF === '' ? '' : $BASE_HREF) . '/pages/ads/index.php'); ?>"
            class="nav-link d-flex <?php echo $isAdsSection ? 'active' : ''; ?>">
            <span>📢</span>
       <span style="flex:1">Ads Management</span>
            </a>
        </div>

        <div class="mb-2">
            <a href="<?php echo e_attr(($BASE_HREF === '' ? '' : $BASE_HREF) . '/settings.php'); ?>" class="nav-link d-flex <?php echo ($current_page === 'settings.php') ? 'active' : ''; ?>">
               <span>⚙️</span><span style="flex:1">Settings</span>
            </a>
        </div>
        
        <div class="mb-2">
    <a href="<?php echo e_attr(($BASE_HREF === '' ? '' : $BASE_HREF) . '/views/support/manage_support.php'); ?>"
       class="nav-link d-flex align-items-center <?php echo ($current_page === 'manage_support.php' || $current_page === 'view_support.php') ? 'active' : ''; ?>">
       <span>💬</span>
       <span style="flex:1">Help & Support</span>
       <?php if ($pendingSupportCount > 0): ?>
           <span class="badge bg-warning text-dark badge-tip"><?php echo (int)$pendingSupportCount; ?> Pending</span>
       <?php endif; ?>
    </a>
    </div>

        <div class="mt-2">
            <a href="<?php echo e_attr(($BASE_HREF === '' ? '' : $BASE_HREF) . '/logout.php'); ?>" class="nav-link d-flex text-danger">
               <span>🚪</span><span style="flex:1">Logout</span>
            </a>
        </div>
    </nav>

    <footer class="mt-auto text-center">
        <div style="opacity:.9; margin-bottom:.35rem;">&copy; 2025 PicPose</div>
        <div class="small-muted">Built with ♥ by Akmal</div>
    </footer>
</div>
