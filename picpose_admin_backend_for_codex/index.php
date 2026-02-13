<?php

// DEBUG BOOTSTRAP - put this at the very top of index.php (before any output)
ini_set('display_errors', '1');
ini_set('display_startup_errors', '1');
error_reporting(E_ALL);

// Log errors to file inside project (make sure webserver can write this file)
ini_set('log_errors', '1');
ini_set('error_log', __DIR__ . '/php_error.log');

// Make sure fatal errors get logged/displayed as much as possible
set_error_handler(function($severity, $message, $file, $line) {
    // Convert errors to ErrorException so exceptions handler will catch them
    throw new ErrorException($message, 0, $severity, $file, $line);
});

set_exception_handler(function($ex) {
    // Format exception for browser and log
    $msg = sprintf("[%s] Uncaught Exception: %s in %s on line %d\nStack trace:\n%s\n",
        date('c'), $ex->getMessage(), $ex->getFile(), $ex->getLine(), $ex->getTraceAsString()
    );
    // Log
    @error_log($msg);
    // Display friendly error to browser (with details because this is dev)
    http_response_code(500);
    echo "<h1>Server error</h1>";
    echo "<pre style=\"white-space:pre-wrap;\">".htmlspecialchars($msg)."</pre>";
    exit(1);
});

// Shutdown handler to catch fatal errors (E_ERROR, E_PARSE, E_CORE_ERROR, E_COMPILE_ERROR, E_USER_ERROR)
register_shutdown_function(function() {
    $err = error_get_last();
    if ($err && in_array($err['type'], [E_ERROR, E_PARSE, E_CORE_ERROR, E_COMPILE_ERROR, E_USER_ERROR])) {
        $msg = sprintf("[%s] Fatal error: %s in %s on line %d\n", date('c'), $err['message'], $err['file'], $err['line']);
        @error_log($msg);
        // If headers not already sent, send 500
        if (!headers_sent()) http_response_code(500);
        echo "<h1>Fatal error</h1>";
        echo "<pre style=\"white-space:pre-wrap;\">".htmlspecialchars($msg)."</pre>";
        exit(1);
    }
});

// Optional: small helper to quickly dump variables when debugging
if (!function_exists('dd')) {
    function dd(...$v) {
        echo "<pre style=\"background:#111;color:#fff;padding:10px;border-radius:6px;\">";
        foreach ($v as $x) var_dump($x);
        echo "</pre>";
        exit;
    }
}


// index.php (Dashboard - PicPose Admin) - updated for ai_posts & guide_posts
session_start();
require 'config.php';

if (!isset($_SESSION['admin'])) {
    header("Location: login.php");
    exit();
}

/* simple escape helper */
function h($v) { return htmlspecialchars($v ?? '', ENT_QUOTES); }

/* Determine link prefix */
$docRoot = rtrim($_SERVER['DOCUMENT_ROOT'] ?? '', '/');
$linkPrefix = '';
if ($docRoot && (file_exists($docRoot . '/views/ai_posts/manage_ai_posts.php') || file_exists($docRoot . '/views/ai_posts/edit_ai_post.php'))) {
    $linkPrefix = '';
} elseif (defined('BASE_PATH') && BASE_PATH !== null && BASE_PATH !== '') {
    $linkPrefix = rtrim((string)BASE_PATH, '/');
} else {
    $scriptName = $_SERVER['SCRIPT_NAME'] ?? '';
    $linkPrefix = rtrim(dirname($scriptName), '/\\');
    if ($linkPrefix === '/' || $linkPrefix === '\\') $linkPrefix = '';
}
if ($linkPrefix !== '' && strpos($linkPrefix, '/') !== 0) $linkPrefix = '/' . $linkPrefix;

/* DB helper utilities */
function listTables($conn) {
    $out = [];
    $res = @$conn->query("SHOW TABLES");
    if ($res) {
        while ($r = $res->fetch_row()) $out[] = $r[0];
    }
    return $out;
}
function pickExistingTable($conn, $candidates) {
    $tables = listTables($conn);
    $map = [];
    foreach ($tables as $t) $map[strtolower($t)] = $t;
    foreach ($candidates as $cand) {
        $lc = strtolower($cand);
        if (isset($map[$lc])) return $map[$lc];
    }
    foreach ($tables as $t) {
        $tl = strtolower($t);
        foreach ($candidates as $cand) {
            if (strpos($tl, strtolower($cand)) !== false) return $t;
        }
    }
    return null;
}
function getCountFromTable($conn, $table) {
    $table = preg_replace('/[^a-z0-9_]/i', '', $table);
    if ($table === '') return 0;
    $res = @$conn->query("SHOW TABLES LIKE '" . $conn->real_escape_string($table) . "'");
    if (!($res && $res->num_rows > 0)) return 0;
    $r = @$conn->query("SELECT COUNT(*) AS cnt FROM `" . $conn->real_escape_string($table) . "`");
    if ($r) {
        $row = $r->fetch_assoc();
        return (int)($row['cnt'] ?? 0);
    }
    error_log("getCountFromTable failed for {$table}: " . ($conn->error ?? ''));
    return 0;
}
function countByTypeColumnHints($conn, $table, $typeHints = []) {
    $colsRes = @$conn->query("SHOW COLUMNS FROM `" . $conn->real_escape_string($table) . "`");
    if (!$colsRes) return 0;
    $cols = [];
    while ($c = $colsRes->fetch_assoc()) $cols[] = strtolower($c['Field']);
    $typeCols = ['post_type','type','kind','category','category_slug','group','content_type'];
    $useCol = null;
    foreach ($typeCols as $tc) { if (in_array($tc, $cols)) { $useCol = $tc; break; } }
    if (!$useCol) return 0;
    $safeTable = $conn->real_escape_string($table);
    $safeCol = $conn->real_escape_string($useCol);
    $parts = [];
    foreach ($typeHints as $hint) {
        $parts[] = "`$safeCol` = '" . $conn->real_escape_string($hint) . "'";
        $parts[] = "`$safeCol` LIKE '%" . $conn->real_escape_string($hint) . "%'";
    }
    $where = implode(' OR ', $parts);
    $sql = "SELECT COUNT(*) AS cnt FROM `{$safeTable}` WHERE ({$where})";
    $r = @$conn->query($sql);
    if ($r) {
        $row = $r->fetch_assoc();
        return (int)($row['cnt'] ?? 0);
    }
    error_log("countByTypeColumnHints failed for {$table}: " . ($conn->error ?? ''));
    return 0;
}
function countByTextSearch($conn, $table, $hint) {
    $colsRes = @$conn->query("SHOW COLUMNS FROM `" . $conn->real_escape_string($table) . "`");
    if (!$colsRes) return 0;
    $cols = [];
    while ($c = $colsRes->fetch_assoc()) $cols[] = strtolower($c['Field']);
    $candidates = ['title','prompt','name','heading','post_title','content','body','description','tip_text'];
    $found = [];
    foreach ($candidates as $cand) if (in_array($cand, $cols)) $found[] = $cand;
    if (empty($found)) return 0;
    $safeTable = $conn->real_escape_string($table);
    $like = $conn->real_escape_string($hint);
    $conds = [];
    foreach ($found as $c) $conds[] = "COALESCE(`" . $conn->real_escape_string($c) . "`,'') LIKE '%{$like}%'";
    $sql = "SELECT COUNT(*) AS cnt FROM `{$safeTable}` WHERE (" . implode(' OR ', $conds) . ")";
    $r = @$conn->query($sql);
    if ($r) {
        $row = $r->fetch_assoc();
        return (int)($row['cnt'] ?? 0);
    }
    return 0;
}
function detectDisplayColumn($conn, $table, $candidates = []) {
    $colsRes = @$conn->query("SHOW COLUMNS FROM `" . $conn->real_escape_string($table) . "`");
    if (!$colsRes) return null;
    $cols = [];
    while ($c = $colsRes->fetch_assoc()) $cols[] = $c['Field'];
    foreach ($candidates as $cand) {
        if (in_array($cand, $cols)) return $cand;
    }
    foreach ($cols as $c) {
        if (!in_array(strtolower($c), ['id','ID'])) return $c;
    }
    return 'id';
}

/* Detection of tables */
$aiCandidates = ['ai_posts','ai_prompts','ai_post','ai_prompt','ai','daily_tips'];
$guideCandidates = ['guide_posts','guides','guide_post','guide'];
$aiTable = pickExistingTable($conn, $aiCandidates);
$guideTable = pickExistingTable($conn, $guideCandidates);
$postsTable = pickExistingTable($conn, ['posts','post','all_posts']);

/* Stats */
$total_ai = 0;
$total_guides = 0;
$aiFoundBy = null;
$guideFoundBy = null;

/* AI detection/preference */
if ($aiTable) {
    $total_ai = getCountFromTable($conn, $aiTable);
    $aiFoundBy = "table: {$aiTable}";
} elseif ($postsTable) {
    $total_ai = countByTypeColumnHints($conn, $postsTable, ['picpose_ai','ai','ai_post','ai_prompt','prompt']);
    if ($total_ai > 0) {
        $aiFoundBy = "posts table ({$postsTable}) by type column match";
        $aiTable = $postsTable;
    } else {
        $total_ai = countByTextSearch($conn, $postsTable, 'ai');
        if ($total_ai > 0) {
            $aiFoundBy = "posts table ({$postsTable}) by text search";
            $aiTable = $postsTable;
        }
    }
}

/* Guide detection/preference */
if ($guideTable) {
    $total_guides = getCountFromTable($conn, $guideTable);
    $guideFoundBy = "table: {$guideTable}";
} elseif ($postsTable) {
    $total_guides = countByTypeColumnHints($conn, $postsTable, ['picpose_guide','guide','guide_post','guide_posts','guides']);
    if ($total_guides > 0) {
        $guideFoundBy = "posts table ({$postsTable}) by type column match";
        $guideTable = $postsTable;
    } else {
        $total_guides = countByTextSearch($conn, $postsTable, 'guide');
        if ($total_guides > 0) {
            $guideFoundBy = "posts table ({$postsTable}) by text search";
            $guideTable = $postsTable;
        }
    }
}

/* Other counts */
$total_users = getCountFromTable($conn, 'users');
$total_notifications = getCountFromTable($conn, 'notifications');


// --- Additional global stats with caching support ---
// --- Quick Stats Auto Refresh ---
$aiStats = $conn->query("SELECT COUNT(*) AS total_prompts,
    COALESCE(SUM(likes),0) AS total_likes,
    COALESCE(SUM(favorites),0) AS total_favorites,
    COALESCE(SUM(copies),0) AS total_copies
    FROM ai_posts");

if ($aiStats && $row = $aiStats->fetch_assoc()) {
    $total_prompts = (int)$row['total_prompts'];
    $total_likes = (int)$row['total_likes'];
    $total_favorites = (int)$row['total_favorites'];
    $total_copies = (int)$row['total_copies'];

    // Update cache table to keep data in sync (optional)
    $conn->query("REPLACE INTO dashboard_cache 
        (id,total_prompts,total_likes,total_favorites,total_copies,updated_at)
        VALUES (1,{$total_prompts},{$total_likes},{$total_favorites},{$total_copies},NOW())");
} else {
    $total_prompts = $total_likes = $total_favorites = $total_copies = 0;
}



/* Determine total posts number (prefer sum of known tables) */
$total_posts = 0;
if ($postsTable) {
    $total_posts = getCountFromTable($conn, $postsTable);
}
if ($total_posts === 0) {
    // if posts table absent, use sum of ai + guides
    $total_posts = $total_ai + $total_guides;
}


/* Recent AI items - prefer dedicated ai table; if only posts table exists, filter by type/text */
$recent_ai = [];
if ($aiTable) {
    $useTable = $aiTable;

    // Prefer columns that actually exist in AI table (note prompt_text used in your forms)
    $displayCandidates = ['prompt_text','short_description','prompt','title','tip_text','post_title','name','heading','content'];
    $displayCol = detectDisplayColumn($conn, $useTable, $displayCandidates) ?: 'id';
    $displayColEsc = $conn->real_escape_string($displayCol);
    $where = '';

    // If we're reading from legacy "posts" table, try to filter by type or text
    if ($useTable === $postsTable && $postsTable) {
        $colsRes = @$conn->query("SHOW COLUMNS FROM `" . $conn->real_escape_string($postsTable) . "`");
        $colsLower = [];
        $colsMap = [];
        while ($c = $colsRes->fetch_assoc()) {
            $colsLower[] = strtolower($c['Field']);
            $colsMap[strtolower($c['Field'])] = $c['Field'];
        }

        $typeCol = null;
        foreach (['post_type','type','kind','category','content_type'] as $tc) {
            if (in_array($tc, $colsLower)) { $typeCol = $tc; break; }
        }

        if ($typeCol) {
            $tcEsc = $conn->real_escape_string($typeCol);
            $where = " WHERE `$tcEsc` IN ('picpose_ai','ai','ai_post','ai_prompt','prompt') OR `$tcEsc` LIKE '%ai%'";
        } else {
            // Build a safe text-search using only columns that actually exist
            $possibleTextCols = ['title','prompt_text','prompt','short_description','name','heading','post_title','content','body','description','tip_text'];
            $have = [];
            foreach ($possibleTextCols as $pc) {
                if (in_array($pc, $colsLower)) $have[] = $colsMap[$pc]; // preserve real column name casing
            }
            if (!empty($have)) {
                $conds = [];
                $like = $conn->real_escape_string('ai');
                foreach ($have as $cname) {
                    $cEsc = $conn->real_escape_string($cname);
                    $conds[] = "COALESCE(`$cEsc`,'') LIKE '%{$like}%'";
                }
                $where = " WHERE (" . implode(' OR ', $conds) . ")";
            } else {
                $where = "";
            }
        }
    }

    // Final safe SQL - ensure selected display column exists in the table
    // If detectDisplayColumn returned something not present (edge case), fallback to `id` as display
    $colsRes2 = @$conn->query("SHOW COLUMNS FROM `" . $conn->real_escape_string($useTable) . "`");
    $actualCols = [];
    if ($colsRes2) { while ($c = $colsRes2->fetch_assoc()) $actualCols[] = $c['Field']; }
    if (!in_array($displayCol, $actualCols)) {
        // pick the first non-id column as a human-readable fallback
        $fallback = 'id';
        foreach ($actualCols as $ac) {
            if (strtolower($ac) !== 'id') { $fallback = $ac; break; }
        }
        $displayColEsc = $conn->real_escape_string($fallback);
    }

    $sql = "SELECT `id`, `" . $displayColEsc . "` AS display FROM `" . $conn->real_escape_string($useTable) . "`" . $where . " ORDER BY id DESC LIMIT 5";
    $r = @$conn->query($sql);
    if ($r) while ($row = $r->fetch_assoc()) $recent_ai[] = $row;
}



/* Recent Guide items */
$recent_guides = [];
if ($guideTable) {
    $useTable = $guideTable;
    $displayCandidates = ['title','name','heading','post_title','content'];
    $displayCol = detectDisplayColumn($conn, $useTable, $displayCandidates) ?: 'id';
    $displayColEsc = $conn->real_escape_string($displayCol);
    $where = '';

    if ($useTable === $postsTable && $postsTable) {
        $colsRes = @$conn->query("SHOW COLUMNS FROM `" . $conn->real_escape_string($postsTable) . "`");
        $colsLower = [];
        $colsMap = [];
        while ($c = $colsRes->fetch_assoc()) {
            $colsLower[] = strtolower($c['Field']);
            $colsMap[strtolower($c['Field'])] = $c['Field'];
        }

        $typeCol = null;
        foreach (['post_type','type','kind','category','content_type'] as $tc) {
            if (in_array($tc, $colsLower)) { $typeCol = $tc; break; }
        }
        if ($typeCol) {
            $tcEsc = $conn->real_escape_string($typeCol);
            $where = " WHERE `$tcEsc` IN ('picpose_guide','guide','guide_post','guide_posts','guides') OR `$tcEsc` LIKE '%guide%'";
        } else {
            // Build a safe text-search using only columns that actually exist
            $possibleTextCols = ['title','name','heading','post_title','content','body','description'];
            $have = [];
            foreach ($possibleTextCols as $pc) {
                if (in_array($pc, $colsLower)) $have[] = $colsMap[$pc];
            }
            if (!empty($have)) {
                $conds = [];
                $like = $conn->real_escape_string('guide');
                foreach ($have as $cname) {
                    $cEsc = $conn->real_escape_string($cname);
                    $conds[] = "COALESCE(`$cEsc`,'') LIKE '%{$like}%'";
                }
                $where = " WHERE (" . implode(' OR ', $conds) . ")";
            } else {
                $where = "";
            }
        }
    }

    $sql = "SELECT `id`, `" . $displayColEsc . "` AS display FROM `" . $conn->real_escape_string($useTable) . "`" . $where . " ORDER BY id DESC LIMIT 5";
    $r = @$conn->query($sql);
    if ($r) while ($row = $r->fetch_assoc()) $recent_guides[] = $row;
}


/* Render header */
include 'includes/header.php';
?>

<div id="main-area" class="container-fluid" style="max-width:1200px;">
    <div class="page-header mb-3">
        <div class="d-flex align-items-center gap-3">
            <span style="font-size:28px;">📊</span>
            <div>
                <h1 class="mb-0">Dashboard</h1>
                <div class="small-muted">Overview of users, posts and recent activity</div>
            </div>
        </div>
    </div>

    <div class="row g-3 mb-4">
        <div class="col-sm-6 col-md-2">
            <div class="card p-3 h-100" style="background:#2563eb;color:#fff;">
                <div class="small-muted">Total Users</div>
                <div class="fs-4 fw-bold"><?php echo h($total_users); ?></div>
            </div>
        </div>

        <div class="col-sm-6 col-md-2">
            <div class="card p-3 h-100" style="background:#5c0099;color:#fff;">
                <div class="small-muted">Total Posts</div>
                <div class="fs-4 fw-bold"><?php echo h($total_posts); ?></div>
            </div>
        </div>

        <div class="col-sm-6 col-md-3">
            <div class="card p-3 h-100" style="background:#bd0000;color:#fff;">
                <div class="small-muted">AI Prompts</div>
                <div class="fs-4 fw-bold"><?php echo h($total_ai); ?></div>
                <?php if ($aiFoundBy): ?><div class="small-muted" style="opacity:.85;font-size:.82rem;"><?php echo h($aiFoundBy); ?></div><?php endif; ?>
            </div>
        </div>

        <div class="col-sm-6 col-md-3">
            <div class="card p-3 h-100" style="background:#0ea5a4;color:#fff;">
                <div class="small-muted">Guide Posts</div>
                <div class="fs-4 fw-bold"><?php echo h($total_guides); ?></div>
                <?php if ($guideFoundBy): ?><div class="small-muted" style="opacity:.85;font-size:.82rem;"><?php echo h($guideFoundBy); ?></div><?php endif; ?>
            </div>
        </div>

        <div class="col-sm-6 col-md-2">
            <div class="card p-3 h-100" style="background:#f97316;color:#fff;">
                <div class="small-muted">Notifications</div>
                <div class="fs-4 fw-bold"><?php echo h($total_notifications); ?></div>
            </div>
        </div>
        
        
<!-- ====================== -->
<!--  QUICK STATS SECTION   -->
<!-- ====================== -->
<div class="col-12 mt-3">
    <div class="card p-3" style="background:#f8f9fc; border:none; box-shadow:0 2px 10px rgba(0,0,0,0.05);">
        <div class="d-flex align-items-center mb-3">
            <i class="bi bi-bar-chart-fill me-2" style="color:#2563eb;font-size:1.3rem;"></i>
            <h5 class="mb-0 fw-semibold text-dark">Quick Stats</h5>
        </div>

        <div class="row text-center g-3">
            <!-- AI Prompts -->
            <div class="col-6 col-md-3">
                <div class="p-3 rounded" style="background:rgba(37,99,235,0.06);">
                    <div class="mb-1">
                        <i class="bi bi-stars" style="color:#2563eb;font-size:1.5rem;"></i>
                    </div>
                    <div class="fw-bold fs-4 text-dark"><?= h($total_prompts); ?></div>
                    <div class="small text-muted">AI Prompts</div>
                </div>
            </div>

            <!-- Likes -->
            <div class="col-6 col-md-3">
                <div class="p-3 rounded" style="background:rgba(234,88,12,0.06);">
                    <div class="mb-1">
                        <i class="bi bi-graph-up-arrow" style="color:#ea580c;font-size:1.5rem;"></i>
                    </div>
                    <div class="fw-bold fs-4 text-dark"><?= h($total_likes); ?></div>
                    <div class="small text-muted">Likes</div>
                </div>
            </div>

            <!-- Favourites -->
            <div class="col-6 col-md-3">
                <div class="p-3 rounded" style="background:rgba(239,68,68,0.06);">
                    <div class="mb-1">
                        <i class="bi bi-heart-fill" style="color:#ef4444;font-size:1.5rem;"></i>
                    </div>
                    <div class="fw-bold fs-4 text-dark"><?= h($total_favorites); ?></div>
                    <div class="small text-muted">Favourites</div>
                </div>
            </div>

            <!-- Copies -->
            <div class="col-6 col-md-3">
                <div class="p-3 rounded" style="background:rgba(16,185,129,0.06);">
                    <div class="mb-1">
                        <i class="bi bi-files" style="color:#10b981;font-size:1.5rem;"></i>
                    </div>
                    <div class="fw-bold fs-4 text-dark"><?= h($total_copies); ?></div>
                    <div class="small text-muted">Copies</div>
                </div>
            </div>
        </div>
    </div>
</div>


        
        
    </div>

    <div class="row g-4">
        <div class="col-md-6">
            <div class="card p-3 h-100">
                <div class="d-flex align-items-center mb-3">
                    <h5 class="mb-0">🧠 Recent AI Prompts</h5>
                    <div class="ms-auto">
                        <a href="<?php echo h(($linkPrefix ?: '') . '/views/ai_posts/manage_ai_posts.php'); ?>" class="btn btn-sm btn-outline-secondary">Manage</a>
                    </div>
                </div>

                <?php if (!$aiTable && !$aiFoundBy): ?>
                    <div class="text-muted">No AI prompts table found (expected: <?php echo h(implode(', ', $aiCandidates)); ?>).</div>
                <?php else: if (empty($recent_ai)): ?>
                    <div class="text-muted">No AI prompts yet.</div>
                <?php else: ?>
                    <ul class="list-group list-group-flush">
                        <?php foreach ($recent_ai as $r): ?>
                            <li class="list-group-item d-flex align-items-center">
                                <div style="flex:1;">
                                    <div class="fw-semibold"><?php echo h(mb_strimwidth($r['display'] ?? '', 0, 90, '...')); ?></div>
                                </div>
                                <div>
                                    <a href="<?php echo h(($linkPrefix ?: '') . '/views/ai_posts/edit_ai_post.php?id=' . (int)$r['id']); ?>" class="btn btn-sm btn-outline-primary">Edit</a>
                                </div>
                            </li>
                        <?php endforeach; ?>
                    </ul>
                <?php endif; endif; ?>
            </div>
        </div>

        <div class="col-md-6">
            <div class="card p-3 h-100">
                <div class="d-flex align-items-center mb-3">
                    <h5 class="mb-0">📚 Recent Guide Posts</h5>
                    <div class="ms-auto">
                        <a href="<?php echo h(($linkPrefix ?: '') . '/views/guide_posts/manage_guides.php'); ?>" class="btn btn-sm btn-outline-secondary">Manage</a>
                    </div>
                </div>

                <?php if (!$guideTable && !$guideFoundBy): ?>
                    <div class="text-muted">No Guide posts table found (expected: <?php echo h(implode(', ', $guideCandidates)); ?>).</div>
                <?php else: if (empty($recent_guides)): ?>
                    <div class="text-muted">No guide posts yet.</div>
                <?php else: ?>
                    <ul class="list-group list-group-flush">
                        <?php foreach ($recent_guides as $r): ?>
                            <li class="list-group-item d-flex align-items-center">
                                <div style="flex:1;">
                                    <div class="fw-semibold"><?php echo h(mb_strimwidth($r['display'] ?? '', 0, 90, '...')); ?></div>
                                </div>
                                <div>
                                    <a href="<?php echo h(($linkPrefix ?: '') . '/views/guide_posts/edit_guide_post.php?id=' . (int)$r['id']); ?>" class="btn btn-sm btn-outline-primary">Edit</a>
                                </div>
                            </li>
                        <?php endforeach; ?>
                    </ul>
                <?php endif; endif; ?>
            </div>
        </div>
    </div>

    <div class="mt-4">
        <h5>Quick Links</h5>
        <div class="d-flex gap-2 flex-wrap">
            <a href="<?php echo h(($linkPrefix ?: '') . '/users.php'); ?>" class="btn btn-outline-secondary btn-sm">Manage Users</a>
            <a href="<?php echo h(($linkPrefix ?: '') . '/manage_categories.php'); ?>" class="btn btn-outline-secondary btn-sm">Manage Categories</a>
            <a href="<?php echo h(($linkPrefix ?: '') . '/views/ai_posts/add_ai_post.php'); ?>" class="btn btn-primary btn-sm">Add AI Post</a>
            <a href="<?php echo h(($linkPrefix ?: '') . '/views/guide_posts/add_guide_post.php'); ?>" class="btn btn-primary btn-sm">Add Guide Post</a>
        </div>
    </div>
</div>

<?php
include 'includes/footer.php';
