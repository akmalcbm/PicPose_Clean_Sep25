<?php
session_start();
require '../../config.php';
require_once '../../app/helpers/ads_config_helper.php';

if (!isset($_SESSION['admin']) || empty($_SESSION['admin'])) {
    header("Location: " . BASE_URL . "/login.php");
    exit();
}

ensure_ads_config_schema($conn);

$global = [
    'ads_enabled' => 0,
    'environment' => 'test',
    'use_test_ads' => 1,
    'config_version' => 1,
    'updated_at' => date('Y-m-d H:i:s'),
    'admob_app_id_test' => '',
    'admob_app_id_live' => '',
    'interstitial_cooldown_seconds' => 60,
    'interstitial_show_every_n_actions' => 3,
];

$gStmt = $conn->prepare('SELECT * FROM ads_global_settings WHERE id = 1 LIMIT 1');
if ($gStmt && $gStmt->execute()) {
    $row = $gStmt->get_result()->fetch_assoc();
    if ($row) {
        $global = array_merge($global, $row);
    }
    $gStmt->close();
}

$counts = [
    'placements' => 0,
    'active_placements' => 0
];
$countResult = $conn->query("SELECT COUNT(*) AS placements, SUM(CASE WHEN enabled = 1 THEN 1 ELSE 0 END) AS active_placements FROM ads_placement_settings");
if ($countResult) {
    $counts = array_merge($counts, $countResult->fetch_assoc() ?: []);
}

$env = normalize_ads_env((string)$global['environment']);
$useTestAds = ((int)$global['use_test_ads'] === 1);
$selectedAppId = ($env === 'live' && !$useTestAds)
    ? (string)($global['admob_app_id_live'] ?? '')
    : (string)($global['admob_app_id_test'] ?? '');

$preview = [
    'success' => true,
    'data' => [
        'ads_enabled' => ((int)$global['ads_enabled'] === 1),
        'env' => $env,
        'use_test_ads' => $useTestAds,
        'config_version' => (int)$global['config_version'],
        'admob_app_id' => $selectedAppId,
        'interstitial_cooldown_seconds' => (int)$global['interstitial_cooldown_seconds'],
        'interstitial_show_every_n_actions' => (int)$global['interstitial_show_every_n_actions'],
    ]
];

include '../../includes/header.php';
?>

<div class="container-fluid">
    <div class="d-flex justify-content-between align-items-center mb-4">
        <div>
            <h1 class="mb-1">Ads Management</h1>
            <p class="text-muted mb-0">Overview for live status and direct path to configure Ads.</p>
        </div>
        <a href="ads_config.php" class="btn btn-primary btn-lg">
            <i class="bi bi-sliders me-1"></i> Configure Ads
        </a>
    </div>

    <div class="row g-3 mb-4">
        <div class="col-md-3">
            <div class="card h-100">
                <div class="card-body">
                    <div class="text-muted small">Ads Enabled</div>
                    <div class="fs-4 fw-bold <?php echo ((int)$global['ads_enabled'] === 1) ? 'text-success' : 'text-danger'; ?>">
                        <?php echo ((int)$global['ads_enabled'] === 1) ? 'ON' : 'OFF'; ?>
                    </div>
                </div>
            </div>
        </div>
        <div class="col-md-3">
            <div class="card h-100">
                <div class="card-body">
                    <div class="text-muted small">Environment</div>
                    <div class="fs-4 fw-bold text-primary"><?php echo strtoupper($env); ?></div>
                </div>
            </div>
        </div>
        <div class="col-md-3">
            <div class="card h-100">
                <div class="card-body">
                    <div class="text-muted small">Placements</div>
                    <div class="fs-4 fw-bold"><?php echo (int)$counts['active_placements']; ?> / <?php echo (int)$counts['placements']; ?></div>
                </div>
            </div>
        </div>
        <div class="col-md-3">
            <div class="card h-100">
                <div class="card-body">
                    <div class="text-muted small">Config Version</div>
                    <div class="fs-4 fw-bold">v<?php echo (int)$global['config_version']; ?></div>
                </div>
            </div>
        </div>
    </div>

    <div class="card mb-4">
        <div class="card-body">
            <h5 class="card-title mb-3">Current Status</h5>
            <div class="row g-3">
                <div class="col-md-4">
                    <div class="small text-muted">use_test_ads</div>
                    <div class="fw-semibold"><?php echo $useTestAds ? 'true' : 'false'; ?></div>
                </div>
                <div class="col-md-4">
                    <div class="small text-muted">Selected AdMob App ID</div>
                    <div class="fw-semibold"><code><?php echo htmlspecialchars($selectedAppId !== '' ? $selectedAppId : 'not_set'); ?></code></div>
                </div>
                <div class="col-md-4">
                    <div class="small text-muted">Last Updated</div>
                    <div class="fw-semibold"><?php echo htmlspecialchars((string)$global['updated_at']); ?></div>
                </div>
            </div>
        </div>
    </div>

    <div class="accordion" id="jsonPreviewAcc">
        <div class="accordion-item">
            <h2 class="accordion-header" id="jsonPreviewHeading">
                <button class="accordion-button collapsed" type="button" data-bs-toggle="collapse" data-bs-target="#jsonPreviewCollapse" aria-expanded="false">
                    JSON Preview (what app receives)
                </button>
            </h2>
            <div id="jsonPreviewCollapse" class="accordion-collapse collapse" data-bs-parent="#jsonPreviewAcc">
                <div class="accordion-body">
                    <div class="d-flex justify-content-end mb-2">
                        <a href="../../api/ads_config.php" target="_blank" class="btn btn-sm btn-outline-primary">Open API</a>
                    </div>
                    <pre class="bg-dark text-light rounded p-3 small" style="max-height: 420px; overflow:auto;"><?php echo htmlspecialchars(json_encode($preview, JSON_PRETTY_PRINT | JSON_UNESCAPED_SLASHES)); ?></pre>
                </div>
            </div>
        </div>
    </div>
</div>

<?php include '../../includes/footer.php'; ?>
