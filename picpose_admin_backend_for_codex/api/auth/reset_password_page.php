<?php
$token = trim((string)($_GET['token'] ?? ''));
if ($token === '') {
    http_response_code(400);
    echo '<!doctype html><html><body><h2>Invalid link</h2></body></html>';
    exit();
}
$appDeepLink = 'picpose://reset-password?token=' . rawurlencode($token);
?>
<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>PicPose Password Reset</title>
  <style>
    body{font-family:Arial,sans-serif;background:#f5f7fb;margin:0;padding:24px;color:#1f2937}
    .card{max-width:560px;margin:8vh auto;background:#fff;border-radius:14px;padding:24px;box-shadow:0 10px 26px rgba(0,0,0,.08)}
    .btn{display:inline-block;background:#111827;color:#fff;text-decoration:none;padding:12px 18px;border-radius:10px;font-weight:600}
    .muted{color:#6b7280;font-size:14px}
  </style>
</head>
<body>
  <div class="card">
    <h2>Reset your PicPose password</h2>
    <p>Tap below to open the app and continue password reset.</p>
    <p><a class="btn" href="<?php echo htmlspecialchars($appDeepLink, ENT_QUOTES, 'UTF-8'); ?>">Open PicPose App</a></p>
    <p class="muted">If the app does not open, install/update PicPose and reopen this email link.</p>
  </div>
  <script>
    window.location.href = <?php echo json_encode($appDeepLink); ?>;
  </script>
</body>
</html>
