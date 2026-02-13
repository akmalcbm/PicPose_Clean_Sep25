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

$csrf_token = $_SESSION['csrf_token'];

// Fetch current global settings with error handling
try {
    $global_stmt = $conn->prepare("SELECT * FROM ads_global_settings WHERE id = 1");
    $global_stmt->execute();
    $global_result = $global_stmt->get_result();
    $global = $global_result->fetch_assoc();
    $global_stmt->close();
    
    if (!$global) {
        // Initialize default settings
        $init_stmt = $conn->prepare("
            INSERT INTO ads_global_settings 
                (ads_enabled, environment, cmp_required, default_frequency_per_hour, config_version)
            VALUES (1, 'development', 0, 3, 1)
        ");
        $init_stmt->execute();
        $init_stmt->close();
        
        $global = [
            'ads_enabled' => 1,
            'environment' => 'development',
            'cmp_required' => 0,
            'default_frequency_per_hour' => 3,
            'config_version' => 1,
            'updated_at' => date('Y-m-d H:i:s')
        ];
    }
} catch (Exception $e) {
    error_log("Global Settings Error: " . $e->getMessage());
    $_SESSION['error'] = "Unable to load settings. Please try again.";
    header("Location: index.php");
    exit();
}

// Fetch statistics for sidebar
try {
    $stats_stmt = $conn->prepare("
        SELECT 
            COUNT(DISTINCT p.id) as total_placements,
            COUNT(DISTINCT u.id) as total_units,
            SUM(CASE WHEN u.is_test = 1 THEN 1 ELSE 0 END) as test_units,
            SUM(CASE WHEN u.is_live = 1 THEN 1 ELSE 0 END) as live_units,
            COUNT(DISTINCT n.id) as total_networks,
            SUM(CASE WHEN n.enabled = 1 THEN 1 ELSE 0 END) as active_networks
        FROM ad_placements p
        LEFT JOIN ad_network_units u ON u.placement_id = p.id AND u.enabled = 1
        LEFT JOIN ad_networks n ON u.network_id = n.id
        WHERE p.enabled = 1
    ");
    $stats_stmt->execute();
    $stats_result = $stats_stmt->get_result();
    $stats = $stats_result->fetch_assoc();
    $stats_stmt->close();
} catch (Exception $e) {
    error_log("Stats Query Error: " . $e->getMessage());
    $stats = [
        'total_placements' => 0,
        'total_units' => 0,
        'test_units' => 0,
        'live_units' => 0,
        'total_networks' => 0,
        'active_networks' => 0
    ];
}

include '../../includes/header.php';
?>

<div class="container-fluid">
    <!-- Page Header -->
    <div class="page-header mb-4">
        <div class="d-flex justify-content-between align-items-center">
            <div class="d-flex align-items-center gap-3">
                <div class="icon-wrapper bg-primary">
                    <i class="bi bi-gear text-white"></i>
                </div>
                <div>
                    <h1 class="mb-1">Global Ads Settings</h1>
                    <nav aria-label="breadcrumb">
                        <ol class="breadcrumb mb-0">
                            <li class="breadcrumb-item"><a href="../dashboard.php">Dashboard</a></li>
                            <li class="breadcrumb-item"><a href="index.php">Ads Management</a></li>
                            <li class="breadcrumb-item active">Global Settings</li>
                        </ol>
                    </nav>
                </div>
            </div>
            
            <div class="btn-group">
                <a href="index.php" class="btn btn-outline-secondary">
                    <i class="bi bi-arrow-left me-1"></i> Back
                </a>
                <button type="button" class="btn btn-primary" onclick="document.getElementById('settingsForm').submit()">
                    <i class="bi bi-save me-1"></i> Save Changes
                </button>
            </div>
        </div>
    </div>

    <div class="row">
        <!-- Main Settings Form -->
        <div class="col-lg-8">
            <div class="card">
                <div class="card-header bg-light">
                    <h5 class="mb-0">
                        <i class="bi bi-sliders me-2"></i>
                        Global Configuration
                    </h5>
                </div>
                <div class="card-body">
                    <form action="save_global_settings.php" method="POST" id="settingsForm">
                        <input type="hidden" name="csrf_token" value="<?php echo htmlspecialchars($csrf_token); ?>">
                        
                        <!-- Master Toggle -->
                        <div class="mb-4">
                            <div class="card border-<?php echo $global['ads_enabled'] ? 'success' : 'danger'; ?>">
                                <div class="card-body">
                                    <div class="form-check form-switch">
                                        <input class="form-check-input" type="checkbox" name="ads_enabled" 
                                               value="1" id="adsEnabled" 
                                               <?php echo $global['ads_enabled'] ? 'checked' : ''; ?> 
                                               onchange="toggleAdsStatus(this.checked)">
                                        <label class="form-check-label" for="adsEnabled">
                                            <h5 class="mb-1">
                                                <?php echo $global['ads_enabled'] ? '✅ Ads are ENABLED' : '❌ Ads are DISABLED'; ?>
                                            </h5>
                                            <p class="text-muted mb-0">
                                                Master switch for all advertisements in the app
                                            </p>
                                        </label>
                                    </div>
                                </div>
                            </div>
                        </div>

                        <div class="row g-3">
                            <!-- Environment Settings -->
                            <div class="col-md-6">
                                <div class="card h-100">
                                    <div class="card-header">
                                        <h6 class="mb-0">Environment</h6>
                                    </div>
                                    <div class="card-body">
                                        <select name="environment" class="form-select" id="environmentSelect">
                                            <option value="development" <?php echo $global['environment'] == 'development' ? 'selected' : ''; ?>>
                                                🧪 Development
                                            </option>
                                            <option value="staging" <?php echo $global['environment'] == 'staging' ? 'selected' : ''; ?>>
                                                ⚡ Staging
                                            </option>
                                            <option value="production" <?php echo $global['environment'] == 'production' ? 'selected' : ''; ?>>
                                                🚀 Production
                                            </option>
                                        </select>
                                        <div class="form-text mt-2">
                                            <ul class="small mb-0">
                                                <li><strong>Development:</strong> Test ads only</li>
                                                <li><strong>Staging:</strong> Mix of test and live ads</li>
                                                <li><strong>Production:</strong> Live ads only</li>
                                            </ul>
                                        </div>
                                    </div>
                                </div>
                            </div>

                            <!-- Frequency Settings -->
                            <div class="col-md-6">
                                <div class="card h-100">
                                    <div class="card-header">
                                        <h6 class="mb-0">Frequency Control</h6>
                                    </div>
                                    <div class="card-body">
                                        <div class="mb-3">
                                            <label class="form-label">Default Ads Per Hour</label>
                                            <input type="number" name="default_frequency_per_hour" 
                                                   class="form-control" min="0" max="20" step="1"
                                                   value="<?php echo htmlspecialchars($global['default_frequency_per_hour']); ?>">
                                            <div class="form-text">
                                                Maximum ads shown per hour per user (0 = no limit)
                                            </div>
                                        </div>
                                        <div class="alert alert-info py-2">
                                            <small>
                                                <i class="bi bi-info-circle me-1"></i>
                                                This can be overridden per placement
                                            </small>
                                        </div>
                                    </div>
                                </div>
                            </div>

                            <!-- Compliance Settings -->
                            <div class="col-12">
                                <div class="card">
                                    <div class="card-header">
                                        <h6 class="mb-0">Compliance & Privacy</h6>
                                    </div>
                                    <div class="card-body">
                                        <div class="form-check mb-3">
                                            <input class="form-check-input" type="checkbox" name="cmp_required" 
                                                   value="1" id="cmpRequired"
                                                   <?php echo $global['cmp_required'] ? 'checked' : ''; ?>>
                                            <label class="form-check-label" for="cmpRequired">
                                                <strong>Require CMP (Consent Management Platform)</strong>
                                            </label>
                                            <div class="form-text">
                                                Enables GDPR/CCPA compliance. When enabled, ads will wait for user consent.
                                            </div>
                                        </div>
                                        
                                        <div class="alert alert-warning">
                                            <h6><i class="bi bi-exclamation-triangle me-2"></i>Important</h6>
                                            <p class="small mb-0">
                                                Enabling CMP is required for compliance in EU, UK, California, and other regions with privacy laws.
                                                Make sure to configure your CMP provider (Google UMP, OneTrust, etc.) in the app code.
                                            </p>
                                        </div>
                                    </div>
                                </div>
                            </div>

                            <!-- Configuration Version -->
                            <div class="col-12">
                                <div class="card">
                                    <div class="card-header">
                                        <h6 class="mb-0">Configuration Management</h6>
                                    </div>
                                    <div class="card-body">
                                        <div class="row align-items-center">
                                            <div class="col-md-8">
                                                <h5 class="mb-2">
                                                    Current Config Version: 
                                                    <span class="badge bg-primary fs-6">v<?php echo $global['config_version']; ?></span>
                                                </h5>
                                                <p class="text-muted mb-0">
                                                    The app checks this version number to know when to reload ad configuration.
                                                    Increment when you make changes that should be immediately reflected in the app.
                                                </p>
                                            </div>
                                            <div class="col-md-4 text-end">
                                                <button type="button" class="btn btn-outline-primary" 
                                                        onclick="incrementVersion()">
                                                    <i class="bi bi-arrow-clockwise me-1"></i>
                                                    Force Config Refresh
                                                </button>
                                            </div>
                                        </div>
                                        
                                        <hr class="my-3">
                                        
                                        <div class="row">
                                            <div class="col-md-6">
                                                <div class="alert alert-light">
                                                    <small>
                                                        <i class="bi bi-clock-history me-1"></i>
                                                        <strong>Last Updated:</strong><br>
                                                        <?php echo date('F j, Y H:i', strtotime($global['updated_at'])); ?>
                                                    </small>
                                                </div>
                                            </div>
                                            <div class="col-md-6">
                                                <div class="alert alert-light">
                                                    <small>
                                                        <i class="bi bi-phone me-1"></i>
                                                        <strong>App Refresh:</strong><br>
                                                        Apps check for updates every 6 hours
                                                    </small>
                                                </div>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>

                        <!-- Action Buttons -->
                        <div class="mt-4">
                            <div class="d-flex justify-content-between">
                                <a href="index.php" class="btn btn-secondary">
                                    <i class="bi bi-x-circle me-1"></i> Cancel
                                </a>
                                <div class="btn-group">
                                    <button type="submit" class="btn btn-primary">
                                        <i class="bi bi-save me-1"></i> Save Settings
                                    </button>
                                    <button type="button" class="btn btn-success" onclick="saveAndClose()">
                                        <i class="bi bi-check-circle me-1"></i> Save & Close
                                    </button>
                                </div>
                            </div>
                        </div>
                    </form>
                </div>
            </div>
        </div>

        <!-- Sidebar Stats -->
        <div class="col-lg-4">
            <!-- Quick Stats -->
            <div class="card mb-3">
                <div class="card-header bg-light">
                    <h6 class="mb-0">
                        <i class="bi bi-speedometer2 me-2"></i>
                        Quick Stats
                    </h6>
                </div>
                <div class="card-body">
                    <div class="list-group list-group-flush">
                        <div class="list-group-item d-flex justify-content-between align-items-center px-0">
                            <span>Active Placements</span>
                            <span class="badge bg-primary"><?php echo $stats['total_placements']; ?></span>
                        </div>
                        <div class="list-group-item d-flex justify-content-between align-items-center px-0">
                            <span>Total Ad Units</span>
                            <span class="badge bg-info"><?php echo $stats['total_units']; ?></span>
                        </div>
                        <div class="list-group-item d-flex justify-content-between align-items-center px-0">
                            <span>Test Units</span>
                            <span class="badge bg-warning text-dark"><?php echo $stats['test_units']; ?></span>
                        </div>
                        <div class="list-group-item d-flex justify-content-between align-items-center px-0">
                            <span>Live Units</span>
                            <span class="badge bg-success"><?php echo $stats['live_units']; ?></span>
                        </div>
                        <div class="list-group-item d-flex justify-content-between align-items-center px-0">
                            <span>Active Networks</span>
                            <span class="badge bg-secondary"><?php echo $stats['active_networks']; ?>/<?php echo $stats['total_networks']; ?></span>
                        </div>
                    </div>
                </div>
            </div>

            <!-- Environment Guide -->
            <div class="card mb-3">
                <div class="card-header bg-light">
                    <h6 class="mb-0">
                        <i class="bi bi-info-circle me-2"></i>
                        Environment Guide
                    </h6>
                </div>
                <div class="card-body">
                    <div class="accordion" id="envGuide">
                        <div class="accordion-item">
                            <h2 class="accordion-header">
                                <button class="accordion-button collapsed" type="button" 
                                        data-bs-toggle="collapse" data-bs-target="#envDev">
                                    🧪 Development
                                </button>
                            </h2>
                            <div id="envDev" class="accordion-collapse collapse" data-bs-parent="#envGuide">
                                <div class="accordion-body">
                                    <small>
                                        <ul class="mb-0">
                                            <li>Test ad units only</li>
                                            <li>No revenue generated</li>
                                            <li>Safe for testing new placements</li>
                                            <li>Ideal for development & QA</li>
                                        </ul>
                                    </small>
                                </div>
                            </div>
                        </div>
                        <div class="accordion-item">
                            <h2 class="accordion-header">
                                <button class="accordion-button collapsed" type="button" 
                                        data-bs-toggle="collapse" data-bs-target="#envStaging">
                                    ⚡ Staging
                                </button>
                            </h2>
                            <div id="envStaging" class="accordion-collapse collapse" data-bs-parent="#envGuide">
                                <div class="accordion-body">
                                    <small>
                                        <ul class="mb-0">
                                            <li>Mix of test and live ads</li>
                                            <li>Partial revenue generation</li>
                                            <li>Good for pre-production testing</li>
                                            <li>Monitor performance before production</li>
                                        </ul>
                                    </small>
                                </div>
                            </div>
                        </div>
                        <div class="accordion-item">
                            <h2 class="accordion-header">
                                <button class="accordion-button collapsed" type="button" 
                                        data-bs-toggle="collapse" data-bs-target="#envProd">
                                    🚀 Production
                                </button>
                            </h2>
                            <div id="envProd" class="accordion-collapse collapse" data-bs-parent="#envGuide">
                                <div class="accordion-body">
                                    <small>
                                        <ul class="mb-0">
                                            <li>Live ad units only</li>
                                            <li>Full revenue generation</li>
                                            <li>Real user traffic</li>
                                            <li>Production-ready configuration</li>
                                        </ul>
                                    </small>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>

            <!-- Quick Actions -->
            <div class="card">
                <div class="card-header bg-light">
                    <h6 class="mb-0">
                        <i class="bi bi-lightning-charge me-2"></i>
                        Quick Actions
                    </h6>
                </div>
                <div class="card-body">
                    <div class="d-grid gap-2">
                        <a href="placements.php" class="btn btn-outline-primary">
                            <i class="bi bi-pin-map me-2"></i> Manage Placements
                        </a>
                        <a href="units.php" class="btn btn-outline-success">
                            <i class="bi bi-puzzle me-2"></i> Edit Ad Units
                        </a>
                        <a href="networks.php" class="btn btn-outline-info">
                            <i class="bi bi-diagram-3 me-2"></i> Configure Networks
                        </a>
                        <a href="analytics.php" class="btn btn-outline-warning">
                            <i class="bi bi-graph-up me-2"></i> View Analytics
                        </a>
                    </div>
                </div>
            </div>
        </div>
    </div>
</div>

<script>
function toggleAdsStatus(enabled) {
    const statusCard = document.querySelector('.card.border-success, .card.border-danger');
    const statusLabel = document.querySelector('.form-check-label h5');
    
    if (enabled) {
        statusCard.classList.remove('border-danger');
        statusCard.classList.add('border-success');
        statusLabel.innerHTML = '✅ Ads are ENABLED';
    } else {
        statusCard.classList.remove('border-success');
        statusCard.classList.add('border-danger');
        statusLabel.innerHTML = '❌ Ads are DISABLED';
    }
}

function incrementVersion() {
    if (confirm('This will force all app instances to reload ad configuration.\n\nAre you sure you want to increment the config version?')) {
        const form = document.createElement('form');
        form.method = 'POST';
        form.action = 'increment_version.php';
        form.style.display = 'none';
        
        const csrfInput = document.createElement('input');
        csrfInput.type = 'hidden';
        csrfInput.name = 'csrf_token';
        csrfInput.value = '<?php echo htmlspecialchars($csrf_token); ?>';
        form.appendChild(csrfInput);
        
        document.body.appendChild(form);
        form.submit();
    }
}

function saveAndClose() {
    document.getElementById('settingsForm').submit();
    setTimeout(() => {
        window.location.href = 'index.php';
    }, 1000);
}

// Environment change warning
document.getElementById('environmentSelect').addEventListener('change', function() {
    const env = this.value;
    let message = '';
    
    switch(env) {
        case 'production':
            message = 'Switching to PRODUCTION environment will enable live ads and generate real revenue. Make sure all ad units are properly configured.';
            break;
        case 'staging':
            message = 'Switching to STAGING environment will use a mix of test and live ads. Good for pre-production testing.';
            break;
        case 'development':
            message = 'Switching to DEVELOPMENT environment will use test ads only. No revenue will be generated.';
            break;
    }
    
    if (message && !confirm(message + '\n\nContinue?')) {
        this.value = '<?php echo $global["environment"]; ?>';
    }
});

// Sidebar toggle
function toggleSidebar() {
    document.querySelector('.sidebar').classList.toggle('show');
    document.querySelector('.sidebar-overlay').classList.toggle('show');
}
</script>

<?php include '../../includes/footer.php'; ?>