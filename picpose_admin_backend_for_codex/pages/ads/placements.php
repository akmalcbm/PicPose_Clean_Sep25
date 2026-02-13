<?php

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

$csrf_token = $_SESSION['csrf_token'];

// Fetch placements with statistics
try {
    $placements_stmt = $conn->prepare("
        SELECT 
            p.*,
            COUNT(DISTINCT u.id) as total_units,
            COUNT(DISTINCT CASE WHEN u.enabled = 1 THEN u.id END) as active_units,
            COUNT(DISTINCT CASE WHEN u.is_live = 1 THEN u.id END) as live_units,
            COALESCE(SUM(CASE WHEN u.is_test = 1 THEN 1 ELSE 0 END), 0) as test_units,
            COALESCE(SUM(CASE WHEN u.is_test = 0 THEN 1 ELSE 0 END), 0) as production_units,
            COALESCE(a.impressions, 0) as last_impressions,
            COALESCE(a.clicks, 0) as last_clicks,
            COALESCE(a.revenue, 0) as last_revenue
        FROM ad_placements p
        LEFT JOIN ad_network_units u ON p.id = u.placement_id
        LEFT JOIN (
            SELECT placement_key, 
                   SUM(impressions) as impressions,
                   SUM(clicks) as clicks,
                   SUM(revenue) as revenue
            FROM ad_analytics 
            WHERE stat_date = CURDATE() - INTERVAL 1 DAY
            GROUP BY placement_key
        ) a ON p.key_name COLLATE utf8mb4_unicode_ci = a.placement_key
        GROUP BY p.id
        ORDER BY p.enabled DESC, p.created_at DESC
    ");
    $placements_stmt->execute();
    $placements_result = $placements_stmt->get_result();
} catch (Exception $e) {
    error_log("Placements Query Error: " . $e->getMessage());
    $placements_result = null;
}

// Fetch global settings for frequency reference
try {
    $global_stmt = $conn->prepare("SELECT default_frequency_per_hour FROM ads_global_settings WHERE id = 1");
    $global_stmt->execute();
    $global_result = $global_stmt->get_result();
    $global = $global_result->fetch_assoc();
    $global_stmt->close();
} catch (Exception $e) {
    error_log("Global Settings Error: " . $e->getMessage());
    $global = ['default_frequency_per_hour' => 3];
}

// Ad type icons
$ad_type_icons = [
    'banner' => 'image',
    'interstitial' => 'square',
    'native' => 'file-earmark',
    'rewarded' => 'gift'
];

// Ad type colors
$ad_type_colors = [
    'banner' => 'primary',
    'interstitial' => 'success',
    'native' => 'info',
    'rewarded' => 'warning'
];

include '../../includes/header.php';
?>

<div class="container-fluid">
    <!-- Page Header -->
    <div class="page-header mb-4">
        <div class="d-flex justify-content-between align-items-center">
            <div class="d-flex align-items-center gap-3">
                <div class="icon-wrapper bg-primary">
                    <i class="bi bi-pin-map text-white"></i>
                </div>
                <div>
                    <h1 class="mb-1">Ad Placements Management</h1>
                    <nav aria-label="breadcrumb">
                        <ol class="breadcrumb mb-0">
                            <li class="breadcrumb-item"><a href="../dashboard.php">Dashboard</a></li>
                            <li class="breadcrumb-item"><a href="index.php">Ads Management</a></li>
                            <li class="breadcrumb-item active">Placements</li>
                        </ol>
                    </nav>
                </div>
            </div>
            
            <div class="btn-group">
                <a href="index.php" class="btn btn-outline-secondary">
                    <i class="bi bi-arrow-left me-1"></i> Back
                </a>
                <button type="button" class="btn btn-primary" data-bs-toggle="modal" data-bs-target="#addPlacementModal">
                    <i class="bi bi-plus-circle me-1"></i> Add Placement
                </button>
            </div>
        </div>
    </div>

    <!-- Stats Summary -->
    <div class="row g-3 mb-4">
        <?php
        $summary = [
            'total' => 0,
            'active' => 0,
            'with_units' => 0,
            'banners' => 0,
            'interstitials' => 0,
            'natives' => 0,
            'rewarded' => 0
        ];
        
        if ($placements_result) {
            $placements_result->data_seek(0);
            while($placement = $placements_result->fetch_assoc()) {
                $summary['total']++;
                if ($placement['enabled']) $summary['active']++;
                if ($placement['total_units'] > 0) $summary['with_units']++;
                
                // FIXED: Use conditional check
                switch($placement['ad_type']) {
                    case 'banner':
                        $summary['banners']++;
                        break;
                    case 'interstitial':
                        $summary['interstitials']++;
                        break;
                    case 'native':
                        $summary['natives']++;
                        break;
                    case 'rewarded':
                        $summary['rewarded']++;
                        break;
                }
            }
            $placements_result->data_seek(0);
        }
        ?>
        
        <div class="col-md-2 col-sm-4 col-6">
            <div class="card stat-card-sm">
                <div class="card-body text-center">
                    <h3 class="text-primary mb-1"><?php echo $summary['total']; ?></h3>
                    <small class="text-muted">Total Placements</small>
                </div>
            </div>
        </div>
        
        <div class="col-md-2 col-sm-4 col-6">
            <div class="card stat-card-sm">
                <div class="card-body text-center">
                    <h3 class="text-success mb-1"><?php echo $summary['active']; ?></h3>
                    <small class="text-muted">Active</small>
                </div>
            </div>
        </div>
        
        <div class="col-md-2 col-sm-4 col-6">
            <div class="card stat-card-sm">
                <div class="card-body text-center">
                    <h3 class="text-info mb-1"><?php echo $summary['with_units']; ?></h3>
                    <small class="text-muted">With Units</small>
                </div>
            </div>
        </div>
        
        <div class="col-md-2 col-sm-4 col-6">
            <div class="card stat-card-sm">
                <div class="card-body text-center">
                    <h3 class="text-primary mb-1"><?php echo $summary['banners']; ?></h3>
                    <small class="text-muted">Banners</small>
                </div>
            </div>
        </div>
        
        <div class="col-md-2 col-sm-4 col-6">
            <div class="card stat-card-sm">
                <div class="card-body text-center">
                    <h3 class="text-success mb-1"><?php echo $summary['interstitials']; ?></h3>
                    <small class="text-muted">Interstitials</small>
                </div>
            </div>
        </div>
        
        <div class="col-md-2 col-sm-4 col-6">
            <div class="card stat-card-sm">
                <div class="card-body text-center">
                    <h3 class="text-warning mb-1"><?php echo $summary['rewarded']; ?></h3>
                    <small class="text-muted">Rewarded</small>
                </div>
            </div>
        </div>
    </div>

    <!-- Placements Table -->
    <div class="card">
        <div class="card-header bg-light d-flex justify-content-between align-items-center">
            <h5 class="mb-0">
                <i class="bi bi-table me-2"></i>
                All Placements
            </h5>
            <div class="btn-group">
                <button type="button" class="btn btn-sm btn-outline-secondary" onclick="exportPlacements()">
                    <i class="bi bi-download me-1"></i> Export
                </button>
                <button type="button" class="btn btn-sm btn-outline-secondary" onclick="refreshPlacements()">
                    <i class="bi bi-arrow-clockwise me-1"></i> Refresh
                </button>
            </div>
        </div>
        <div class="card-body p-0">
            <div class="table-responsive">
                <table class="table table-hover mb-0" id="placementsTable">
                    <thead class="table-light">
                        <tr>
                            <th>Placement</th>
                            <th>Type</th>
                            <th>Screen</th>
                            <th class="text-center">Units</th>
                            <th class="text-center">Frequency</th>
                            <th class="text-center">Refresh</th>
                            <th class="text-center">Status</th>
                            <th class="text-center">Yesterday</th>
                            <th class="text-center">Actions</th>
                        </tr>
                    </thead>
                    <tbody>
                        <?php if ($placements_result && $placements_result->num_rows > 0): ?>
                            <?php while($placement = $placements_result->fetch_assoc()): 
                                $ctr = $placement['last_impressions'] > 0 ? 
                                    round(($placement['last_clicks'] / $placement['last_impressions']) * 100, 2) : 0;
                                $has_yesterday_data = $placement['last_impressions'] > 0;
                            ?>
                                <tr class="<?php echo $placement['enabled'] ? '' : 'table-secondary'; ?>">
                                    <!-- Placement Key -->
                                    <td>
                                        <div class="d-flex align-items-center">
                                            <div class="icon-wrapper-sm bg-<?php echo $ad_type_colors[$placement['ad_type']]; ?> me-2">
                                                <i class="bi bi-<?php echo $ad_type_icons[$placement['ad_type']]; ?> text-white"></i>
                                            </div>
                                            <div>
                                                <strong class="d-block">
                                                    <code><?php echo htmlspecialchars($placement['key_name']); ?></code>
                                                </strong>
                                                <small class="text-muted">
                                                    <?php echo $placement['auto_disabled'] ? '⚠️ Auto-disabled' : 'Normal'; ?>
                                                </small>
                                            </div>
                                        </div>
                                    </td>
                                    
                                    <!-- Ad Type -->
                                    <td>
                                        <span class="badge bg-<?php echo $ad_type_colors[$placement['ad_type']]; ?>">
                                            <?php echo ucfirst($placement['ad_type']); ?>
                                        </span>
                                    </td>
                                    
                                    <!-- Screen Hint -->
                                    <td>
                                        <small><?php echo htmlspecialchars($placement['screen_hint'] ?: 'N/A'); ?></small>
                                    </td>
                                    
                                    <!-- Units -->
                                    <td class="text-center">
                                        <div class="d-flex justify-content-center gap-1">
                                            <span class="badge bg-secondary" title="Total Units">
                                                <?php echo $placement['total_units']; ?>
                                            </span>
                                            <?php if ($placement['active_units'] > 0): ?>
                                                <span class="badge bg-success" title="Active Units">
                                                    <?php echo $placement['active_units']; ?>
                                                </span>
                                            <?php endif; ?>
                                            <?php if ($placement['live_units'] > 0): ?>
                                                <span class="badge bg-info" title="Live Units">
                                                    <?php echo $placement['live_units']; ?>
                                                </span>
                                            <?php endif; ?>
                                        </div>
                                    </td>
                                    
                                    <!-- Frequency -->
                                    <td class="text-center">
                                        <?php if ($placement['frequency_override']): ?>
                                            <span class="badge bg-warning text-dark" title="Overridden">
                                                <?php echo $placement['frequency_override']; ?>/hr
                                            </span>
                                            <small class="d-block text-muted">
                                                <small>Default: <?php echo $global['default_frequency_per_hour']; ?>/hr</small>
                                            </small>
                                        <?php else: ?>
                                            <span class="badge bg-light text-dark">
                                                <?php echo $global['default_frequency_per_hour']; ?>/hr
                                            </span>
                                            <small class="d-block text-muted">
                                                <small>Default</small>
                                            </small>
                                        <?php endif; ?>
                                    </td>
                                    
                                    <!-- Refresh -->
                                    <td class="text-center">
                                        <?php if ($placement['refresh_seconds']): ?>
                                            <span class="badge bg-info">
                                                <?php echo $placement['refresh_seconds']; ?>s
                                            </span>
                                            <small class="d-block text-muted">
                                                <small>Refresh rate</small>
                                            </small>
                                        <?php else: ?>
                                            <span class="text-muted">—</span>
                                        <?php endif; ?>
                                    </td>
                                    
                                    <!-- Status -->
                                    <td class="text-center">
                                        <div class="form-check form-switch d-inline-block">
                                            <input class="form-check-input" type="checkbox" 
                                                   <?php echo $placement['enabled'] ? 'checked' : ''; ?>
                                                   onchange="togglePlacement(<?php echo $placement['id']; ?>, this.checked)">
                                        </div>
                                        <div>
                                            <small class="text-<?php echo $placement['enabled'] ? 'success' : 'danger'; ?>">
                                                <?php echo $placement['enabled'] ? 'Active' : 'Inactive'; ?>
                                            </small>
                                        </div>
                                    </td>
                                    
                                    <!-- Yesterday's Stats -->
                                    <td class="text-center">
                                        <?php if ($has_yesterday_data): ?>
                                            <div class="small">
                                                <div class="d-flex justify-content-center gap-2">
                                                    <span title="Impressions">
                                                        <i class="bi bi-eye text-primary"></i> 
                                                        <?php echo number_format($placement['last_impressions']); ?>
                                                    </span>
                                                    <span title="Clicks">
                                                        <i class="bi bi-cursor text-success"></i> 
                                                        <?php echo number_format($placement['last_clicks']); ?>
                                                    </span>
                                                </div>
                                                <div>
                                                    <small class="text-<?php echo $ctr > 2 ? 'success' : ($ctr > 0.5 ? 'warning' : 'danger'); ?>">
                                                        CTR: <?php echo $ctr; ?>%
                                                    </small>
                                                </div>
                                            </div>
                                        <?php else: ?>
                                            <span class="text-muted">—</span>
                                        <?php endif; ?>
                                    </td>
                                    
                                    <!-- Actions - WITH TEXT FOR VISIBILITY -->
                                    <td class="text-center">
                                        <div class="btn-group" role="group">
                                            <!-- EDIT BUTTON - COMPLETELY FIXED -->
                                            <button type="button" class="btn btn-sm btn-outline-primary"
                                                    onclick="editPlacement(<?php echo htmlspecialchars(json_encode($placement, JSON_HEX_APOS | JSON_HEX_QUOT | JSON_HEX_TAG | JSON_HEX_AMP), ENT_QUOTES, 'UTF-8'); ?>)"
                                                    title="Edit">
                                                <i class="bi bi-pencil me-1"></i> Edit
                                            </button>
                                            
                                            <!-- UNITS BUTTON -->
                                            <a href="units.php?placement=<?php echo urlencode($placement['key_name']); ?>" 
                                               class="btn btn-sm btn-outline-info" title="View Units">
                                                <i class="bi bi-puzzle me-1"></i> Units
                                            </a>
                                            
                                            <!-- DELETE BUTTON -->
                                            <button type="button" class="btn btn-sm btn-outline-danger"
                                                    onclick="deletePlacement(<?php echo $placement['id']; ?>, '<?php echo htmlspecialchars($placement['key_name'], ENT_QUOTES, 'UTF-8'); ?>')"
                                                    title="Delete">
                                                <i class="bi bi-trash me-1"></i> Delete
                                            </button>
                                        </div>
                                    </td>
                                </tr>
                            <?php endwhile; ?>
                        <?php else: ?>
                            <tr>
                                <td colspan="9" class="text-center py-5">
                                    <i class="bi bi-pin-map display-6 text-muted"></i>
                                    <h5 class="mt-3 text-muted">No Placements Found</h5>
                                    <p class="text-muted">Create your first ad placement to get started</p>
                                    <button type="button" class="btn btn-primary" data-bs-toggle="modal" data-bs-target="#addPlacementModal">
                                        <i class="bi bi-plus-circle me-1"></i> Add Placement
                                    </button>
                                </td>
                            </tr>
                        <?php endif; ?>
                    </tbody>
                </table>
            </div>
        </div>
    </div>
</div>

<!-- Add Placement Modal -->
<div class="modal fade" id="addPlacementModal" tabindex="-1" aria-labelledby="addPlacementModalLabel" aria-hidden="true">
    <div class="modal-dialog modal-lg modal-dialog-centered">
        <div class="modal-content">
            <div class="modal-header">
                <h5 class="modal-title" id="addPlacementModalLabel">
                    <i class="bi bi-plus-circle me-2"></i>
                    Add New Placement
                </h5>
                <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
            </div>
            <form method="POST" action="save_placement.php" id="addPlacementForm">
                <div class="modal-body">
                    <input type="hidden" name="csrf_token" value="<?php echo htmlspecialchars($csrf_token); ?>">
                    <input type="hidden" name="id" value="0">
                    
                    <div class="row g-3">
                        <!-- Basic Information -->
                        <div class="col-md-6">
                            <div class="mb-3">
                                <label for="key_name" class="form-label">Placement Key *</label>
                                <input type="text" class="form-control" id="key_name" name="key_name" 
                                       required placeholder="e.g., home_banner">
                                <div class="form-text">
                                    Unique identifier used in app code (lowercase, underscores)
                                </div>
                            </div>
                        </div>
                        
                        <div class="col-md-6">
                            <div class="mb-3">
                                <label for="ad_type" class="form-label">Ad Type *</label>
                                <select class="form-select" id="ad_type" name="ad_type" required>
                                    <option value="">Select Type</option>
                                    <option value="banner">Banner</option>
                                    <option value="interstitial">Interstitial</option>
                                    <option value="native">Native</option>
                                    <option value="rewarded">Rewarded</option>
                                </select>
                                <div class="form-text">
                                    Type of ad to display
                                </div>
                            </div>
                        </div>
                        
                        <div class="col-12">
                            <div class="mb-3">
                                <label for="screen_hint" class="form-label">Screen / Location</label>
                                <input type="text" class="form-control" id="screen_hint" name="screen_hint" 
                                       placeholder="e.g., Home Screen, Post Details">
                                <div class="form-text">
                                    Where this placement appears in the app
                                </div>
                            </div>
                        </div>
                        
                        <!-- Configuration -->
                        <div class="col-md-6">
                            <div class="mb-3">
                                <label for="refresh_seconds" class="form-label">Refresh Rate (seconds)</label>
                                <input type="number" class="form-control" id="refresh_seconds" name="refresh_seconds" 
                                       min="0" max="3600" placeholder="e.g., 60 for banners">
                                <div class="form-text">
                                    For banner/native ads only. Leave empty for interstitials/rewarded.
                                </div>
                            </div>
                        </div>
                        
                        <div class="col-md-6">
                            <div class="mb-3">
                                <label for="frequency_override" class="form-label">Frequency Override (per hour)</label>
                                <input type="number" class="form-control" id="frequency_override" name="frequency_override" 
                                       min="0" max="20" placeholder="Override global frequency">
                                <div class="form-text">
                                    Default: <?php echo $global['default_frequency_per_hour']; ?> ads/hour
                                </div>
                            </div>
                        </div>
                        
                        <!-- Status -->
                        <div class="col-12">
                            <div class="card">
                                <div class="card-body">
                                    <div class="row">
                                        <div class="col-md-6">
                                            <div class="form-check form-switch">
                                                <input class="form-check-input" type="checkbox" id="enabled" name="enabled" value="1" checked>
                                                <label class="form-check-label" for="enabled">
                                                    <strong>Enable Placement</strong>
                                                </label>
                                                <div class="form-text">
                                                    Placement will be active immediately
                                                </div>
                                            </div>
                                        </div>
                                        <div class="col-md-6">
                                            <div class="alert alert-light">
                                                <small>
                                                    <i class="bi bi-info-circle me-1"></i>
                                                    Placement key must be unique and will be referenced in app code.
                                                </small>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
                <div class="modal-footer">
                    <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Cancel</button>
                    <button type="submit" class="btn btn-primary">Create Placement</button>
                </div>
            </form>
        </div>
    </div>
</div>

<!-- Edit Placement Modal -->
<div class="modal fade" id="editPlacementModal" tabindex="-1" aria-labelledby="editPlacementModalLabel" aria-hidden="true">
    <div class="modal-dialog modal-lg modal-dialog-centered">
        <div class="modal-content">
            <div class="modal-header">
                <h5 class="modal-title" id="editPlacementModalLabel">
                    <i class="bi bi-pencil me-2"></i>
                    Edit Placement
                </h5>
                <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
            </div>
            <form method="POST" action="save_placement.php" id="editPlacementForm">
                <div class="modal-body">
                    <input type="hidden" name="csrf_token" value="<?php echo htmlspecialchars($csrf_token); ?>">
                    <input type="hidden" id="edit_id" name="id" value="">
                    
                    <div class="row g-3">
                        <div class="col-md-6">
                            <div class="mb-3">
                                <label for="edit_key_name" class="form-label">Placement Key</label>
                                <input type="text" class="form-control" id="edit_key_name" name="key_name" readonly>
                                <div class="form-text">
                                    Key cannot be changed after creation
                                </div>
                            </div>
                        </div>
                        
                        <div class="col-md-6">
                            <div class="mb-3">
                                <label for="edit_ad_type" class="form-label">Ad Type</label>
                                <select class="form-select" id="edit_ad_type" name="ad_type" required>
                                    <option value="">Select Type</option>
                                    <option value="banner">Banner</option>
                                    <option value="interstitial">Interstitial</option>
                                    <option value="native">Native</option>
                                    <option value="rewarded">Rewarded</option>
                                </select>
                            </div>
                        </div>
                        
                        <div class="col-12">
                            <div class="mb-3">
                                <label for="edit_screen_hint" class="form-label">Screen / Location</label>
                                <input type="text" class="form-control" id="edit_screen_hint" name="screen_hint">
                            </div>
                        </div>
                        
                        <div class="col-md-6">
                            <div class="mb-3">
                                <label for="edit_refresh_seconds" class="form-label">Refresh Rate (seconds)</label>
                                <input type="number" class="form-control" id="edit_refresh_seconds" name="refresh_seconds" 
                                       min="0" max="3600">
                            </div>
                        </div>
                        
                        <div class="col-md-6">
                            <div class="mb-3">
                                <label for="edit_frequency_override" class="form-label">Frequency Override (per hour)</label>
                                <input type="number" class="form-control" id="edit_frequency_override" name="frequency_override" 
                                       min="0" max="20">
                                <div class="form-text">
                                    Leave empty to use default (<?php echo $global['default_frequency_per_hour']; ?> ads/hour)
                                </div>
                            </div>
                        </div>
                        
                        <div class="col-12">
                            <div class="card">
                                <div class="card-body">
                                    <div class="form-check form-switch">
                                        <input class="form-check-input" type="checkbox" id="edit_enabled" name="enabled" value="1">
                                        <label class="form-check-label" for="edit_enabled">
                                            <strong>Enable Placement</strong>
                                        </label>
                                    </div>
                                    <div class="form-check form-switch mt-2">
                                        <input class="form-check-input" type="checkbox" id="edit_auto_disabled" name="auto_disabled" value="1">
                                        <label class="form-check-label" for="edit_auto_disabled">
                                            <strong>Auto-disabled</strong>
                                        </label>
                                        <div class="form-text">
                                            System will automatically disable if performance is poor
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
                <div class="modal-footer">
                    <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Cancel</button>
                    <button type="submit" class="btn btn-primary">Save Changes</button>
                </div>
            </form>
        </div>
    </div>
</div>

<script>
    
    // TEMPORARY DEBUG - REMOVE LATER
console.log("Page loaded successfully");
console.log("CSRF Token:", '<?php echo $csrf_token; ?>');
console.log("Bootstrap available:", typeof bootstrap !== 'undefined');

// Test JSON parsing
const testJson = '{"id":1,"key_name":"test"}';
console.log("JSON test:", JSON.parse(testJson));

    
    
function togglePlacement(placementId, enabled) {
    if (confirm(`Are you sure you want to ${enabled ? 'enable' : 'disable'} this placement?`)) {
        const form = document.createElement('form');
        form.method = 'POST';
        form.action = 'toggle_placement.php';
        form.style.display = 'none';
        
        const csrfInput = document.createElement('input');
        csrfInput.type = 'hidden';
        csrfInput.name = 'csrf_token';
        csrfInput.value = '<?php echo htmlspecialchars($csrf_token); ?>';
        form.appendChild(csrfInput);
        
        const idInput = document.createElement('input');
        idInput.type = 'hidden';
        idInput.name = 'id';
        idInput.value = placementId;
        form.appendChild(idInput);
        
        const enabledInput = document.createElement('input');
        enabledInput.type = 'hidden';
        enabledInput.name = 'enabled';
        enabledInput.value = enabled ? '1' : '0';
        form.appendChild(enabledInput);
        
        document.body.appendChild(form);
        form.submit();
    } else {
        // Reset checkbox if cancelled
        event.target.checked = !enabled;
    }
}

function editPlacement(placementJson) {
    try {
        // Parse JSON if it's a string
        const placement = typeof placementJson === 'string' 
            ? JSON.parse(placementJson) 
            : placementJson;
        
        console.log("Editing placement:", placement);
        
        // Fill form fields
        document.getElementById('edit_id').value = placement.id;
        document.getElementById('edit_key_name').value = placement.key_name;
        document.getElementById('edit_ad_type').value = placement.ad_type;
        document.getElementById('edit_screen_hint').value = placement.screen_hint || '';
        document.getElementById('edit_refresh_seconds').value = placement.refresh_seconds || '';
        document.getElementById('edit_frequency_override').value = placement.frequency_override || '';
        document.getElementById('edit_enabled').checked = placement.enabled == 1;
        document.getElementById('edit_auto_disabled').checked = placement.auto_disabled == 1;
        
        // Show modal
        const modal = new bootstrap.Modal(document.getElementById('editPlacementModal'));
        modal.show();
    } catch (error) {
        console.error("Error parsing placement data:", error);
        alert("Error loading placement data. Please try again.");
    }
}


// Debug: Log all button clicks
document.addEventListener('DOMContentLoaded', function() {
    document.querySelectorAll('button[onclick*="editPlacement"]').forEach(btn => {
        btn.addEventListener('click', function(e) {
            console.log("Edit button clicked");
            console.log("Onclick attribute:", this.getAttribute('onclick'));
        });
    });
    
    document.querySelectorAll('button[onclick*="deletePlacement"]').forEach(btn => {
        btn.addEventListener('click', function(e) {
            console.log("Delete button clicked");
        });
    });
});



function deletePlacement(placementId, placementName) {
    if (confirm(`Are you sure you want to delete placement "${placementName}"?\n\nThis will also delete all associated ad units.`)) {
        const form = document.createElement('form');
        form.method = 'POST';
        form.action = 'delete_placement.php';
        form.style.display = 'none';
        
        const csrfInput = document.createElement('input');
        csrfInput.type = 'hidden';
        csrfInput.name = 'csrf_token';
        csrfInput.value = '<?php echo htmlspecialchars($csrf_token); ?>';
        form.appendChild(csrfInput);
        
        const idInput = document.createElement('input');
        idInput.type = 'hidden';
        idInput.name = 'id';
        idInput.value = placementId;
        form.appendChild(idInput);
        
        document.body.appendChild(form);
        form.submit();
    }
}

function exportPlacements() {
    const table = document.getElementById('placementsTable');
    const rows = table.querySelectorAll('tr');
    let csv = [];
    
    rows.forEach(row => {
        const rowData = [];
        row.querySelectorAll('th, td').forEach(cell => {
            let text = cell.textContent.replace(/\n/g, ' ').trim();
            text = text.replace(/,/g, ';'); // Avoid CSV issues
            rowData.push(`"${text}"`);
        });
        csv.push(rowData.join(','));
    });
    
    const csvContent = csv.join('\n');
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const link = document.createElement('a');
    const url = URL.createObjectURL(blob);
    
    link.setAttribute('href', url);
    link.setAttribute('download', `placements-export-<?php echo date('Y-m-d'); ?>.csv`);
    link.style.visibility = 'hidden';
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
}

function refreshPlacements() {
    const btn = event.target;
    const originalText = btn.innerHTML;
    
    btn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span> Refreshing...';
    btn.disabled = true;
    
    setTimeout(() => {
        window.location.reload();
    }, 1000);
}

// Form validation
document.getElementById('addPlacementForm').addEventListener('submit', function(e) {
    const keyName = document.getElementById('key_name').value.trim();
    const adType = document.getElementById('ad_type').value;
    
    if (!keyName || !adType) {
        e.preventDefault();
        alert('Please fill in all required fields');
        return false;
    }
    
    // Validate key format
    if (!/^[a-z][a-z0-9_]*$/.test(keyName)) {
        e.preventDefault();
        alert('Placement key must start with lowercase letter and contain only lowercase letters, numbers, and underscores');
        return false;
    }
});

// Show/hide fields based on ad type
document.getElementById('ad_type').addEventListener('change', function() {
    const adType = this.value;
    const refreshField = document.getElementById('refresh_seconds');
    
    // Hide refresh for interstitial and rewarded
    if (adType === 'interstitial' || adType === 'rewarded') {
        refreshField.value = '';
        refreshField.disabled = true;
        refreshField.parentElement.classList.add('text-muted');
    } else {
        refreshField.disabled = false;
        refreshField.parentElement.classList.remove('text-muted');
    }
});

document.getElementById('edit_ad_type').addEventListener('change', function() {
    const adType = this.value;
    const refreshField = document.getElementById('edit_refresh_seconds');
    
    if (adType === 'interstitial' || adType === 'rewarded') {
        refreshField.disabled = true;
        refreshField.parentElement.classList.add('text-muted');
    } else {
        refreshField.disabled = false;
        refreshField.parentElement.classList.remove('text-muted');
    }
});

// Sidebar toggle
function toggleSidebar() {
    document.querySelector('.sidebar').classList.toggle('show');
    document.querySelector('.sidebar-overlay').classList.toggle('show');
}


// Catch all JavaScript errors
window.onerror = function(msg, url, line, col, error) {
    console.error("JavaScript Error:", msg, "at", url + ":" + line);
    alert("JavaScript Error: " + msg);
    return false;
};

// Also add this to catch promise rejections
window.addEventListener('unhandledrejection', function(event) {
    console.error("Unhandled Promise Rejection:", event.reason);
    alert("JavaScript Error: " + event.reason);
});


</script>

<style>
.stat-card-sm {
    border: none;
    border-radius: 10px;
    box-shadow: 0 2px 10px rgba(0,0,0,0.08);
    transition: transform 0.2s;
}

.stat-card-sm:hover {
    transform: translateY(-2px);
}

.icon-wrapper-sm {
    width: 36px;
    height: 36px;
    border-radius: 8px;
    display: flex;
    align-items: center;
    justify-content: center;
}

.placement-stats {
    font-size: 0.85rem;
}

.placement-stats .badge {
    font-size: 0.75rem;
    padding: 0.25em 0.5em;
}
</style>


<!-- Bootstrap Icons CDN (Essential) -->
<link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.10.0/font/bootstrap-icons.css">

<!-- Bootstrap CSS -->
<link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">


<?php include '../../includes/footer.php'; ?>