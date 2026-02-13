<?php
session_start();
require '../../config.php';

// Use the SAME session check as main admin panel
if (!isset($_SESSION['admin']) || empty($_SESSION['admin'])) {
    // Redirect to main admin login if not logged in
    header("Location: " . BASE_URL . "/login.php");
    exit();
}

// CSRF protection
if (empty($_SESSION['csrf_token'])) {
    $_SESSION['csrf_token'] = bin2hex(random_bytes(32));
}

// Fetch global settings
try {
    $global_stmt = $conn->prepare("SELECT * FROM ads_global_settings WHERE id = 1");
    $global_stmt->execute();
    $global_result = $global_stmt->get_result();
    $global = $global_result->fetch_assoc();
    $global_stmt->close();
} catch (Exception $e) {
    error_log("Global Settings Error: " . $e->getMessage());
    $global = [
        'ads_enabled' => 0,
        'environment' => 'development',
        'config_version' => 1
    ];
}

// Fetch statistics
try {
    $stats_stmt = $conn->prepare("
        SELECT 
            (SELECT COUNT(*) FROM ad_placements WHERE enabled = 1) as active_placements,
            (SELECT COUNT(*) FROM ad_placements) as total_placements,
            (SELECT COUNT(*) FROM ad_network_units WHERE enabled = 1) as active_units,
            (SELECT COUNT(*) FROM ad_network_units) as total_units,
            (SELECT COUNT(*) FROM ad_networks WHERE enabled = 1) as active_networks,
            (SELECT COUNT(*) FROM ad_networks) as total_networks,
            (SELECT COALESCE(SUM(revenue), 0) FROM ad_analytics WHERE stat_date = CURDATE()) as today_revenue,
            (SELECT COALESCE(SUM(impressions), 0) FROM ad_analytics WHERE stat_date = CURDATE()) as today_impressions,
            (SELECT COALESCE(SUM(clicks), 0) FROM ad_analytics WHERE stat_date = CURDATE()) as today_clicks
    ");
    $stats_stmt->execute();
    $stats_result = $stats_stmt->get_result();
    $stats = $stats_result->fetch_assoc();
    $stats_stmt->close();
} catch (Exception $e) {
    error_log("Stats Query Error: " . $e->getMessage());
    $stats = [
        'active_placements' => 0,
        'total_placements' => 0,
        'active_units' => 0,
        'total_units' => 0,
        'active_networks' => 0,
        'total_networks' => 0,
        'today_revenue' => 0,
        'today_impressions' => 0,
        'today_clicks' => 0
    ];
}

// Calculate CTR
$today_ctr = $stats['today_impressions'] > 0 ? 
    round(($stats['today_clicks'] / $stats['today_impressions']) * 100, 2) : 0;

// Fetch recent activity
try {
    $activity_stmt = $conn->prepare("
        SELECT 
            a.*,
            p.key_name as placement_key,
            p.ad_type,
            n.display_name as network_name
        FROM ad_network_units a
        LEFT JOIN ad_placements p ON a.placement_id = p.id
        LEFT JOIN ad_networks n ON a.network_id = n.id
        WHERE a.enabled = 1
        ORDER BY a.updated_at DESC
        LIMIT 5
    ");
    $activity_stmt->execute();
    $activity_result = $activity_stmt->get_result();
} catch (Exception $e) {
    error_log("Activity Query Error: " . $e->getMessage());
    $activity_result = null;
}

include '../../includes/header.php';
?>

<div class="container-fluid">
    <!-- Page Header -->
    <div class="page-header mb-4">
        <div class="d-flex justify-content-between align-items-center">
            <div class="d-flex align-items-center gap-3">
                <div class="icon-wrapper bg-primary">
                    <i class="bi bi-megaphone text-white"></i>
                </div>
                <div>
                    <h1 class="mb-1">Ads Management Dashboard</h1>
                    <nav aria-label="breadcrumb">
                        <ol class="breadcrumb mb-0">
                            <li class="breadcrumb-item"><a href="<?= BASE_URL ?>/index.php">Home Dashboard</a></li>
                            <li class="breadcrumb-item active">Ads Management</li>
                        </ol>
                    </nav>
                </div>
            </div>
            
            <div class="btn-group">
                <button type="button" class="btn btn-primary" onclick="location.href='global_settings.php'">
                    <i class="bi bi-gear me-1"></i> Settings
                </button>
                <button type="button" class="btn btn-success" onclick="location.href='analytics.php'">
                    <i class="bi bi-graph-up me-1"></i> Analytics
                </button>
            </div>
        </div>
    </div>

    <!-- Status Banner -->
    <div class="alert alert-<?php echo $global['ads_enabled'] ? 'success' : 'danger'; ?> mb-4">
        <div class="d-flex justify-content-between align-items-center">
            <div>
                <h5 class="alert-heading mb-1">
                    <i class="bi bi-<?php echo $global['ads_enabled'] ? 'check-circle' : 'x-circle'; ?> me-2"></i>
                    Ads are currently <?php echo $global['ads_enabled'] ? 'ENABLED' : 'DISABLED'; ?>
                </h5>
                <p class="mb-0">
                    Environment: <strong><?php echo strtoupper($global['environment']); ?></strong> | 
                    Config Version: <strong>v<?php echo $global['config_version']; ?></strong>
                </p>
            </div>
            <div>
                <a href="global_settings.php" class="btn btn-outline-<?php echo $global['ads_enabled'] ? 'success' : 'danger'; ?>">
                    <i class="bi bi-sliders me-1"></i> Change Status
                </a>
            </div>
        </div>
    </div>

    <!-- KPI Cards -->
    <div class="row g-3 mb-4">
        <!-- Revenue Card -->
        <div class="col-md-3 col-sm-6">
            <div class="card stat-card" style="background: linear-gradient(135deg, #4361ee, #3a0ca3);">
                <div class="card-body">
                    <div class="d-flex justify-content-between align-items-start">
                        <div>
                            <h6 class="text-white-50 mb-1">Today's Revenue</h6>
                            <h3 class="text-white mb-0">₹<?php echo number_format($stats['today_revenue'], 2); ?></h3>
                        </div>
                        <div class="icon-wrapper bg-white bg-opacity-25">
                            <i class="bi bi-currency-rupee text-white"></i>
                        </div>
                    </div>
                    <div class="mt-2">
                        <small class="text-white-75">
                            <i class="bi bi-calendar-check me-1"></i>
                            <?php echo date('M j, Y'); ?>
                        </small>
                    </div>
                </div>
            </div>
        </div>

        <!-- Impressions Card -->
        <div class="col-md-3 col-sm-6">
            <div class="card stat-card" style="background: linear-gradient(135deg, #4cc9f0, #4895ef);">
                <div class="card-body">
                    <div class="d-flex justify-content-between align-items-start">
                        <div>
                            <h6 class="text-white-50 mb-1">Today's Impressions</h6>
                            <h3 class="text-white mb-0"><?php echo number_format($stats['today_impressions']); ?></h3>
                        </div>
                        <div class="icon-wrapper bg-white bg-opacity-25">
                            <i class="bi bi-eye text-white"></i>
                        </div>
                    </div>
                    <div class="mt-2">
                        <small class="text-white-75">
                            <i class="bi bi-bar-chart me-1"></i>
                            Clicks: <?php echo number_format($stats['today_clicks']); ?>
                        </small>
                    </div>
                </div>
            </div>
        </div>

        <!-- CTR Card -->
        <div class="col-md-3 col-sm-6">
            <div class="card stat-card" style="background: linear-gradient(135deg, #f72585, #b5179e);">
                <div class="card-body">
                    <div class="d-flex justify-content-between align-items-start">
                        <div>
                            <h6 class="text-white-50 mb-1">Today's CTR</h6>
                            <h3 class="text-white mb-0"><?php echo $today_ctr; ?>%</h3>
                        </div>
                        <div class="icon-wrapper bg-white bg-opacity-25">
                            <i class="bi bi-percent text-white"></i>
                        </div>
                    </div>
                    <div class="mt-2">
                        <small class="text-white-75">
                            <i class="bi bi-activity me-1"></i>
                            Click-through rate
                        </small>
                    </div>
                </div>
            </div>
        </div>

        <!-- Placements Card -->
        <div class="col-md-3 col-sm-6">
            <div class="card stat-card" style="background: linear-gradient(135deg, #7209b7, #560bad);">
                <div class="card-body">
                    <div class="d-flex justify-content-between align-items-start">
                        <div>
                            <h6 class="text-white-50 mb-1">Active Placements</h6>
                            <h3 class="text-white mb-0"><?php echo $stats['active_placements']; ?></h3>
                        </div>
                        <div class="icon-wrapper bg-white bg-opacity-25">
                            <i class="bi bi-pin-map text-white"></i>
                        </div>
                    </div>
                    <div class="mt-2">
                        <small class="text-white-75">
                            <i class="bi bi-list-check me-1"></i>
                            <?php echo $stats['total_placements']; ?> total placements
                        </small>
                    </div>
                </div>
            </div>
        </div>
    </div>

    <!-- Quick Actions -->
    <div class="row g-3 mb-4">
        <div class="col-md-8">
            <div class="card">
                <div class="card-header bg-light">
                    <h5 class="mb-0">
                        <i class="bi bi-lightning-charge me-2"></i>
                        Quick Actions
                    </h5>
                </div>
                <div class="card-body">
                    <div class="row g-3">
                        <div class="col-md-3 col-6">
                            <a href="global_settings.php" class="card action-card text-decoration-none">
                                <div class="card-body text-center">
                                    <div class="icon-wrapper-lg bg-primary mb-3">
                                        <i class="bi bi-gear text-white"></i>
                                    </div>
                                    <h6 class="mb-1">Global Settings</h6>
                                    <small class="text-muted">Configure ads globally</small>
                                </div>
                            </a>
                        </div>
                        
                        <div class="col-md-3 col-6">
                            <a href="placements.php" class="card action-card text-decoration-none">
                                <div class="card-body text-center">
                                    <div class="icon-wrapper-lg bg-success mb-3">
                                        <i class="bi bi-pin-map text-white"></i>
                                    </div>
                                    <h6 class="mb-1">Placements</h6>
                                    <small class="text-muted">Manage ad placements</small>
                                </div>
                            </a>
                        </div>
                        
                        <div class="col-md-3 col-6">
                            <a href="networks.php" class="card action-card text-decoration-none">
                                <div class="card-body text-center">
                                    <div class="icon-wrapper-lg bg-info mb-3">
                                        <i class="bi bi-diagram-3 text-white"></i>
                                    </div>
                                    <h6 class="mb-1">Networks</h6>
                                    <small class="text-muted">Configure ad networks</small>
                                </div>
                            </a>
                        </div>
                        
                        <div class="col-md-3 col-6">
                            <a href="units.php" class="card action-card text-decoration-none">
                                <div class="card-body text-center">
                                    <div class="icon-wrapper-lg bg-warning mb-3">
                                        <i class="bi bi-puzzle text-white"></i>
                                    </div>
                                    <h6 class="mb-1">Ad Units</h6>
                                    <small class="text-muted">Manage ad units</small>
                                </div>
                            </a>
                        </div>
                    </div>
                </div>
            </div>
        </div>
        
        <div class="col-md-4">
            <div class="card">
                <div class="card-header bg-light">
                    <h5 class="mb-0">
                        <i class="bi bi-speedometer2 me-2"></i>
                        System Status
                    </h5>
                </div>
                <div class="card-body">
                    <div class="list-group list-group-flush">
                        <div class="list-group-item d-flex justify-content-between align-items-center px-0">
                            <span>Ad Units</span>
                            <span>
                                <span class="badge bg-success"><?php echo $stats['active_units']; ?></span>
                                <span class="badge bg-secondary">/<?php echo $stats['total_units']; ?></span>
                            </span>
                        </div>
                        <div class="list-group-item d-flex justify-content-between align-items-center px-0">
                            <span>Networks</span>
                            <span>
                                <span class="badge bg-success"><?php echo $stats['active_networks']; ?></span>
                                <span class="badge bg-secondary">/<?php echo $stats['total_networks']; ?></span>
                            </span>
                        </div>
                        <div class="list-group-item d-flex justify-content-between align-items-center px-0">
                            <span>Environment</span>
                            <span class="badge bg-<?php echo $global['environment'] == 'production' ? 'danger' : ($global['environment'] == 'staging' ? 'warning' : 'info'); ?>">
                                <?php echo strtoupper($global['environment']); ?>
                            </span>
                        </div>
                        <div class="list-group-item d-flex justify-content-between align-items-center px-0">
                            <span>Config Version</span>
                            <span class="badge bg-primary">v<?php echo $global['config_version']; ?></span>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>

    <!-- Recent Activity -->
    <div class="row">
        <div class="col-md-8">
            <div class="card">
                <div class="card-header bg-light d-flex justify-content-between align-items-center">
                    <h5 class="mb-0">
                        <i class="bi bi-clock-history me-2"></i>
                        Recent Activity
                    </h5>
                    <a href="units.php" class="btn btn-sm btn-outline-primary">
                        View All
                    </a>
                </div>
                <div class="card-body p-0">
                    <?php if ($activity_result && $activity_result->num_rows > 0): ?>
                        <div class="table-responsive">
                            <table class="table table-hover mb-0">
                                <thead class="table-light">
                                    <tr>
                                        <th>Ad Unit</th>
                                        <th>Placement</th>
                                        <th>Network</th>
                                        <th>Type</th>
                                        <th>Status</th>
                                        <th>Updated</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    <?php while($activity = $activity_result->fetch_assoc()): ?>
                                        <tr>
                                            <td>
                                                <div class="d-flex align-items-center">
                                                    <?php if ($activity['is_test']): ?>
                                                        <span class="badge bg-warning text-dark me-2">TEST</span>
                                                    <?php endif; ?>
                                                    <div>
                                                        <strong class="d-block"><?php echo htmlspecialchars($activity['ad_unit_name'] ?: 'Unnamed Unit'); ?></strong>
                                                        <small class="text-muted">
                                                            <code><?php echo substr($activity['ad_unit_id'], 0, 20); ?>...</code>
                                                        </small>
                                                    </div>
                                                </div>
                                            </td>
                                            <td>
                                                <code><?php echo htmlspecialchars($activity['placement_key']); ?></code>
                                                <div class="small text-muted"><?php echo $activity['ad_type']; ?></div>
                                            </td>
                                            <td>
                                                <span class="badge bg-secondary">
                                                    <?php echo htmlspecialchars($activity['network_name']); ?>
                                                </span>
                                            </td>
                                            <td>
                                                <?php if ($activity['is_live']): ?>
                                                    <span class="badge bg-success">Live</span>
                                                <?php else: ?>
                                                    <span class="badge bg-warning text-dark">Testing</span>
                                                <?php endif; ?>
                                            </td>
                                            <td>
                                                <?php if ($activity['enabled']): ?>
                                                    <span class="badge bg-success">Enabled</span>
                                                <?php else: ?>
                                                    <span class="badge bg-secondary">Disabled</span>
                                                <?php endif; ?>
                                            </td>
                                            <td>
                                                <small class="text-muted" title="<?php echo $activity['updated_at']; ?>">
                                                    <?php echo time_ago($activity['updated_at']); ?>
                                                </small>
                                            </td>
                                        </tr>
                                    <?php endwhile; ?>
                                </tbody>
                            </table>
                        </div>
                    <?php else: ?>
                        <div class="text-center py-5">
                            <i class="bi bi-inbox display-6 text-muted"></i>
                            <p class="mt-3 text-muted">No recent activity</p>
                        </div>
                    <?php endif; ?>
                </div>
            </div>
        </div>
        
        <div class="col-md-4">
            <div class="card">
                <div class="card-header bg-light">
                    <h5 class="mb-0">
                        <i class="bi bi-info-circle me-2"></i>
                        Quick Tips
                    </h5>
                </div>
                <div class="card-body">
                    <div class="alert alert-info">
                        <h6><i class="bi bi-lightbulb me-2"></i>Best Practices</h6>
                        <ul class="small mb-0">
                            <li>Test ad units in development before going live</li>
                            <li>Monitor CTR and adjust placements accordingly</li>
                            <li>Use multiple ad networks for better fill rates</li>
                            <li>Regularly update ad units for optimal performance</li>
                        </ul>
                    </div>
                    
                    <div class="alert alert-warning">
                        <h6><i class="bi bi-exclamation-triangle me-2"></i>Important</h6>
                        <p class="small mb-0">
                            Always increment config version after making changes to ensure apps reload the configuration.
                        </p>
                    </div>
                    
                    <div class="d-grid gap-2 mt-3">
                        <a href="analytics.php" class="btn btn-outline-success">
                            <i class="bi bi-graph-up me-2"></i> View Analytics
                        </a>
                        <a href="global_settings.php" class="btn btn-outline-primary">
                            <i class="bi bi-sliders me-2"></i> Change Settings
                        </a>
                    </div>
                </div>
            </div>
        </div>
    </div>
</div>

<script>
// Refresh data function
function refreshData() {
    const btn = event.target;
    const originalText = btn.innerHTML;
    
    btn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span> Refreshing...';
    btn.disabled = true;
    
    setTimeout(() => {
        window.location.reload();
    }, 1000);
}

// Sidebar toggle
function toggleSidebar() {
    document.querySelector('.sidebar').classList.toggle('show');
    document.querySelector('.sidebar-overlay').classList.toggle('show');
}
</script>

<?php
// Helper function for time ago - FIXED VERSION
function time_ago($datetime, $full = false) {
    if (empty($datetime)) {
        return 'recently';
    }
    
    try {
        $now = new DateTime;
        $ago = new DateTime($datetime);
        $diff = $now->diff($ago);
        
        // Calculate weeks from days without creating dynamic property
        $days = $diff->d;
        $weeks = floor($days / 7);
        $remaining_days = $days - ($weeks * 7);
        
        $string = [];
        
        // Years
        if ($diff->y > 0) {
            $string[] = $diff->y . ' year' . ($diff->y > 1 ? 's' : '');
        }
        
        // Months
        if ($diff->m > 0) {
            $string[] = $diff->m . ' month' . ($diff->m > 1 ? 's' : '');
        }
        
        // Weeks (calculated, not from dynamic property)
        if ($weeks > 0) {
            $string[] = $weeks . ' week' . ($weeks > 1 ? 's' : '');
        }
        
        // Days (remaining after weeks)
        if ($remaining_days > 0) {
            $string[] = $remaining_days . ' day' . ($remaining_days > 1 ? 's' : '');
        }
        
        // Hours
        if ($diff->h > 0) {
            $string[] = $diff->h . ' hour' . ($diff->h > 1 ? 's' : '');
        }
        
        // Minutes
        if ($diff->i > 0) {
            $string[] = $diff->i . ' minute' . ($diff->i > 1 ? 's' : '');
        }
        
        // Seconds (only show if less than a minute)
        if (count($string) === 0 && $diff->s > 0) {
            $string[] = $diff->s . ' second' . ($diff->s > 1 ? 's' : '');
        } elseif (count($string) === 0) {
            return 'just now';
        }
        
        if (!$full) {
            $string = array_slice($string, 0, 1);
        }
        
        return implode(', ', $string) . ' ago';
    } catch (Exception $e) {
        return 'recently';
    }
}

include '../../includes/footer.php';
?>


