<?php
require_once __DIR__ . '/email_verification_lib.php';

header('Content-Type: text/html; charset=utf-8');

if (!$conn || $conn->connect_errno) {
    http_response_code(500);
    echo '<!doctype html><html><body><h2>Server unavailable</h2></body></html>';
    exit();
}

$token = trim((string)($_GET['token'] ?? ''));
$result = auth_consume_email_verification_token($conn, $token);
$deepLink = 'picpose://verify-email?token=' . rawurlencode($token);
$title = $result['ok'] ? 'Email verified' : 'Verification failed';
$message = $result['ok']
    ? 'Your email is now verified. You can return to PicPose.'
    : 'This verification link is invalid or expired.';
?>
<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title><?php echo htmlspecialchars($title, ENT_QUOTES, 'UTF-8'); ?></title>
  <style>
    body{font-family:Arial,sans-serif;background:#f5f7fb;margin:0;padding:24px;color:#1f2937}
    .card{max-width:560px;margin:8vh auto;background:#fff;border-radius:14px;padding:24px;box-shadow:0 10px 26px rgba(0,0,0,.08)}
    .btn{display:inline-block;background:#111827;color:#fff;text-decoration:none;padding:12px 18px;border-radius:10px;font-weight:600}
    .muted{color:#6b7280;font-size:14px}
  </style>
</head>
<body>
  <div class="card">
    <h2><?php echo htmlspecialchars($title, ENT_QUOTES, 'UTF-8'); ?></h2>
    <p><?php echo htmlspecialchars($message, ENT_QUOTES, 'UTF-8'); ?></p>
    <p><a class="btn" href="<?php echo htmlspecialchars($deepLink, ENT_QUOTES, 'UTF-8'); ?>">Open PicPose App</a></p>
    <p class="muted">If app does not open, launch PicPose manually and check Profile.</p>
  </div>
</body>
</html>
