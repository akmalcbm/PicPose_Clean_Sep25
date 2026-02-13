<?php
session_start();
require '../../config.php';

// Debug: Check what's in the database
$debug_sql = "SELECT u.id, u.ad_unit_id, u.ad_unit_name, u.placement_id, u.network_id, 
                     p.key_name as placement_name, n.code as network_code
              FROM ad_network_units u
              LEFT JOIN ad_placements p ON u.placement_id = p.id
              LEFT JOIN ad_networks n ON u.network_id = n.id
              LIMIT 10";
$debug_result = $conn->query($debug_sql);
echo "<!-- Debug: Found " . $debug_result->num_rows . " units in DB -->";

// Use the SAME session check as main admin panel
if (!isset($_SESSION['admin']) || empty($_SESSION['admin'])) {
    // Redirect to main admin login if not logged in
    header("Location: " . BASE_URL . "/login.php");
    exit();
}

// Define cleanInput function if it doesn't exist
if (!function_exists('cleanInput')) {
    function cleanInput($data) {
        if (empty($data)) return '';
        $data = trim($data);
        $data = stripslashes($data);
        $data = htmlspecialchars($data, ENT_QUOTES, 'UTF-8');
        return $data;
    }
}

// CSRF protection
if (empty($_SESSION['csrf_token'])) {
    $_SESSION['csrf_token'] = bin2hex(random_bytes(32));
}
$csrf_token = $_SESSION['csrf_token'];

// Get filter parameters
$placement_filter = isset($_GET['placement']) ? cleanInput($_GET['placement']) : '';
$network_filter = isset($_GET['network']) ? cleanInput($_GET['network']) : '';
$status_filter = isset($_GET['status']) ? cleanInput($_GET['status']) : '';
$type_filter = isset($_GET['type']) ? cleanInput($_GET['type']) : '';

// Get filter parameters
$placement_filter = isset($_GET['placement']) ? cleanInput($_GET['placement']) : '';
$network_filter = isset($_GET['network']) ? cleanInput($_GET['network']) : '';
$status_filter = isset($_GET['status']) ? cleanInput($_GET['status']) : '';
$type_filter = isset($_GET['type']) ? cleanInput($_GET['type']) : '';

// Build WHERE clause for filters
$where_clauses = [];
$params = [];
$types = '';

if ($placement_filter) {
    $where_clauses[] = "p.key_name = ?";
    $params[] = $placement_filter;
    $types .= 's';
}

if ($network_filter) {
    $where_clauses[] = "n.code = ?";
    $params[] = $network_filter;
    $types .= 's';
}

if ($status_filter === 'active') {
    $where_clauses[] = "u.enabled = 1";
} elseif ($status_filter === 'inactive') {
    $where_clauses[] = "u.enabled = 0";
}

if ($type_filter === 'test') {
    $where_clauses[] = "u.is_test = 1";
} elseif ($type_filter === 'live') {
    $where_clauses[] = "u.is_live = 1";
} elseif ($type_filter === 'production') {
    $where_clauses[] = "u.is_test = 0";
}

$where_sql = $where_clauses ? "WHERE " . implode(" AND ", $where_clauses) : "";

// Fetch units with filters
try {
    // पहले network id और code को सही तरीके से लाएं
    $units_sql = "
        SELECT u.*, 
               p.key_name as placement_key,
               p.ad_type as placement_type,
               p.screen_hint,
               n.id as network_id,
               n.code as network_code,
               n.display_name as network_name,
               COALESCE(a.impressions, 0) as total_impressions,
               COALESCE(a.clicks, 0) as total_clicks,
               COALESCE(a.revenue, 0) as total_revenue
        FROM ad_network_units u
        INNER JOIN ad_placements p ON u.placement_id = p.id
        INNER JOIN ad_networks n ON u.network_id = n.id
        LEFT JOIN (
            SELECT placement_key,
                   SUM(impressions) as impressions,
                   SUM(clicks) as clicks,
                   SUM(revenue) as revenue
            FROM ad_analytics
            GROUP BY placement_key
        ) a ON p.key_name = a.placement_key
        $where_sql
        ORDER BY u.priority ASC, u.created_at DESC
    ";
    
    // Debug के लिए query print करें
    error_log("Units Query: " . $units_sql);
    
    if ($params) {
        $units_stmt = $conn->prepare($units_sql);
        $units_stmt->bind_param($types, ...$params);
        $units_stmt->execute();
        $units_result = $units_stmt->get_result();
    } else {
        $units_result = $conn->query($units_sql);
    }
    
    // Check result
    if (!$units_result) {
        throw new Exception("Query failed: " . $conn->error);
    }
    
    error_log("Units found: " . $units_result->num_rows);
    
} catch (Exception $e) {
    error_log("Units Query Error: " . $e->getMessage());
    // Debug के लिए query error show करें
    echo "<div class='alert alert-danger'>Query Error: " . htmlspecialchars($e->getMessage()) . "</div>";
    $units_result = null;
}

// Fetch filter options
$placements_result = $conn->query("SELECT id, key_name, ad_type FROM ad_placements ORDER BY key_name");
$networks_result = $conn->query("SELECT id, code, display_name FROM ad_networks WHERE enabled = 1 ORDER BY display_name");

// Statistics
try {
    $stats_sql = "
        SELECT 
            COUNT(*) as total_units,
            SUM(CASE WHEN enabled = 1 THEN 1 ELSE 0 END) as active_units,
            SUM(CASE WHEN is_test = 1 THEN 1 ELSE 0 END) as test_units,
            SUM(CASE WHEN is_live = 1 THEN 1 ELSE 0 END) as live_units,
            COUNT(DISTINCT network_id) as unique_networks,
            COUNT(DISTINCT placement_id) as unique_placements
        FROM ad_network_units
    ";
    
    $stats_result = $conn->query($stats_sql);
    $stats = $stats_result->fetch_assoc();
} catch (Exception $e) {
    error_log("Stats Query Error: " . $e->getMessage());
    $stats = [
        'total_units' => 0,
        'active_units' => 0,
        'test_units' => 0,
        'live_units' => 0,
        'unique_networks' => 0,
        'unique_placements' => 0
    ];
}

include '../../includes/header.php';
?>

<!-- Units Table से पहले यह जोड़ें Temporary Debgg   -->
<div class="alert alert-info mb-3">
    <h6>Debug Information:</h6>
    <p>Total Units in DB: <?php echo $stats['total_units']; ?></p>
    <p>Active Units: <?php echo $stats['active_units']; ?></p>
    <p>Query returned: <?php echo $units_result ? $units_result->num_rows : 'NULL'; ?> rows</p>
    <p>Filters: 
        Placement: <?php echo $placement_filter ?: 'All'; ?>, 
        Network: <?php echo $network_filter ?: 'All'; ?>,
        Status: <?php echo $status_filter ?: 'All'; ?>,
        Type: <?php echo $type_filter ?: 'All'; ?>
    </p>
</div>

<div class="container-fluid">
    <!-- Page Header -->
    <div class="page-header mb-4">
        <div class="d-flex justify-content-between align-items-center">
            <div class="d-flex align-items-center gap-3">
                <div class="icon-wrapper bg-primary">
                    <i class="bi bi-puzzle text-white"></i>
                </div>
                <div>
                    <h1 class="mb-1">Ad Units Management</h1>
                    <nav aria-label="breadcrumb">
                        <ol class="breadcrumb mb-0">
                            <li class="breadcrumb-item"><a href="../dashboard.php">Dashboard</a></li>
                            <li class="breadcrumb-item"><a href="index.php">Ads Management</a></li>
                            <li class="breadcrumb-item active">Ad Units</li>
                        </ol>
                    </nav>
                </div>
            </div>
            
            <div class="btn-group">
                <a href="index.php" class="btn btn-outline-secondary">
                    <i class="bi bi-arrow-left me-1"></i> Back
                </a>
                <button type="button" class="btn btn-primary" onclick="scrollToAddForm()">
                    <i class="bi bi-plus-circle me-1"></i> Add Unit
                </button>
            </div>
        </div>
    </div>

    <!-- Success/Error Messages -->
    <?php if (isset($_SESSION['success'])): ?>
        <div class="alert alert-success alert-dismissible fade show mb-4" role="alert">
            <i class="bi bi-check-circle me-2"></i>
            <?php echo htmlspecialchars($_SESSION['success']); ?>
            <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
        </div>
        <?php unset($_SESSION['success']); ?>
    <?php endif; ?>

    <?php if (isset($_SESSION['error'])): ?>
        <div class="alert alert-danger alert-dismissible fade show mb-4" role="alert">
            <i class="bi bi-exclamation-triangle me-2"></i>
            <?php echo htmlspecialchars($_SESSION['error']); ?>
            <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
        </div>
        <?php unset($_SESSION['error']); ?>
    <?php endif; ?>

    <?php if (isset($_SESSION['warning_preserved'])): ?>
        <div class="alert alert-warning alert-dismissible fade show mb-4" role="alert">
            <i class="bi bi-exclamation-triangle me-2"></i>
            <?php echo htmlspecialchars($_SESSION['warning_preserved']); ?>
            <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
        </div>
        <?php unset($_SESSION['warning_preserved']); ?>
    <?php endif; ?>

    <!-- Stats Overview -->
    <div class="row g-3 mb-4">
        <div class="col-md-2 col-sm-4 col-6">
            <div class="card stat-card-sm">
                <div class="card-body text-center">
                    <h3 class="text-primary mb-1"><?php echo $stats['total_units']; ?></h3>
                    <small class="text-muted">Total Units</small>
                </div>
            </div>
        </div>
        
        <div class="col-md-2 col-sm-4 col-6">
            <div class="card stat-card-sm">
                <div class="card-body text-center">
                    <h3 class="text-success mb-1"><?php echo $stats['active_units']; ?></h3>
                    <small class="text-muted">Active</small>
                </div>
            </div>
        </div>
        
        <div class="col-md-2 col-sm-4 col-6">
            <div class="card stat-card-sm">
                <div class="card-body text-center">
                    <h3 class="text-warning mb-1"><?php echo $stats['test_units']; ?></h3>
                    <small class="text-muted">Test Units</small>
                </div>
            </div>
        </div>
        
        <div class="col-md-2 col-sm-4 col-6">
            <div class="card stat-card-sm">
                <div class="card-body text-center">
                    <h3 class="text-info mb-1"><?php echo $stats['live_units']; ?></h3>
                    <small class="text-muted">Live Units</small>
                </div>
            </div>
        </div>
        
        <div class="col-md-2 col-sm-4 col-6">
            <div class="card stat-card-sm">
                <div class="card-body text-center">
                    <h3 class="text-secondary mb-1"><?php echo $stats['unique_networks']; ?></h3>
                    <small class="text-muted">Networks</small>
                </div>
            </div>
        </div>
        
        <div class="col-md-2 col-sm-4 col-6">
            <div class="card stat-card-sm">
                <div class="card-body text-center">
                    <h3 class="text-dark mb-1"><?php echo $stats['unique_placements']; ?></h3>
                    <small class="text-muted">Placements</small>
                </div>
            </div>
        </div>
    </div>

    <!-- Filters -->
    <div class="card mb-4">
        <div class="card-header bg-light">
            <h6 class="mb-0">
                <i class="bi bi-funnel me-2"></i>
                Filters
            </h6>
        </div>
        <div class="card-body">
            <form method="GET" action="" class="row g-3">
                <div class="col-md-3">
                    <label class="form-label">Placement</label>
                    <select name="placement" class="form-select" onchange="this.form.submit()">
                        <option value="">All Placements</option>
                        <?php while($placement = $placements_result->fetch_assoc()): ?>
                            <option value="<?php echo htmlspecialchars($placement['key_name']); ?>" 
                                <?php echo $placement_filter == $placement['key_name'] ? 'selected' : ''; ?>>
                                <?php echo htmlspecialchars($placement['key_name']); ?> (<?php echo $placement['ad_type']; ?>)
                            </option>
                        <?php endwhile; ?>
                    </select>
                </div>
                
                <div class="col-md-3">
                    <label class="form-label">Network</label>
                    <select name="network" class="form-select" onchange="this.form.submit()">
                        <option value="">All Networks</option>
                        <?php while($network = $networks_result->fetch_assoc()): ?>
                            <option value="<?php echo htmlspecialchars($network['code']); ?>"
                                <?php echo $network_filter == $network['code'] ? 'selected' : ''; ?>>
                                <?php echo htmlspecialchars($network['display_name']); ?>
                            </option>
                        <?php endwhile; ?>
                    </select>
                </div>
                
                <div class="col-md-2">
                    <label class="form-label">Status</label>
                    <select name="status" class="form-select" onchange="this.form.submit()">
                        <option value="">All Status</option>
                        <option value="active" <?php echo $status_filter == 'active' ? 'selected' : ''; ?>>Active Only</option>
                        <option value="inactive" <?php echo $status_filter == 'inactive' ? 'selected' : ''; ?>>Inactive Only</option>
                    </select>
                </div>
                
                <div class="col-md-2">
                    <label class="form-label">Type</label>
                    <select name="type" class="form-select" onchange="this.form.submit()">
                        <option value="">All Types</option>
                        <option value="test" <?php echo $type_filter == 'test' ? 'selected' : ''; ?>>Test Only</option>
                        <option value="live" <?php echo $type_filter == 'live' ? 'selected' : ''; ?>>Live Only</option>
                        <option value="production" <?php echo $type_filter == 'production' ? 'selected' : ''; ?>>Production Only</option>
                    </select>
                </div>
                
                <div class="col-md-2 d-flex align-items-end">
                    <button type="submit" class="btn btn-primary w-100">
                        <i class="bi bi-filter me-1"></i> Apply
                    </button>
                </div>
            </form>
            
            <?php if ($placement_filter || $network_filter || $status_filter || $type_filter): ?>
                <div class="mt-3">
                    <small class="text-muted">
                        Filtered by: 
                        <?php 
                        $filters = [];
                        if ($placement_filter) $filters[] = "Placement: $placement_filter";
                        if ($network_filter) $filters[] = "Network: $network_filter";
                        if ($status_filter) $filters[] = "Status: $status_filter";
                        if ($type_filter) $filters[] = "Type: $type_filter";
                        echo implode(', ', $filters);
                        ?>
                        <a href="units.php" class="ms-2">Clear all</a>
                    </small>
                </div>
            <?php endif; ?>
        </div>
    </div>

    <!-- Add Unit Form -->
    <div class="card mb-4" id="addForm">
        <div class="card-header bg-light">
            <h5 class="mb-0">
                <i class="bi bi-plus-circle me-2"></i>
                Add New Ad Unit
            </h5>
        </div>
        <div class="card-body">
            <form action="save_unit.php" method="POST" id="unitForm">
                <input type="hidden" name="csrf_token" value="<?php echo htmlspecialchars($csrf_token); ?>">
                <input type="hidden" name="id" id="editId" value="0">
                
                <div class="row g-3">
                    <div class="col-md-3">
                        <label class="form-label">Placement *</label>
                        <select name="placement_id" class="form-select" required id="placementSelect">
                            <option value="">Select Placement</option>
                            <?php 
                            $placements_result->data_seek(0);
                            while($placement = $placements_result->fetch_assoc()): ?>
                                <option value="<?php echo $placement['id']; ?>" 
                                    data-type="<?php echo $placement['ad_type']; ?>">
                                    <?php echo htmlspecialchars($placement['key_name']); ?> (<?php echo $placement['ad_type']; ?>)
                                </option>
                            <?php endwhile; ?>
                        </select>
                    </div>
                    
                    <div class="col-md-3">
                        <label class="form-label">Network *</label>
                        <select name="network_id" class="form-select" required id="networkSelect">
                            <option value="">Select Network</option>
                            <?php 
                            // Reset pointer
                            $networks_result->data_seek(0);
                            while($network = $networks_result->fetch_assoc()): 
                            ?>
                                <option value="<?php echo $network['id']; ?>" 
                                        data-code="<?php echo htmlspecialchars($network['code']); ?>">
                                    <?php echo htmlspecialchars($network['display_name']); ?>
                                </option>
                            <?php endwhile; ?>
                        </select>
                    </div>
                    
                    <div class="col-md-3">
                        <label class="form-label">Ad Unit Name</label>
                        <input type="text" name="ad_unit_name" class="form-control" 
                               id="adUnitName" placeholder="e.g., Home Banner Test">
                    </div>
                    
                    <div class="col-md-3">
                        <label class="form-label">Priority</label>
                        <select name="priority" class="form-select" id="prioritySelect">
                            <?php for($i = 1; $i <= 10; $i++): ?>
                                <option value="<?php echo $i; ?>" <?php echo $i == 1 ? 'selected' : ''; ?>>
                                    <?php echo $i; ?> <?php echo $i == 1 ? '(Highest)' : ''; ?>
                                </option>
                            <?php endfor; ?>
                        </select>
                    </div>
                    
                    <div class="col-md-8">
                        <label class="form-label">Ad Unit ID *</label>
                        <input type="text" name="ad_unit_id" class="form-control" 
                               id="adUnitId" placeholder="Enter Ad Unit ID" required>
                        <div class="form-text" id="adUnitHelp">
                            Enter the ad unit ID for selected network
                        </div>
                    </div>
                    
                    <div class="col-md-4">
                        <label class="form-label">Country Code</label>
                        <input type="text" name="country_code" class="form-control" 
                               placeholder="e.g., US, IN (optional)" maxlength="2">
                        <div class="form-text">
                            2-letter ISO country code for geo-targeting
                        </div>
                    </div>
                    
                    <div class="col-md-12">
                        <label class="form-label">Notes</label>
                        <textarea name="notes" class="form-control" rows="2" 
                                  placeholder="Any notes about this ad unit..."></textarea>
                    </div>
                    
                    <!-- Toggle Switches -->
                    <div class="col-md-3">
                        <div class="form-check form-switch">
                            <input class="form-check-input" type="checkbox" name="enabled" 
                                   id="enabledSwitch" value="1" checked>
                            <label class="form-check-label" for="enabledSwitch">
                                <strong>Enabled</strong>
                            </label>
                        </div>
                    </div>
                    
                    <div class="col-md-3">
                        <div class="form-check form-switch">
                            <input class="form-check-input" type="checkbox" name="is_test" 
                                   id="testSwitch" value="1" checked>
                            <label class="form-check-label" for="testSwitch">
                                <strong>Test ID</strong>
                            </label>
                        </div>
                    </div>
                    
                    <div class="col-md-3">
                        <div class="form-check form-switch">
                            <input class="form-check-input" type="checkbox" name="is_live" 
                                   id="liveSwitch" value="1">
                            <label class="form-check-label" for="liveSwitch">
                                <strong>Mark as Live</strong>
                            </label>
                        </div>
                    </div>
                    
                    <div class="col-md-3">
                        <div class="form-check form-switch">
                            <input class="form-check-input" type="checkbox" name="sdk_required" 
                                   id="sdkSwitch" value="1" checked>
                            <label class="form-check-label" for="sdkSwitch">
                                <strong>SDK Required</strong>
                            </label>
                        </div>
                    </div>
                </div>
                
                <div class="mt-4">
                    <button type="submit" class="btn btn-primary" id="submitBtn">
                        <i class="bi bi-save me-1"></i> Save Ad Unit
                    </button>
                    <button type="button" class="btn btn-secondary" onclick="resetForm()">
                        <i class="bi bi-x-circle me-1"></i> Reset
                    </button>
                    <button type="button" class="btn btn-outline-info" onclick="testAdUnit()">
                        <i class="bi bi-play-circle me-1"></i> Test Connection
                    </button>
                </div>
            </form>
        </div>
    </div>

    <!-- Units Table -->
    <div class="card">
        <div class="card-header bg-light d-flex justify-content-between align-items-center">
            <h5 class="mb-0">
                <i class="bi bi-table me-2"></i>
                Ad Units (<?php echo $units_result ? $units_result->num_rows : 0; ?>)
            </h5>
            <div class="btn-group">
                <button type="button" class="btn btn-sm btn-outline-secondary" onclick="exportUnits()">
                    <i class="bi bi-download me-1"></i> Export
                </button>
                <button type="button" class="btn btn-sm btn-outline-secondary" onclick="refreshTable()">
                    <i class="bi bi-arrow-clockwise me-1"></i> Refresh
                </button>
            </div>
        </div>
        <div class="card-body p-0">
            <div class="table-responsive">
                <table class="table table-hover mb-0" id="unitsTable">
                    <thead class="table-light">
                        <tr>
                            <th>ID</th>
                            <th>Placement</th>
                            <th>Network</th>
                            <th>Ad Unit</th>
                            <th class="text-center">Priority</th>
                            <th class="text-center">Type</th>
                            <th class="text-center">Status</th>
                            <th class="text-center">Performance</th>
                            <th class="text-center">Actions</th>
                        </tr>
                    </thead>
                    <tbody>
                        <?php if ($units_result && $units_result->num_rows > 0): ?>
                            <?php while($unit = $units_result->fetch_assoc()): 
                                $is_admob = ($unit['network_code'] ?? '') === 'admob';
                                $ctr = $unit['total_impressions'] > 0 ? 
                                    round(($unit['total_clicks'] / $unit['total_impressions']) * 100, 2) : 0;
                            ?>
                                <tr class="<?php echo $unit['enabled'] ? '' : 'table-secondary'; ?>">
                                    <td><?php echo $unit['id']; ?></td>
                                    
                                    <td>
                                        <div>
                                            <strong class="d-block">
                                                <code><?php echo htmlspecialchars($unit['placement_key']); ?></code>
                                            </strong>
                                            <small class="text-muted">
                                                <?php echo $unit['placement_type']; ?>
                                                <?php if ($unit['screen_hint']): ?>
                                                    • <?php echo htmlspecialchars($unit['screen_hint']); ?>
                                                <?php endif; ?>
                                            </small>
                                        </div>
                                    </td>
                                    
                                    <td>
                                        <span class="badge bg-secondary">
                                            <?php echo htmlspecialchars($unit['network_name']); ?>
                                        </span>
                                        <div class="small text-muted">
                                            <?php echo $unit['network_code']; ?>
                                        </div>
                                    </td>
                                    
                                    <td>
                                        <div class="d-flex align-items-start">
                                            <?php if ($unit['is_test']): ?>
                                                <span class="badge bg-warning text-dark me-2">TEST</span>
                                            <?php endif; ?>
                                            <?php if ($unit['is_live']): ?>
                                                <span class="badge bg-success me-2">LIVE</span>
                                            <?php endif; ?>
                                            <div>
                                                <?php if ($unit['ad_unit_name']): ?>
                                                    <strong class="d-block"><?php echo htmlspecialchars($unit['ad_unit_name']); ?></strong>
                                                <?php endif; ?>
                                                <code class="small d-block"><?php echo htmlspecialchars($unit['ad_unit_id']); ?></code>
                                                <?php if ($unit['country_code']): ?>
                                                    <small class="text-muted">
                                                        <i class="bi bi-geo-alt"></i> <?php echo $unit['country_code']; ?>
                                                    </small>
                                                <?php endif; ?>
                                            </div>
                                        </div>
                                    </td>
                                    
                                    <td class="text-center">
                                        <span class="badge bg-<?php 
                                            echo $unit['priority'] == 1 ? 'primary' : 
                                                 ($unit['priority'] <= 3 ? 'info' : 'secondary'); 
                                        ?>">
                                            <?php echo $unit['priority']; ?>
                                        </span>
                                    </td>
                                    
                                    <td class="text-center">
                                        <?php if ($unit['is_test']): ?>
                                            <span class="badge bg-warning text-dark">Test ID</span>
                                        <?php else: ?>
                                            <span class="badge bg-success">Production</span>
                                        <?php endif; ?>
                                    </td>
                                    
                                    <td class="text-center">
                                        <div class="form-check form-switch d-inline-block">
                                            <input class="form-check-input" type="checkbox" 
                                                   <?php echo $unit['enabled'] ? 'checked' : ''; ?>
                                                   onchange="toggleUnit(<?php echo $unit['id']; ?>, this.checked)">
                                        </div>
                                        <div>
                                            <small class="text-<?php echo $unit['enabled'] ? 'success' : 'danger'; ?>">
                                                <?php echo $unit['enabled'] ? 'Active' : 'Inactive'; ?>
                                            </small>
                                        </div>
                                    </td>
                                    
                                    <td class="text-center">
                                        <?php if ($unit['total_impressions'] > 0): ?>
                                            <div class="small">
                                                <div>
                                                    <span class="text-primary"><?php echo number_format($unit['total_impressions']); ?></span>
                                                    <span class="text-muted">imp</span>
                                                </div>
                                                <div>
                                                    <span class="text-success"><?php echo number_format($unit['total_clicks']); ?></span>
                                                    <span class="text-muted">clicks</span>
                                                </div>
                                                <div>
                                                    <span class="text-<?php echo $ctr > 2 ? 'success' : ($ctr > 0.5 ? 'warning' : 'danger'); ?>">
                                                        <?php echo $ctr; ?>% CTR
                                                    </span>
                                                </div>
                                            </div>
                                        <?php else: ?>
                                            <span class="text-muted">No data</span>
                                        <?php endif; ?>
                                    </td>
                                    
                                    <td class="text-center">
                                        <div class="btn-group btn-group-sm" role="group">
                                            <!-- Edit Button -->
                                            <button class="btn btn-outline-primary" 
                                                    onclick="editUnit(<?php echo htmlspecialchars(json_encode($unit)); ?>)"
                                                    title="Edit">
                                                <i class="bi bi-pencil"></i> Edit
                                            </button>
                                            
                                            <!-- Duplicate Button - FIXED -->
                                            <button class="btn btn-outline-info" 
                                                    onclick="duplicateUnit(<?php echo $unit['id']; ?>)"
                                                    title="Duplicate">
                                                <i class="bi bi-files"></i> Copy
                                            </button>
                                            
                                            <!-- Delete Button -->
                                            <button class="btn btn-outline-danger"
                                                    onclick="deleteUnit(<?php echo $unit['id']; ?>, '<?php echo htmlspecialchars($unit['ad_unit_name'] ?: $unit['ad_unit_id']); ?>')"
                                                    title="Delete">
                                                <i class="bi bi-trash"></i> Delete
                                            </button>
                                        </div>
                                    </td>
                                </tr>
                            <?php endwhile; ?>
                        <?php else: ?>
                            <tr>
                                <td colspan="9" class="text-center py-5">
                                    <i class="bi bi-inbox display-6 text-muted"></i>
                                    <h5 class="mt-3 text-muted">No Ad Units Found</h5>
                                    <p class="text-muted">
                                        <?php if ($placement_filter || $network_filter || $status_filter || $type_filter): ?>
                                            No units match your filters. <a href="units.php">Clear filters</a>
                                        <?php else: ?>
                                            Add your first ad unit to get started
                                        <?php endif; ?>
                                    </p>
                                </td>
                            </tr>
                        <?php endif; ?>
                    </tbody>
                </table>
            </div>
        </div>
    </div>
</div>

<script>
// Network-specific help text and validation
const networkHelp = {
    'admob': {
        placeholder: 'ca-app-pub-xxxxxxxxxxxxxxxx/yyyyyyyyyy',
        help: 'AdMob format: ca-app-pub-{publisher-id}/{ad-unit-id}',
        testPattern: '^ca-app-pub-3940256099942544/\\d+$',
        livePattern: '^ca-app-pub-\\d+/\\d+$'
    },
    'facebook': {
        placeholder: 'IMG_16_9_APP_INSTALL#YOUR_PLACEMENT_ID',
        help: 'Facebook format: {ad_format}#{placement_id}',
        testPattern: '.*',
        livePattern: '.*'
    },
    'unity': {
        placeholder: '1234567',
        help: 'Unity Ads Placement ID',
        testPattern: '.*',
        livePattern: '.*'
    },
    'applovin': {
        placeholder: 'YOUR_ZONE_ID',
        help: 'AppLovin Zone ID',
        testPattern: '.*',
        livePattern: '.*'
    },
    'ironsource': {
        placeholder: 'YOUR_INSTANCE_ID',
        help: 'IronSource Instance ID',
        testPattern: '.*',
        livePattern: '.*'
    },
    'startio': {
        placeholder: 'YOUR_SLOT_ID',
        help: 'Start.io Slot ID',
        testPattern: '.*',
        livePattern: '.*'
    },
    'inhouse': {
        placeholder: 'campaign_123',
        help: 'In-house campaign identifier',
        testPattern: '.*',
        livePattern: '.*'
    }
};

document.getElementById('networkSelect').addEventListener('change', function() {
    const networkCode = this.options[this.selectedIndex]?.dataset.code;
    const adUnitIdInput = document.getElementById('adUnitId');
    const helpText = document.getElementById('adUnitHelp');
    const testSwitch = document.getElementById('testSwitch');
    
    if (networkCode && networkHelp[networkCode]) {
        const help = networkHelp[networkCode];
        adUnitIdInput.placeholder = help.placeholder;
        helpText.textContent = help.help;
        
        // Auto-check test for AdMob if ID looks like test
        if (networkCode === 'admob' && adUnitIdInput.value.includes('3940256099942544')) {
            testSwitch.checked = true;
        }
    } else {
        adUnitIdInput.placeholder = 'Enter Ad Unit ID';
        helpText.textContent = 'Enter the ad unit ID for selected network';
    }
});

function editUnit(unit) {
    document.getElementById('editId').value = unit.id;
    document.getElementById('placementSelect').value = unit.placement_id;
    document.getElementById('networkSelect').value = unit.network_id;
    document.getElementById('adUnitName').value = unit.ad_unit_name || '';
    document.getElementById('adUnitId').value = unit.ad_unit_id;
    document.getElementById('prioritySelect').value = unit.priority;
    
    // Set toggle switches
    document.getElementById('enabledSwitch').checked = unit.enabled == 1;
    document.getElementById('testSwitch').checked = unit.is_test == 1;
    document.getElementById('liveSwitch').checked = unit.is_live == 1;
    document.getElementById('sdkSwitch').checked = unit.sdk_required == 1;
    
    // Update button text
    document.getElementById('submitBtn').innerHTML = '<i class="bi bi-save me-1"></i> Update Ad Unit';
    
    // Scroll to form
    scrollToAddForm();
}

function resetForm() {
    document.getElementById('unitForm').reset();
    document.getElementById('editId').value = 0;
    document.getElementById('submitBtn').innerHTML = '<i class="bi bi-save me-1"></i> Save Ad Unit';
    document.getElementById('adUnitHelp').textContent = 'Enter the ad unit ID for selected network';
    document.getElementById('adUnitId').placeholder = 'Enter Ad Unit ID';
}

function toggleUnit(unitId, enabled) {
    if (confirm(`Are you sure you want to ${enabled ? 'enable' : 'disable'} this ad unit?`)) {
        const form = document.createElement('form');
        form.method = 'POST';
        form.action = 'toggle_unit.php';
        form.style.display = 'none';
        
        const csrfInput = document.createElement('input');
        csrfInput.type = 'hidden';
        csrfInput.name = 'csrf_token';
        csrfInput.value = '<?php echo htmlspecialchars($csrf_token); ?>';
        form.appendChild(csrfInput);
        
        const idInput = document.createElement('input');
        idInput.type = 'hidden';
        idInput.name = 'id';
        idInput.value = unitId;
        form.appendChild(idInput);
        
        document.body.appendChild(form);
        form.submit();
    } else {
        // Reset checkbox if cancelled
        event.target.checked = !enabled;
    }
}

function duplicateUnit(unitId) {
    if (confirm('Create a copy of this ad unit?')) {
        // Show loading indicator
        const btn = event.target;
        const originalHTML = btn.innerHTML;
        btn.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span> Copying...';
        btn.disabled = true;
        
        const form = document.createElement('form');
        form.method = 'POST';
        form.action = 'duplicate_unit.php';
        form.style.display = 'none';
        
        const csrfInput = document.createElement('input');
        csrfInput.type = 'hidden';
        csrfInput.name = 'csrf_token';
        csrfInput.value = '<?php echo htmlspecialchars($csrf_token); ?>';
        form.appendChild(csrfInput);
        
        const idInput = document.createElement('input');
        idInput.type = 'hidden';
        idInput.name = 'id';
        idInput.value = unitId;
        form.appendChild(idInput);
        
        document.body.appendChild(form);
        form.submit();
        
        // Reset button after 3 seconds
        setTimeout(() => {
            btn.innerHTML = originalHTML;
            btn.disabled = false;
        }, 3000);
    }
}

function deleteUnit(unitId, unitName) {
    if (confirm(`Are you sure you want to delete "${unitName}"?\n\nThis action cannot be undone.`)) {
        const form = document.createElement('form');
        form.method = 'POST';
        form.action = 'delete_unit.php';
        form.style.display = 'none';
        
        const csrfInput = document.createElement('input');
        csrfInput.type = 'hidden';
        csrfInput.name = 'csrf_token';
        csrfInput.value = '<?php echo htmlspecialchars($csrf_token); ?>';
        form.appendChild(csrfInput);
        
        const idInput = document.createElement('input');
        idInput.type = 'hidden';
        idInput.name = 'id';
        idInput.value = unitId;
        form.appendChild(idInput);
        
        document.body.appendChild(form);
        form.submit();
    }
}

function exportUnits() {
    const table = document.getElementById('unitsTable');
    const rows = table.querySelectorAll('tr');
    let csv = [];
    
    rows.forEach(row => {
        const rowData = [];
        row.querySelectorAll('th, td').forEach(cell => {
            let text = cell.textContent.replace(/\n/g, ' ').trim();
            text = text.replace(/,/g, ';');
            rowData.push(`"${text}"`);
        });
        csv.push(rowData.join(','));
    });
    
    const csvContent = csv.join('\n');
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const link = document.createElement('a');
    const url = URL.createObjectURL(blob);
    
    link.setAttribute('href', url);
    link.setAttribute('download', `ad-units-export-<?php echo date('Y-m-d'); ?>.csv`);
    link.style.visibility = 'hidden';
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
}

function refreshTable() {
    const btn = event.target;
    const originalText = btn.innerHTML;
    
    btn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span> Refreshing...';
    btn.disabled = true;
    
    setTimeout(() => {
        window.location.reload();
    }, 1000);
}

function scrollToAddForm() {
    document.getElementById('addForm').scrollIntoView({ 
        behavior: 'smooth',
        block: 'start'
    });
}

function testAdUnit() {
    const adUnitId = document.getElementById('adUnitId').value;
    const network = document.getElementById('networkSelect').value;
    
    if (!adUnitId || !network) {
        alert('Please enter Ad Unit ID and select a network first');
        return;
    }
    
    alert(`Testing connection for ${adUnitId}\n\nNote: This is a placeholder. In production, you would:\n1. Validate the ad unit ID format\n2. Test API connection to the network\n3. Verify the ad unit is active and serving ads`);
}

// Form validation
document.getElementById('unitForm').addEventListener('submit', function(e) {
    const adUnitId = document.getElementById('adUnitId').value.trim();
    const network = document.getElementById('networkSelect').value;
    const placement = document.getElementById('placementSelect').value;
    
    if (!adUnitId || !network || !placement) {
        e.preventDefault();
        alert('Please fill in all required fields');
        return false;
    }
    
    // Network-specific validation
    const networkCode = document.getElementById('networkSelect').options[
        document.getElementById('networkSelect').selectedIndex
    ]?.dataset.code;
    
    if (networkCode && networkHelp[networkCode]) {
        const isTest = document.getElementById('testSwitch').checked;
        const pattern = isTest ? networkHelp[networkCode].testPattern : networkHelp[networkCode].livePattern;
        
        if (pattern !== '.*' && !new RegExp(pattern).test(adUnitId)) {
            e.preventDefault();
            alert(`Invalid Ad Unit ID format for ${networkCode} ${isTest ? 'test' : 'live'} mode`);
            return false;
        }
    }
    
    return true;
});

// Sidebar toggle
function toggleSidebar() {
    document.querySelector('.sidebar').classList.toggle('show');
    document.querySelector('.sidebar-overlay').classList.toggle('show');
}
</script>



<style>
/* Fix for button icons */
.btn-group-sm .btn {
    padding: 0.25rem 0.5rem;
    font-size: 0.875rem;
    display: inline-flex;
    align-items: center;
    gap: 4px;
}

.btn-outline-primary {
    border-color: #4361ee;
    color: #4361ee;
}

.btn-outline-primary:hover {
    background-color: #4361ee;
    color: white;
}

.btn-outline-info {
    border-color: #17a2b8;
    color: #17a2b8;
}

.btn-outline-info:hover {
    background-color: #17a2b8;
    color: white;
}

.btn-outline-danger {
    border-color: #dc3545;
    color: #dc3545;
}

.btn-outline-danger:hover {
    background-color: #dc3545;
    color: white;
}

/* Ensure icons are visible */
.bi {
    font-size: 0.875rem;
    line-height: 1;
    vertical-align: middle;
}

/* Button group spacing */
.btn-group {
    gap: 2px;
}

/* Table styling */
.table td {
    vertical-align: middle;
}

/* Ensure proper icon display */
.bi::before {
    display: inline-block;
    font-family: "Bootstrap Icons" !important;
    font-style: normal;
    font-weight: normal !important;
    line-height: 1;
}
</style>

<!-- Bootstrap Icons CDN -->
<link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.10.0/font/bootstrap-icons.css">

<!-- Bootstrap CSS -->
<link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">

<?php include '../../includes/footer.php'; ?>