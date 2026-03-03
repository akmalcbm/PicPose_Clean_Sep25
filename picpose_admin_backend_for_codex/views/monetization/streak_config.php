<?php
session_start();
require '../../config.php';
if (!isset($_SESSION['admin'])) { header('Location: ../../login.php'); exit(); }

if (empty($_SESSION['csrf_token'])) $_SESSION['csrf_token'] = bin2hex(random_bytes(32));
$csrf = $_SESSION['csrf_token'];

function default_daily_rewards(): array
{
    return [10, 20, 30, 40, 50, 60, 100];
}

function detect_config_table(mysqli $conn): ?string
{
    $checkSql = "SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ? LIMIT 1";
    $stmt = $conn->prepare($checkSql);
    if (!$stmt) return null;

    $table = 'pricing_config';
    $stmt->bind_param('s', $table);
    $stmt->execute();
    $exists = (bool)$stmt->get_result()->fetch_assoc();
    if ($exists) {
        $stmt->close();
        return 'pricing_config';
    }

    $table = 'app_config';
    $stmt->bind_param('s', $table);
    $stmt->execute();
    $exists = (bool)$stmt->get_result()->fetch_assoc();
    $stmt->close();
    if ($exists) {
        return 'app_config';
    }

    $create = "
        CREATE TABLE IF NOT EXISTS app_config (
            key_name VARCHAR(120) NOT NULL,
            value_json JSON NULL,
            updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            PRIMARY KEY (key_name)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    ";
    if (!$conn->query($create)) {
        return null;
    }
    return 'app_config';
}

function sanitize_rewards_from_array(array $arr): array
{
    $out = [];
    for ($i = 0; $i < 7; $i++) {
        $v = isset($arr[$i]) ? (int)$arr[$i] : 0;
        if ($v < 0) $v = 0;
        if ($v > 1000) $v = 1000;
        $out[] = $v;
    }
    return $out;
}

function load_rewards(mysqli $conn, ?string $table): array
{
    $defaults = default_daily_rewards();
    if ($table === null) return $defaults;

    $sql = "SELECT value_json FROM {$table} WHERE key_name = 'daily_login_rewards' LIMIT 1";
    $res = $conn->query($sql);
    if (!$res) return $defaults;
    $row = $res->fetch_assoc();
    if (!$row || !isset($row['value_json'])) return $defaults;

    $decoded = json_decode((string)$row['value_json'], true);
    if (!is_array($decoded)) return $defaults;
    $rewards = $decoded['rewards'] ?? null;
    if (!is_array($rewards)) return $defaults;

    return sanitize_rewards_from_array($rewards);
}

function table_has_updated_at(mysqli $conn, string $table): bool
{
    $sql = "SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = ? AND column_name = 'updated_at' LIMIT 1";
    $stmt = $conn->prepare($sql);
    if (!$stmt) return false;
    $stmt->bind_param('s', $table);
    $stmt->execute();
    $exists = (bool)$stmt->get_result()->fetch_assoc();
    $stmt->close();
    return $exists;
}

function save_rewards(mysqli $conn, string $table, array $rewards): bool
{
    $json = json_encode(['rewards' => $rewards], JSON_UNESCAPED_UNICODE);
    if ($json === false) return false;

    if (table_has_updated_at($conn, $table)) {
        $sql = "
            INSERT INTO {$table} (key_name, value_json, updated_at)
            VALUES ('daily_login_rewards', ?, CURRENT_TIMESTAMP)
            ON DUPLICATE KEY UPDATE
                value_json = VALUES(value_json),
                updated_at = CURRENT_TIMESTAMP
        ";
    } else {
        $sql = "
            INSERT INTO {$table} (key_name, value_json)
            VALUES ('daily_login_rewards', ?)
            ON DUPLICATE KEY UPDATE
                value_json = VALUES(value_json)
        ";
    }

    $stmt = $conn->prepare($sql);
    if (!$stmt) return false;
    $stmt->bind_param('s', $json);
    $ok = $stmt->execute();
    $stmt->close();
    return (bool)$ok;
}

$table = detect_config_table($conn);

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $csrfPost = $_POST['csrf_token'] ?? '';
    if (!hash_equals((string)$csrf, (string)$csrfPost)) {
        $_SESSION['message'] = 'Invalid CSRF token.';
        $_SESSION['message_type'] = 'danger';
        header('Location: streak_config.php');
        exit();
    }

    $input = [];
    for ($i = 1; $i <= 7; $i++) {
        $input[] = (int)($_POST['day_' . $i] ?? 0);
    }
    $rewards = sanitize_rewards_from_array($input);

    if ($table === null) {
        $_SESSION['message'] = 'Unable to resolve config table.';
        $_SESSION['message_type'] = 'danger';
    } elseif (save_rewards($conn, $table, $rewards)) {
        $_SESSION['message'] = 'Daily login rewards updated successfully.';
        $_SESSION['message_type'] = 'success';
    } else {
        $_SESSION['message'] = 'Failed to save rewards config.';
        $_SESSION['message_type'] = 'danger';
    }

    header('Location: streak_config.php');
    exit();
}

$rewards = load_rewards($conn, $table);

include '../../includes/header.php';
?>

<div class="container">
  <div class="d-flex justify-content-between align-items-center mb-3">
    <h2 class="mb-0">Streak Config</h2>
    <a href="user_wallets.php" class="btn btn-outline-secondary">Back to Wallets</a>
  </div>

  <?php if(!empty($_SESSION['message'])): ?>
    <div class="alert alert-<?php echo htmlspecialchars($_SESSION['message_type'] ?? 'info'); ?>">
      <?php echo htmlspecialchars($_SESSION['message']); unset($_SESSION['message'], $_SESSION['message_type']); ?>
    </div>
  <?php endif; ?>

  <div class="alert alert-info">
    Config table in use: <strong><?php echo htmlspecialchars((string)($table ?? 'unavailable')); ?></strong>
  </div>

  <form method="POST" class="card">
    <div class="card-body">
      <input type="hidden" name="csrf_token" value="<?php echo htmlspecialchars($csrf); ?>">

      <div class="row g-3">
        <?php for ($i = 1; $i <= 7; $i++): ?>
          <div class="col-md-3 col-sm-6">
            <label class="form-label">Day <?php echo $i; ?></label>
            <input
              type="number"
              min="0"
              max="1000"
              name="day_<?php echo $i; ?>"
              class="form-control"
              value="<?php echo (int)$rewards[$i - 1]; ?>"
              required
            >
          </div>
        <?php endfor; ?>
      </div>

      <div class="mt-3">
        <button type="submit" class="btn btn-primary">Save Rewards</button>
      </div>
    </div>
  </form>
</div>

<?php include '../../includes/footer.php'; ?>
