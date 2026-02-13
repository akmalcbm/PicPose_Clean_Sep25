<?php 
session_start();
require 'config.php';

if (!isset($_SESSION['admin'])) {
    header("Location: login.php");
    exit();
}

$message = $_SESSION['message'] ?? null;
unset($_SESSION['message']);
$message_type = $_SESSION['message_type'] ?? 'info';
unset($_SESSION['message_type']);

if (empty($_SESSION['csrf_token'])) {
    $_SESSION['csrf_token'] = bin2hex(random_bytes(32));
}
$csrf = $_SESSION['csrf_token'];

// Fetch current settings
$sql = "SELECT * FROM app_settings LIMIT 1";
$result = $conn->query($sql);
$settings = $result->fetch_assoc();

// Create default array if no data
if (!$settings) {
    $settings = [
        'admin_name' => '',
        'app_name' => '',
        'tagline' => '',
        'description' => '',
        'google_play_url' => '',
        'privacy_policy' => '',
        'terms_conditions' => '',
        'support_email' => '',
        'support_phone' => '',
        'about' => ''
    ];
}

include 'includes/header.php';
?>

<h2>⚙️ App Settings</h2>

<?php if ($message): ?>
  <div class="alert alert-<?php echo htmlspecialchars($message_type); ?>">
    <?php echo htmlspecialchars($message); ?>
  </div>
<?php endif; ?>

<!-- ✅ Start of main form -->
<div class="card p-3 mb-4">
  <form method="POST" action="update_settings.php">
    <input type="hidden" name="csrf_token" value="<?php echo htmlspecialchars($csrf); ?>">

    <div class="row">
      <div class="col-md-12">
        <div class="row">
          <div class="col-md-6">
            <div class="mb-3">
              <label>Admin Name:</label>
              <input type="text" name="admin_name" class="form-control" value="<?= htmlspecialchars($settings['admin_name']); ?>" required>
            </div>
          </div>
          <div class="col-md-6">
            <div class="mb-3">
              <label>App Name:</label>
              <input type="text" name="app_name" class="form-control" value="<?= htmlspecialchars($settings['app_name']); ?>" required>
            </div>
          </div>
        </div>
        
        <div class="row">
          <div class="col-md-6">
            <div class="mb-3">
              <label>Tagline:</label>
              <input type="text" name="tagline" class="form-control" value="<?= htmlspecialchars($settings['tagline']); ?>">
            </div>
          </div>
          <div class="col-md-6">
            <div class="mb-3">
              <label>Google Play URL:</label>
              <input type="text" name="google_play_url" class="form-control" value="<?= htmlspecialchars($settings['google_play_url']); ?>">
            </div>
          </div>
        </div>
        
        <div class="mb-3">
          <label>App Description (Plain Text):</label>
          <textarea name="description" class="form-control" rows="4"><?= htmlspecialchars($settings['description']); ?></textarea>
          <small class="text-muted">Simple text description shown in app listings</small>
        </div>
      </div>
    </div>

    <hr class="my-4">

    <h5>📜 Legal Content (HTML Format)</h5>
    
    <div class="mb-3">
      <label>Privacy Policy:</label>
      <textarea name="privacy_policy" id="privacy_editor" class="form-control" rows="8" placeholder="Enter HTML content for Privacy Policy..."><?= htmlspecialchars_decode($settings['privacy_policy']); ?></textarea>
      <small class="text-muted">Use HTML tags for formatting. This will be displayed in app.</small>
    </div>

    <h5>ℹ️ Terms & Conditions (HTML Format)</h5>
    <div class="mb-3">
      <label>Terms & Conditions:</label>
      <textarea name="terms_conditions" id="terms_editor" class="form-control" rows="8" placeholder="Enter HTML content for Terms & Conditions..."><?= htmlspecialchars_decode($settings['terms_conditions']); ?></textarea>
      <small class="text-muted">Use HTML tags for formatting. This will be displayed in app.</small>
    </div>

    <hr class="my-4">

    <h5>📞 Support Info</h5>
    <div class="row">
      <div class="col-md-6">
        <div class="mb-3">
          <label>Support Email:</label>
          <input type="email" name="support_email" class="form-control" value="<?= htmlspecialchars($settings['support_email']); ?>">
        </div>
      </div>
      <div class="col-md-6">
        <div class="mb-3">
          <label>Support Phone:</label>
          <input type="text" name="support_phone" class="form-control" value="<?= htmlspecialchars($settings['support_phone']); ?>">
        </div>
      </div>
    </div>

    <hr class="my-4">

    <h5>ℹ️ About App (HTML Format)</h5>
    <div class="mb-3">
      <label>About Description:</label>
      <textarea name="about" id="about_editor" class="form-control" rows="8" placeholder="Enter HTML content for About section..."><?= htmlspecialchars_decode($settings['about']); ?></textarea>
      <small class="text-muted">Use HTML tags for formatting. This will be displayed in app's About screen.</small>
    </div>

    <button type="submit" class="btn btn-primary w-100">💾 Save Settings</button>
  </form>
</div>

<!-- ✅ Change Password section OUTSIDE the main form -->
<div class="card p-3 mt-4">
  <h4>Change Admin Password</h4>
  <p class="small text-muted">Click the button below to open a secure dialog for changing the admin password.</p>
  <button type="button" class="btn btn-warning w-100" data-bs-toggle="modal" data-bs-target="#changePasswordModal">
    Change Password
  </button>
</div>

<!-- Modal for password change -->
<div class="modal fade" id="changePasswordModal" tabindex="-1" aria-labelledby="changePasswordModalLabel" aria-hidden="true" data-bs-backdrop="static" data-bs-keyboard="false">
  <div class="modal-dialog modal-dialog-centered">
    <div class="modal-content">
      <form method="POST" action="change_password.php" id="changePasswordForm">
        <input type="hidden" name="csrf_token" value="<?php echo htmlspecialchars($csrf); ?>">
        <div class="modal-header">
          <h5 class="modal-title">Update Admin Password</h5>
          <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
        </div>
        <div class="modal-body">
          <div class="mb-3">
            <label>Current Password</label>
            <input type="password" name="current_password" class="form-control" required>
          </div>
          <div class="mb-3">
            <label>New Password</label>
            <input type="password" name="new_password" class="form-control" minlength="8" required>
          </div>
          <div class="mb-3">
            <label>Confirm New Password</label>
            <input type="password" name="confirm_password" class="form-control" required>
          </div>
          <div id="pwdMismatch" class="text-danger small" style="display:none;">Passwords do not match.</div>
        </div>
        <div class="modal-footer">
          <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Cancel</button>
          <button type="submit" class="btn btn-warning">Update Password</button>
        </div>
      </form>
    </div>
  </div>
</div>

<script>
// Helper function to insert HTML tags
function insertHtmlTag(textareaId, openTag, closeTag = '') {
  const textarea = document.getElementById(textareaId);
  const start = textarea.selectionStart;
  const end = textarea.selectionEnd;
  const selectedText = textarea.value.substring(start, end);
  const newText = openTag + selectedText + closeTag;
  
  textarea.value = textarea.value.substring(0, start) + newText + textarea.value.substring(end);
  textarea.focus();
  textarea.selectionStart = start + openTag.length;
  textarea.selectionEnd = start + openTag.length + selectedText.length;
}

// Password validation
document.addEventListener('DOMContentLoaded', function () {
  const form = document.getElementById('changePasswordForm');
  const newPwd = form.querySelector('[name="new_password"]');
  const confirmPwd = form.querySelector('[name="confirm_password"]');
  const mismatch = document.getElementById('pwdMismatch');
  form.addEventListener('submit', e => {
    if (newPwd.value !== confirmPwd.value) {
      e.preventDefault();
      mismatch.style.display = 'block';
    } else {
      mismatch.style.display = 'none';
    }
  });
});
</script>

<?php include 'includes/footer.php'; ?>