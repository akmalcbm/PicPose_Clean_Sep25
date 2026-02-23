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
$csrf_token = $_SESSION['csrf_token'];

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
    $row = $globalStmt->get_result()->fetch_assoc();
    if ($row) {
        $global = array_merge($global, $row);
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
    'home_native',
    'detail_native',
    'interstitial_detail',
    'interstitial_home',
    'rewarded',
    'banner_home',
    'native_1',
    'native_2',
    'native_3',
    'interstitial_1',
    'interstitial_2',
    'banner_1',
    'banner_2',
    'rewarded_1',
    'home_banner',
    'home_interstitial',
    'detail_interstitial',
    'native_ad',
    'rewarded_ad'
];

include '../../includes/header.php';
?>

<div class="container-fluid">
    <div class="page-header mb-4">
        <div class="d-flex justify-content-between align-items-center">
            <div>
                <h1 class="mb-1">Ads Configuration</h1>
                <p class="text-muted mb-0">Manage global flags, AdMob App IDs, and per-placement test/live units.</p>
            </div>
            <a href="index.php" class="btn btn-outline-secondary"><i class="bi bi-arrow-left me-1"></i>Back</a>
        </div>
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

    <div class="card mb-4">
        <div class="card-header"><h5 class="mb-0">Global</h5></div>
        <div class="card-body">
            <form method="POST" action="save_ads_config.php" class="row g-3">
                <input type="hidden" name="csrf_token" value="<?php echo htmlspecialchars($csrf_token); ?>">
                <input type="hidden" name="action" value="save_global">

                <div class="col-md-3">
                    <label class="form-label">Ads Enabled</label>
                    <div class="form-check form-switch mt-2">
                        <input class="form-check-input" type="checkbox" name="ads_enabled" value="1" <?php echo ((int)$global['ads_enabled'] === 1) ? 'checked' : ''; ?>>
                    </div>
                </div>

                <div class="col-md-3">
                    <label class="form-label">Environment</label>
                    <select name="environment" class="form-select" required>
                        <option value="test" <?php echo normalize_ads_env((string)$global['environment']) === 'test' ? 'selected' : ''; ?>>test</option>
                        <option value="live" <?php echo normalize_ads_env((string)$global['environment']) === 'live' ? 'selected' : ''; ?>>live</option>
                    </select>
                </div>

                <div class="col-md-3">
                    <label class="form-label">useTestAds</label>
                    <div class="form-check form-switch mt-2">
                        <input class="form-check-input" type="checkbox" name="use_test_ads" value="1" <?php echo ((int)$global['use_test_ads'] === 1) ? 'checked' : ''; ?>>
                    </div>
                </div>

                <div class="col-md-3">
                    <label class="form-label">Config Version</label>
                    <input type="text" class="form-control" value="<?php echo (int)$global['config_version']; ?>" disabled>
                </div>

                <div class="col-md-6">
                    <label class="form-label">AdMob App ID (test)</label>
                    <input type="text" name="admob_app_id_test" class="form-control" placeholder="ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY" value="<?php echo htmlspecialchars((string)($global['admob_app_id_test'] ?? '')); ?>">
                </div>

                <div class="col-md-6">
                    <label class="form-label">AdMob App ID (live)</label>
                    <input type="text" name="admob_app_id_live" class="form-control" placeholder="ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY" value="<?php echo htmlspecialchars((string)($global['admob_app_id_live'] ?? '')); ?>">
                </div>

                <div class="col-md-6">
                    <label class="form-label">Interstitial Cooldown (seconds)</label>
                    <input type="number" min="0" max="86400" name="interstitial_cooldown_seconds" class="form-control" value="<?php echo (int)$global['interstitial_cooldown_seconds']; ?>" required>
                </div>

                <div class="col-md-6">
                    <label class="form-label">Interstitial Every N Actions</label>
                    <input type="number" min="1" max="100" name="interstitial_show_every_n_actions" class="form-control" value="<?php echo (int)$global['interstitial_show_every_n_actions']; ?>" required>
                </div>

                <div class="col-12">
                    <button type="submit" class="btn btn-primary"><i class="bi bi-save me-1"></i>Save Global</button>
                </div>
            </form>
        </div>
    </div>

    <div class="card mb-4">
        <div class="card-header"><h5 class="mb-0">Add Placement</h5></div>
        <div class="card-body">
            <form method="POST" action="save_ads_config.php" class="row g-3">
                <input type="hidden" name="csrf_token" value="<?php echo htmlspecialchars($csrf_token); ?>">
                <input type="hidden" name="action" value="add_placement">

                <div class="col-md-3">
                    <label class="form-label">Placement Key</label>
                    <input list="placementKeys" name="placement_key" class="form-control" placeholder="home_native" required>
                    <datalist id="placementKeys">
                        <?php foreach ($knownPlacements as $key): ?>
                            <option value="<?php echo htmlspecialchars($key); ?>"></option>
                        <?php endforeach; ?>
                    </datalist>
                </div>

                <div class="col-md-2">
                    <label class="form-label">Type</label>
                    <select name="ad_type" class="form-select" required>
                        <option value="banner">banner</option>
                        <option value="native">native</option>
                        <option value="interstitial">interstitial</option>
                        <option value="rewarded">rewarded</option>
                    </select>
                </div>

                <div class="col-md-2">
                    <label class="form-label">Enabled</label>
                    <div class="form-check form-switch mt-2">
                        <input class="form-check-input" type="checkbox" name="enabled" value="1" checked>
                    </div>
                </div>

                <div class="col-md-5">
                    <label class="form-label">Ad Unit ID (test)</label>
                    <input type="text" name="ad_unit_id_test" class="form-control" placeholder="ca-app-pub-XXXXXXXXXXXXXXXX/YYYYYYYYYY">
                </div>

                <div class="col-md-5">
                    <label class="form-label">Ad Unit ID (live)</label>
                    <input type="text" name="ad_unit_id_live" class="form-control" placeholder="ca-app-pub-XXXXXXXXXXXXXXXX/YYYYYYYYYY">
                </div>

                <div class="col-md-7">
                    <label class="form-label">Notes</label>
                    <input type="text" name="notes" class="form-control" maxlength="500" placeholder="Optional note">
                </div>

                <div class="col-12">
                    <button type="submit" class="btn btn-success"><i class="bi bi-plus-circle me-1"></i>Add Placement</button>
                </div>
            </form>
        </div>
    </div>

    <div class="card">
        <div class="card-header"><h5 class="mb-0">Placements</h5></div>
        <div class="card-body p-0">
            <div class="table-responsive">
                <table class="table table-striped mb-0">
                    <thead>
                        <tr>
                            <th>Placement</th>
                            <th>Type</th>
                            <th>Enabled</th>
                            <th>Test Unit</th>
                            <th>Live Unit</th>
                            <th>Notes</th>
                            <th>Updated</th>
                            <th>Actions</th>
                        </tr>
                    </thead>
                    <tbody>
                        <?php if (empty($placements)): ?>
                            <tr><td colspan="8" class="text-center py-4 text-muted">No placements configured</td></tr>
                        <?php else: ?>
                            <?php foreach ($placements as $placement): ?>
                                <tr>
                                    <form method="POST" action="save_ads_config.php">
                                        <input type="hidden" name="csrf_token" value="<?php echo htmlspecialchars($csrf_token); ?>">
                                        <input type="hidden" name="action" value="update_placement">
                                        <input type="hidden" name="id" value="<?php echo (int)$placement['id']; ?>">
                                        <td>
                                            <input class="form-control" type="text" name="placement_key" value="<?php echo htmlspecialchars((string)$placement['placement_key']); ?>" required>
                                        </td>
                                        <td>
                                            <select name="ad_type" class="form-select" required>
                                                <?php foreach (['banner','native','interstitial','rewarded'] as $type): ?>
                                                    <option value="<?php echo $type; ?>" <?php echo $placement['ad_type'] === $type ? 'selected' : ''; ?>><?php echo $type; ?></option>
                                                <?php endforeach; ?>
                                            </select>
                                        </td>
                                        <td class="text-center align-middle">
                                            <input type="checkbox" name="enabled" value="1" <?php echo ((int)$placement['enabled'] === 1) ? 'checked' : ''; ?>>
                                        </td>
                                        <td>
                                            <input class="form-control" type="text" name="ad_unit_id_test" value="<?php echo htmlspecialchars((string)($placement['ad_unit_id_test'] ?? '')); ?>">
                                        </td>
                                        <td>
                                            <input class="form-control" type="text" name="ad_unit_id_live" value="<?php echo htmlspecialchars((string)($placement['ad_unit_id_live'] ?? '')); ?>">
                                        </td>
                                        <td>
                                            <input class="form-control" type="text" name="notes" maxlength="500" value="<?php echo htmlspecialchars((string)($placement['notes'] ?? '')); ?>">
                                        </td>
                                        <td class="align-middle small text-muted">
                                            <?php echo htmlspecialchars((string)($placement['updated_at'] ?? '')); ?>
                                        </td>
                                        <td class="align-middle">
                                            <div class="btn-group" role="group">
                                                <button type="submit" class="btn btn-sm btn-primary">Update</button>
                                            </div>
                                    </form>
                                            <form method="POST" action="save_ads_config.php" class="d-inline" onsubmit="return confirm('Delete this placement?');">
                                                <input type="hidden" name="csrf_token" value="<?php echo htmlspecialchars($csrf_token); ?>">
                                                <input type="hidden" name="action" value="delete_placement">
                                                <input type="hidden" name="id" value="<?php echo (int)$placement['id']; ?>">
                                                <button type="submit" class="btn btn-sm btn-outline-danger">Delete</button>
                                            </form>
                                        </td>
                                </tr>
                            <?php endforeach; ?>
                        <?php endif; ?>
                    </tbody>
                </table>
            </div>
        </div>
    </div>
</div>

<?php include '../../includes/footer.php'; ?>
