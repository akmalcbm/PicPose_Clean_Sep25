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

// Fetch networks with error handling
try {
    $networks_stmt = $conn->prepare("
        SELECT n.*, 
               COUNT(u.id) as total_units,
               SUM(CASE WHEN u.enabled = 1 THEN 1 ELSE 0 END) as active_units,
               SUM(CASE WHEN u.is_live = 1 THEN 1 ELSE 0 END) as live_units
        FROM ad_networks n
        LEFT JOIN ad_network_units u ON n.id = u.network_id
        GROUP BY n.id
        ORDER BY n.enabled DESC, n.display_name ASC
    ");
    $networks_stmt->execute();
    $networks_result = $networks_stmt->get_result();
} catch (Exception $e) {
    error_log("Networks Query Error: " . $e->getMessage());
    $networks_result = null;
}

// Fetch network icons
$network_icons = [
    'admob' => 'google',
    'facebook' => 'facebook',
    'fan' => 'facebook',
    'unity' => 'unity',
    'applovin' => 'applovin',
    'ironsource' => 'ironsource',
    'startio' => 'startio',
    'inhouse' => 'house'
];

// Process form submission
$form_data = [];
$form_errors = [];

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    // CSRF validation
    if (!isset($_POST['csrf_token']) || $_POST['csrf_token'] !== $csrf_token) {
        $form_errors[] = "CSRF token validation failed";
    }
    
    // Validate inputs
    $code = trim($_POST['code'] ?? '');
    $display_name = trim($_POST['display_name'] ?? '');
    $icon = trim($_POST['icon'] ?? '');
    $sdk_required = isset($_POST['sdk_required']) ? 1 : 0;
    
    if (empty($code)) {
        $form_errors[] = "Network code is required";
    }
    
    if (empty($display_name)) {
        $form_errors[] = "Display name is required";
    }
    
    // Check if code already exists
    if (empty($form_errors)) {
        try {
            $check_stmt = $conn->prepare("SELECT id FROM ad_networks WHERE code = ?");
            $check_stmt->bind_param("s", $code);
            $check_stmt->execute();
            $check_result = $check_stmt->get_result();
            
            if ($check_result->num_rows > 0) {
                $form_errors[] = "Network code already exists";
            }
            $check_stmt->close();
        } catch (Exception $e) {
            $form_errors[] = "Error checking network code: " . $e->getMessage();
        }
    }
    
    // Insert if no errors
    if (empty($form_errors)) {
        try {
            $insert_stmt = $conn->prepare("
                INSERT INTO ad_networks (code, display_name, icon, sdk_required, enabled)
                VALUES (?, ?, ?, ?, 1)
            ");
            $insert_stmt->bind_param("sssi", $code, $display_name, $icon, $sdk_required);
            
            if ($insert_stmt->execute()) {
                // Increment config version
                $conn->query("UPDATE ads_global_settings SET config_version = config_version + 1 WHERE id = 1");
                
                // Log the action
                logActivity("network_created", "Network: $display_name ($code)");
                
                $_SESSION['success'] = "Network added successfully";
                header("Location: networks.php");
                exit();
            } else {
                $form_errors[] = "Failed to add network: " . $conn->error;
            }
            $insert_stmt->close();
        } catch (Exception $e) {
            $form_errors[] = "Error adding network: " . $e->getMessage();
        }
    }
    
    // Store form data for repopulation
    $form_data = [
        'code' => $code,
        'display_name' => $display_name,
        'icon' => $icon,
        'sdk_required' => $sdk_required
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
                    <i class="bi bi-diagram-3 text-white"></i>
                </div>
                <div>
                    <h1 class="mb-1">Ad Networks Management</h1>
                    <nav aria-label="breadcrumb">
                        <ol class="breadcrumb mb-0">
                            <li class="breadcrumb-item"><a href="../dashboard.php">Dashboard</a></li>
                            <li class="breadcrumb-item"><a href="index.php">Ads Management</a></li>
                            <li class="breadcrumb-item active">Networks</li>
                        </ol>
                    </nav>
                </div>
            </div>
            
            <div class="btn-group">
                <a href="index.php" class="btn btn-outline-secondary">
                    <i class="bi bi-arrow-left me-1"></i> Back
                </a>
                <button type="button" class="btn btn-primary" data-bs-toggle="modal" data-bs-target="#addNetworkModal">
                    <i class="bi bi-plus-circle me-1"></i> Add Network
                </button>
            </div>
        </div>
    </div>

    <!-- Error Messages -->
    <?php if (!empty($form_errors)): ?>
        <div class="alert alert-danger mb-4">
            <h5><i class="bi bi-exclamation-triangle me-2"></i> Please fix the following errors:</h5>
            <ul class="mb-0">
                <?php foreach ($form_errors as $error): ?>
                    <li><?php echo htmlspecialchars($error); ?></li>
                <?php endforeach; ?>
            </ul>
        </div>
    <?php endif; ?>

    <!-- Success Message -->
    <?php if (isset($_SESSION['success'])): ?>
        <div class="alert alert-success alert-dismissible fade show mb-4" role="alert">
            <i class="bi bi-check-circle me-2"></i>
            <?php echo htmlspecialchars($_SESSION['success']); ?>
            <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
        </div>
        <?php unset($_SESSION['success']); ?>
    <?php endif; ?>

    <!-- Networks Grid -->
    <div class="row g-3">
        <?php if ($networks_result && $networks_result->num_rows > 0): ?>
            <?php while($network = $networks_result->fetch_assoc()): 
                $icon_class = $network_icons[strtolower($network['code'])] ?? 'question-circle';
                $is_active = $network['enabled'] == 1;
                $active_units = $network['active_units'] ?? 0;
                $live_units = $network['live_units'] ?? 0;
            ?>
                <div class="col-md-4 col-lg-3">
                    <div class="card network-card h-100 <?php echo $is_active ? 'border-success' : 'border-secondary'; ?>">
                        <div class="card-header <?php echo $is_active ? 'bg-success text-white' : 'bg-secondary text-white'; ?>">
                            <div class="d-flex justify-content-between align-items-center">
                                <h6 class="mb-0">
                                    <i class="bi bi-<?php echo $icon_class; ?> me-2"></i>
                                    <?php echo htmlspecialchars($network['code']); ?>
                                </h6>
                                <div class="form-check form-switch">
                                    <input class="form-check-input" type="checkbox" 
                                           <?php echo $is_active ? 'checked' : ''; ?>
                                           onchange="toggleNetwork(<?php echo $network['id']; ?>, this.checked)">
                                </div>
                            </div>
                        </div>
                        <div class="card-body">
                            <h5 class="card-title"><?php echo htmlspecialchars($network['display_name']); ?></h5>
                            
                            <div class="mb-3">
                                <span class="badge bg-light text-dark">
                                    <i class="bi bi-<?php echo $network['sdk_required'] ? 'check' : 'x'; ?>-circle me-1"></i>
                                    SDK <?php echo $network['sdk_required'] ? 'Required' : 'Not Required'; ?>
                                </span>
                            </div>
                            
                            <div class="network-stats">
                                <div class="d-flex justify-content-between mb-2">
                                    <span>Total Units:</span>
                                    <span class="badge bg-secondary"><?php echo $network['total_units']; ?></span>
                                </div>
                                <div class="d-flex justify-content-between mb-2">
                                    <span>Active Units:</span>
                                    <span class="badge bg-success"><?php echo $active_units; ?></span>
                                </div>
                                <div class="d-flex justify-content-between">
                                    <span>Live Units:</span>
                                    <span class="badge bg-info"><?php echo $live_units; ?></span>
                                </div>
                            </div>
                            
                            <?php if (!empty($network['icon'])): ?>
                                <div class="mt-3">
                                    <small class="text-muted">
                                        <i class="bi bi-tag me-1"></i>
                                        Icon: <?php echo htmlspecialchars($network['icon']); ?>
                                    </small>
                                </div>
                            <?php endif; ?>
                        </div>
                        <div class="card-footer bg-transparent">
                            <div class="d-flex justify-content-between">
                                <small class="text-muted">
                                    Created: <?php echo date('M j, Y', strtotime($network['created_at'])); ?>
                                </small>
                                <div class="btn-group btn-group-sm">
                                    <button type="button" class="btn btn-outline-primary" 
                                            onclick="editNetwork(<?php echo htmlspecialchars(json_encode($network)); ?>)">
                                        <i class="bi bi-pencil"></i>
                                    </button>
                                    <button type="button" class="btn btn-outline-danger"
                                            onclick="deleteNetwork(<?php echo $network['id']; ?>, '<?php echo htmlspecialchars($network['display_name']); ?>')">
                                        <i class="bi bi-trash"></i>
                                    </button>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            <?php endwhile; ?>
        <?php else: ?>
            <div class="col-12">
                <div class="card">
                    <div class="card-body text-center py-5">
                        <i class="bi bi-diagram-3 display-1 text-muted"></i>
                        <h4 class="mt-3 text-muted">No Networks Found</h4>
                        <p class="text-muted">Add your first ad network to get started</p>
                        <button type="button" class="btn btn-primary" data-bs-toggle="modal" data-bs-target="#addNetworkModal">
                            <i class="bi bi-plus-circle me-1"></i> Add Network
                        </button>
                    </div>
                </div>
            </div>
        <?php endif; ?>
    </div>
</div>

<!-- Add Network Modal -->
<div class="modal fade" id="addNetworkModal" tabindex="-1" aria-labelledby="addNetworkModalLabel" aria-hidden="true">
    <div class="modal-dialog modal-dialog-centered">
        <div class="modal-content">
            <div class="modal-header">
                <h5 class="modal-title" id="addNetworkModalLabel">
                    <i class="bi bi-plus-circle me-2"></i>
                    Add New Network
                </h5>
                <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
            </div>
            <form method="POST" action="" id="addNetworkForm">
                <div class="modal-body">
                    <input type="hidden" name="csrf_token" value="<?php echo htmlspecialchars($csrf_token); ?>">
                    
                    <div class="mb-3">
                        <label for="code" class="form-label">Network Code *</label>
                        <input type="text" class="form-control" id="code" name="code" 
                               value="<?php echo htmlspecialchars($form_data['code'] ?? ''); ?>" 
                               required placeholder="e.g., admob">
                        <div class="form-text">
                            Unique identifier (lowercase, no spaces). Used in API calls.
                        </div>
                    </div>
                    
                    <div class="mb-3">
                        <label for="display_name" class="form-label">Display Name *</label>
                        <input type="text" class="form-control" id="display_name" name="display_name" 
                               value="<?php echo htmlspecialchars($form_data['display_name'] ?? ''); ?>" 
                               required placeholder="e.g., Google AdMob">
                        <div class="form-text">
                            Human-readable name shown in dashboard
                        </div>
                    </div>
                    
                    <div class="mb-3">
                        <label for="icon" class="form-label">Icon Name</label>
                        <input type="text" class="form-control" id="icon" name="icon" 
                               value="<?php echo htmlspecialchars($form_data['icon'] ?? ''); ?>" 
                               placeholder="e.g., google, facebook">
                        <div class="form-text">
                            Icon class for UI display (optional)
                        </div>
                    </div>
                    
                    <div class="mb-3">
                        <div class="form-check">
                            <input class="form-check-input" type="checkbox" id="sdk_required" name="sdk_required" 
                                   value="1" <?php echo isset($form_data['sdk_required']) && $form_data['sdk_required'] ? 'checked' : 'checked'; ?>>
                            <label class="form-check-label" for="sdk_required">
                                <strong>SDK Required</strong>
                            </label>
                            <div class="form-text">
                                Check if this network requires SDK integration in the app
                            </div>
                        </div>
                    </div>
                </div>
                <div class="modal-footer">
                    <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Cancel</button>
                    <button type="submit" class="btn btn-primary">Add Network</button>
                </div>
            </form>
        </div>
    </div>
</div>

<!-- Edit Network Modal -->
<div class="modal fade" id="editNetworkModal" tabindex="-1" aria-labelledby="editNetworkModalLabel" aria-hidden="true">
    <div class="modal-dialog modal-dialog-centered">
        <div class="modal-content">
            <div class="modal-header">
                <h5 class="modal-title" id="editNetworkModalLabel">
                    <i class="bi bi-pencil me-2"></i>
                    Edit Network
                </h5>
                <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
            </div>
            <form method="POST" action="update_network.php" id="editNetworkForm">
                <div class="modal-body">
                    <input type="hidden" name="csrf_token" value="<?php echo htmlspecialchars($csrf_token); ?>">
                    <input type="hidden" id="edit_network_id" name="id" value="">
                    
                    <div class="mb-3">
                        <label for="edit_code" class="form-label">Network Code</label>
                        <input type="text" class="form-control" id="edit_code" name="code" readonly>
                        <div class="form-text">
                            Network code cannot be changed
                        </div>
                    </div>
                    
                    <div class="mb-3">
                        <label for="edit_display_name" class="form-label">Display Name *</label>
                        <input type="text" class="form-control" id="edit_display_name" name="display_name" required>
                    </div>
                    
                    <div class="mb-3">
                        <label for="edit_icon" class="form-label">Icon Name</label>
                        <input type="text" class="form-control" id="edit_icon" name="icon" placeholder="e.g., google, facebook">
                    </div>
                    
                    <div class="row g-3">
                        <div class="col-md-6">
                            <div class="mb-3">
                                <div class="form-check">
                                    <input class="form-check-input" type="checkbox" id="edit_sdk_required" name="sdk_required" value="1">
                                    <label class="form-check-label" for="edit_sdk_required">
                                        SDK Required
                                    </label>
                                </div>
                            </div>
                        </div>
                        <div class="col-md-6">
                            <div class="mb-3">
                                <div class="form-check">
                                    <input class="form-check-input" type="checkbox" id="edit_enabled" name="enabled" value="1">
                                    <label class="form-check-label" for="edit_enabled">
                                        Enabled
                                    </label>
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
function toggleNetwork(networkId, enabled) {
    if (confirm(`Are you sure you want to ${enabled ? 'enable' : 'disable'} this network?`)) {
        const form = document.createElement('form');
        form.method = 'POST';
        form.action = 'toggle_network.php';
        form.style.display = 'none';
        
        const csrfInput = document.createElement('input');
        csrfInput.type = 'hidden';
        csrfInput.name = 'csrf_token';
        csrfInput.value = '<?php echo htmlspecialchars($csrf_token); ?>';
        form.appendChild(csrfInput);
        
        const idInput = document.createElement('input');
        idInput.type = 'hidden';
        idInput.name = 'id';
        idInput.value = networkId;
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

function editNetwork(network) {
    document.getElementById('edit_network_id').value = network.id;
    document.getElementById('edit_code').value = network.code;
    document.getElementById('edit_display_name').value = network.display_name;
    document.getElementById('edit_icon').value = network.icon || '';
    document.getElementById('edit_sdk_required').checked = network.sdk_required == 1;
    document.getElementById('edit_enabled').checked = network.enabled == 1;
    
    const modal = new bootstrap.Modal(document.getElementById('editNetworkModal'));
    modal.show();
}

function deleteNetwork(networkId, networkName) {
    if (confirm(`Are you sure you want to delete "${networkName}"?\n\nThis action cannot be undone and will remove all associated ad units.`)) {
        const form = document.createElement('form');
        form.method = 'POST';
        form.action = 'delete_network.php';
        form.style.display = 'none';
        
        const csrfInput = document.createElement('input');
        csrfInput.type = 'hidden';
        csrfInput.name = 'csrf_token';
        csrfInput.value = '<?php echo htmlspecialchars($csrf_token); ?>';
        form.appendChild(csrfInput);
        
        const idInput = document.createElement('input');
        idInput.type = 'hidden';
        idInput.name = 'id';
        idInput.value = networkId;
        form.appendChild(idInput);
        
        document.body.appendChild(form);
        form.submit();
    }
}

// Form validation
document.getElementById('addNetworkForm').addEventListener('submit', function(e) {
    const code = document.getElementById('code').value.trim();
    const name = document.getElementById('display_name').value.trim();
    
    if (!code || !name) {
        e.preventDefault();
        alert('Please fill in all required fields');
        return false;
    }
    
    // Validate code format
    if (!/^[a-z0-9_]+$/.test(code)) {
        e.preventDefault();
        alert('Network code must contain only lowercase letters, numbers, and underscores');
        return false;
    }
});

// Auto-suggest icon based on code
document.getElementById('code').addEventListener('input', function() {
    const code = this.value.toLowerCase();
    const iconField = document.getElementById('icon');
    
    const iconSuggestions = {
        'admob': 'google',
        'google': 'google',
        'facebook': 'facebook',
        'fan': 'facebook',
        'unity': 'unity',
        'applovin': 'applovin',
        'ironsource': 'ironsource',
        'startio': 'startio',
        'inhouse': 'house'
    };
    
    if (iconSuggestions[code] && !iconField.value) {
        iconField.value = iconSuggestions[code];
    }
});

// Sidebar toggle
function toggleSidebar() {
    document.querySelector('.sidebar').classList.toggle('show');
    document.querySelector('.sidebar-overlay').classList.toggle('show');
}
</script>

<style>
.network-card {
    transition: all 0.3s ease;
    border-width: 2px;
}

.network-card:hover {
    transform: translateY(-5px);
    box-shadow: 0 10px 20px rgba(0,0,0,0.1);
}

.network-stats {
    background: #f8f9fa;
    padding: 15px;
    border-radius: 8px;
    margin: 15px 0;
}

.network-stats div {
    font-size: 0.9rem;
}

.icon-wrapper-lg {
    width: 60px;
    height: 60px;
    border-radius: 12px;
    display: flex;
    align-items: center;
    justify-content: center;
    margin: 0 auto;
}

.icon-wrapper-lg i {
    font-size: 24px;
}
</style>

<?php include '../../includes/footer.php'; ?>