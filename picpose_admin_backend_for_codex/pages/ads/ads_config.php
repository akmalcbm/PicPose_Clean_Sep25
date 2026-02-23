<?php
session_start();
require '../../config.php';
require_once '../../app/helpers/ads_config_helper.php';

if (!isset($_SESSION['admin']) || empty($_SESSION['admin'])) {
    header("Location: " . BASE_URL . "/login.php");
    exit();
}

if (empty($_SESSION['csrf_token'])) {
    $_SESSION['csrf_token'] = bin2hex(random_bytes(32));
}
$csrfToken = $_SESSION['csrf_token'];

ensure_ads_config_schema($conn);

$global = [
    'ads_enabled' => 1,
    'environment' => 'test',
    'use_test_ads' => 1,
    'admob_app_id_test' => '',
    'admob_app_id_live' => '',
    'interstitial_cooldown_seconds' => 60,
    'interstitial_show_every_n_actions' => 3,
    'config_version' => 1,
    'updated_at' => date('Y-m-d H:i:s')
];

$globalStmt = $conn->prepare("SELECT * FROM ads_global_settings WHERE id = 1 LIMIT 1");
if ($globalStmt && $globalStmt->execute()) {
    $dbRow = $globalStmt->get_result()->fetch_assoc();
    if ($dbRow) {
        $global = array_merge($global, $dbRow);
    }
    $globalStmt->close();
}

$placements = [];
$placementsResult = $conn->query("SELECT * FROM ads_placement_settings ORDER BY placement_key ASC");
if ($placementsResult) {
    while ($row = $placementsResult->fetch_assoc()) {
        $placements[] = $row;
    }
}

$knownPlacements = [
    'home_native', 'detail_native', 'interstitial_home', 'interstitial_detail', 'rewarded', 'banner_home',
    'native_1', 'native_2', 'native_3', 'interstitial_1', 'interstitial_2', 'banner_1', 'banner_2', 'rewarded_1',
    'home_banner', 'home_interstitial', 'detail_interstitial', 'native_ad', 'rewarded_ad'
];

$env = normalize_ads_env((string)$global['environment']);
$useTestAds = ((int)$global['use_test_ads'] === 1);
$selectedAppId = ($env === 'live' && !$useTestAds)
    ? (string)($global['admob_app_id_live'] ?? '')
    : (string)($global['admob_app_id_test'] ?? '');

$previewPlacements = [];
foreach ($placements as $placement) {
    $testUnit = trim((string)($placement['ad_unit_id_test'] ?? ''));
    $liveUnit = trim((string)($placement['ad_unit_id_live'] ?? ''));
    $resolvedUnit = ($env === 'live' && !$useTestAds)
        ? ($liveUnit !== '' ? $liveUnit : $testUnit)
        : ($testUnit !== '' ? $testUnit : $liveUnit);

    $previewPlacements[$placement['placement_key']] = [
        'enabled' => ((int)$placement['enabled'] === 1),
        'ad_type' => (string)$placement['ad_type'],
        'ad_unit_id' => $resolvedUnit,
        'ad_unit_id_test' => $testUnit,
        'ad_unit_id_live' => $liveUnit,
        'notes' => (string)($placement['notes'] ?? ''),
        'updated_at' => (string)($placement['updated_at'] ?? '')
    ];
}

$previewJson = [
    'success' => true,
    'data' => [
        'ads_enabled' => ((int)$global['ads_enabled'] === 1),
        'env' => $env,
        'use_test_ads' => $useTestAds,
        'config_version' => (int)$global['config_version'],
        'updated_at' => (string)$global['updated_at'],
        'admob_app_id' => $selectedAppId,
        'admob_app_id_test' => (string)($global['admob_app_id_test'] ?? ''),
        'admob_app_id_live' => (string)($global['admob_app_id_live'] ?? ''),
        'interstitial_cooldown_seconds' => (int)$global['interstitial_cooldown_seconds'],
        'interstitial_show_every_n_actions' => (int)$global['interstitial_show_every_n_actions'],
        'placements' => (object)$previewPlacements
    ],
    'meta' => [
        'api_version' => '2.1.0-preview',
        'generated_at' => gmdate('c')
    ]
];

include '../../includes/header.php';
?>

<div class="container-fluid">
    <div class="d-flex justify-content-between align-items-center mb-4">
        <div>
            <h1 class="mb-1">Ads Configuration</h1>
            <p class="text-muted mb-0">Single control panel for global flags, AdMob IDs, placements, and app payload preview.</p>
        </div>
        <a href="index.php" class="btn btn-outline-secondary"><i class="bi bi-arrow-left me-1"></i>Back to Overview</a>
    </div>

    <?php if (!empty($_SESSION['success'])): ?>
        <div class="alert alert-success alert-dismissible fade show" role="alert">
            <?php echo htmlspecialchars($_SESSION['success']); unset($_SESSION['success']); ?>
            <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
        </div>
    <?php endif; ?>

    <?php if (!empty($_SESSION['error'])): ?>
        <div class="alert alert-danger alert-dismissible fade show" role="alert">
            <?php echo htmlspecialchars($_SESSION['error']); unset($_SESSION['error']); ?>
            <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
        </div>
    <?php endif; ?>

    <ul class="nav nav-tabs" id="adsConfigTabs" role="tablist">
        <li class="nav-item" role="presentation">
            <button class="nav-link active" id="global-tab" data-bs-toggle="tab" data-bs-target="#global" type="button" role="tab">Global</button>
        </li>
        <li class="nav-item" role="presentation">
            <button class="nav-link" id="admob-tab" data-bs-toggle="tab" data-bs-target="#admob" type="button" role="tab">AdMob Setup</button>
        </li>
        <li class="nav-item" role="presentation">
            <button class="nav-link" id="placements-tab" data-bs-toggle="tab" data-bs-target="#placements" type="button" role="tab">Placements</button>
        </li>
        <li class="nav-item" role="presentation">
            <button class="nav-link" id="preview-tab" data-bs-toggle="tab" data-bs-target="#preview" type="button" role="tab">Preview</button>
        </li>
    </ul>

    <div class="tab-content border border-top-0 rounded-bottom p-3 bg-white" id="adsConfigTabContent">
        <div class="tab-pane fade show active" id="global" role="tabpanel" aria-labelledby="global-tab">
            <form method="POST" action="save_global.php" class="row g-3">
                <input type="hidden" name="csrf_token" value="<?php echo htmlspecialchars($csrfToken); ?>">
                <div class="col-md-3">
                    <label class="form-label">Ads Enabled</label>
                    <div class="form-check form-switch mt-2">
                        <input class="form-check-input" type="checkbox" name="ads_enabled" value="1" <?php echo ((int)$global['ads_enabled'] === 1) ? 'checked' : ''; ?>>
                    </div>
                </div>
                <div class="col-md-3">
                    <label class="form-label">Environment</label>
                    <select name="environment" class="form-select" required>
                        <option value="test" <?php echo $env === 'test' ? 'selected' : ''; ?>>test</option>
                        <option value="live" <?php echo $env === 'live' ? 'selected' : ''; ?>>live</option>
                    </select>
                </div>
                <div class="col-md-3">
                    <label class="form-label">use_test_ads</label>
                    <div class="form-check form-switch mt-2">
                        <input class="form-check-input" type="checkbox" name="use_test_ads" value="1" <?php echo $useTestAds ? 'checked' : ''; ?>>
                    </div>
                </div>
                <div class="col-md-3">
                    <label class="form-label">Config Version</label>
                    <input class="form-control" value="<?php echo (int)$global['config_version']; ?>" disabled>
                </div>
                <div class="col-md-6">
                    <label class="form-label">interstitial_cooldown_seconds</label>
                    <input type="number" min="0" max="86400" name="interstitial_cooldown_seconds" class="form-control" value="<?php echo (int)$global['interstitial_cooldown_seconds']; ?>" required>
                </div>
                <div class="col-md-6">
                    <label class="form-label">interstitial_show_every_n_actions</label>
                    <input type="number" min="1" max="100" name="interstitial_show_every_n_actions" class="form-control" value="<?php echo (int)$global['interstitial_show_every_n_actions']; ?>" required>
                </div>
                <div class="col-12">
                    <button type="submit" class="btn btn-primary"><i class="bi bi-save me-1"></i>Save Global</button>
                </div>
            </form>
        </div>

        <div class="tab-pane fade" id="admob" role="tabpanel" aria-labelledby="admob-tab">
            <form method="POST" action="save_admob.php" class="row g-3">
                <input type="hidden" name="csrf_token" value="<?php echo htmlspecialchars($csrfToken); ?>">
                <div class="col-md-6">
                    <label class="form-label">AdMob App ID TEST</label>
                    <input type="text" class="form-control" name="admob_app_id_test" value="<?php echo htmlspecialchars((string)$global['admob_app_id_test']); ?>" placeholder="ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY">
                </div>
                <div class="col-md-6">
                    <label class="form-label">AdMob App ID LIVE</label>
                    <input type="text" class="form-control" name="admob_app_id_live" value="<?php echo htmlspecialchars((string)$global['admob_app_id_live']); ?>" placeholder="ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY">
                </div>
                <div class="col-12">
                    <div class="alert alert-info mb-0 small">
                        Format required: <code>ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY</code>
                    </div>
                </div>
                <div class="col-12">
                    <button type="submit" class="btn btn-primary"><i class="bi bi-save me-1"></i>Save AdMob IDs</button>
                </div>
            </form>
        </div>

        <div class="tab-pane fade" id="placements" role="tabpanel" aria-labelledby="placements-tab">
            <div class="card mb-3">
                <div class="card-header">Add Placement</div>
                <div class="card-body">
                    <form method="POST" action="save_ads_config.php" class="row g-2">
                        <input type="hidden" name="csrf_token" value="<?php echo htmlspecialchars($csrfToken); ?>">
                        <input type="hidden" name="action" value="add_placement">
                        <div class="col-md-2">
                            <input list="placementKeys" name="placement_key" class="form-control" placeholder="Placement Key" required>
                            <datalist id="placementKeys">
                                <?php foreach ($knownPlacements as $key): ?>
                                    <option value="<?php echo htmlspecialchars($key); ?>"></option>
                                <?php endforeach; ?>
                            </datalist>
                        </div>
                        <div class="col-md-2">
                            <input type="text" name="notes" class="form-control" placeholder="Screen/Use label">
                        </div>
                        <div class="col-md-1">
                            <select name="ad_type" class="form-select" required>
                                <option value="native">native</option>
                                <option value="interstitial">interstitial</option>
                                <option value="rewarded">rewarded</option>
                                <option value="banner">banner</option>
                            </select>
                        </div>
                        <div class="col-md-1 d-flex align-items-center">
                            <div class="form-check form-switch">
                                <input class="form-check-input" type="checkbox" name="enabled" value="1" checked>
                            </div>
                        </div>
                        <div class="col-md-2">
                            <input type="text" name="ad_unit_id_test" class="form-control" placeholder="TEST Unit ID">
                        </div>
                        <div class="col-md-2">
                            <input type="text" name="ad_unit_id_live" class="form-control" placeholder="LIVE Unit ID">
                        </div>
                        <div class="col-md-2">
                            <button class="btn btn-success w-100" type="submit">Add Row</button>
                        </div>
                    </form>
                </div>
            </div>

            <div class="alert alert-info py-2 small">
                Ad Unit ID format: <code>ca-app-pub-XXXXXXXXXXXXXXXX/YYYYYYYYYY</code>
            </div>

            <div class="table-responsive">
                <table class="table table-striped align-middle">
                    <thead>
                        <tr>
                            <th>Placement Key</th>
                            <th>Screen/Use Label</th>
                            <th>Format</th>
                            <th>Enabled</th>
                            <th>TEST Unit ID</th>
                            <th>LIVE Unit ID</th>
                            <th>Updated</th>
                            <th>Actions</th>
                        </tr>
                    </thead>
                    <tbody>
                        <?php if (empty($placements)): ?>
                            <tr><td colspan="8" class="text-center text-muted py-4">No placement rows yet.</td></tr>
                        <?php else: ?>
                            <?php foreach ($placements as $placement): ?>
                                <tr>
                                    <form method="POST" action="save_ads_config.php">
                                        <input type="hidden" name="csrf_token" value="<?php echo htmlspecialchars($csrfToken); ?>">
                                        <input type="hidden" name="action" value="update_placement">
                                        <input type="hidden" name="id" value="<?php echo (int)$placement['id']; ?>">
                                        <td><input class="form-control" name="placement_key" value="<?php echo htmlspecialchars((string)$placement['placement_key']); ?>" required></td>
                                        <td><input class="form-control" name="notes" value="<?php echo htmlspecialchars((string)$placement['notes']); ?>" placeholder="Screen/Use label"></td>
                                        <td>
                                            <select name="ad_type" class="form-select" required>
                                                <?php foreach (['native','interstitial','rewarded','banner'] as $type): ?>
                                                    <option value="<?php echo $type; ?>" <?php echo $placement['ad_type'] === $type ? 'selected' : ''; ?>><?php echo $type; ?></option>
                                                <?php endforeach; ?>
                                            </select>
                                        </td>
                                        <td class="text-center"><input type="checkbox" name="enabled" value="1" <?php echo ((int)$placement['enabled'] === 1) ? 'checked' : ''; ?>></td>
                                        <td><input class="form-control" name="ad_unit_id_test" value="<?php echo htmlspecialchars((string)$placement['ad_unit_id_test']); ?>"></td>
                                        <td><input class="form-control" name="ad_unit_id_live" value="<?php echo htmlspecialchars((string)$placement['ad_unit_id_live']); ?>"></td>
                                        <td class="small text-muted"><?php echo htmlspecialchars((string)$placement['updated_at']); ?></td>
                                        <td>
                                            <button class="btn btn-sm btn-primary" type="submit">Update</button>
                                    </form>
                                            <form method="POST" action="save_ads_config.php" class="d-inline" onsubmit="return confirm('Delete this placement row?');">
                                                <input type="hidden" name="csrf_token" value="<?php echo htmlspecialchars($csrfToken); ?>">
                                                <input type="hidden" name="action" value="delete_placement">
                                                <input type="hidden" name="id" value="<?php echo (int)$placement['id']; ?>">
                                                <button class="btn btn-sm btn-outline-danger" type="submit">Delete</button>
                                            </form>
                                        </td>
                                </tr>
                            <?php endforeach; ?>
                        <?php endif; ?>
                    </tbody>
                </table>
            </div>
        </div>

        <div class="tab-pane fade" id="preview" role="tabpanel" aria-labelledby="preview-tab">
            <div class="d-flex justify-content-between align-items-center mb-2">
                <h6 class="mb-0">API Payload Preview</h6>
                <a href="../../api/ads_config.php" target="_blank" class="btn btn-sm btn-outline-primary">Open Live API</a>
            </div>
            <pre class="bg-dark text-light rounded p-3 small" style="max-height: 460px; overflow:auto;"><?php echo htmlspecialchars(json_encode($previewJson, JSON_PRETTY_PRINT | JSON_UNESCAPED_SLASHES)); ?></pre>
        </div>
    </div>
</div>

<?php include '../../includes/footer.php'; ?>
