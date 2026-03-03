<?php
declare(strict_types=1);

require_once dirname(__DIR__) . '/config.php';

$playUrl = 'https://play.google.com/store/apps/details?id=com.picpose.bestphotographyapp';
$rawCode = strtoupper(trim((string)($_GET['code'] ?? '')));
$code = preg_match('/^[A-Z0-9]{4,16}$/', $rawCode) ? $rawCode : '';
$codeExists = false;

if ($code !== '' && isset($conn) && $conn instanceof mysqli) {
    $stmt = $conn->prepare('SELECT 1 FROM referral_codes WHERE code = ? LIMIT 1');
    if ($stmt) {
        $stmt->bind_param('s', $code);
        $stmt->execute();
        $res = $stmt->get_result();
        $codeExists = (bool)($res && $res->fetch_assoc());
        $stmt->close();
    }
}

header('X-Robots-Tag: noindex, nofollow', true);
?><!doctype html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="robots" content="noindex,nofollow">
    <title>Install PicPose</title>
    <style>
        body {
            margin: 0;
            font-family: Arial, sans-serif;
            background: linear-gradient(180deg, #f5f1e8 0%, #ffffff 100%);
            color: #1d1d1f;
        }
        .wrap {
            max-width: 560px;
            margin: 0 auto;
            padding: 48px 20px;
        }
        .card {
            background: #fffdf8;
            border: 1px solid #eadfca;
            border-radius: 20px;
            padding: 28px;
            box-shadow: 0 20px 50px rgba(65, 48, 20, 0.08);
        }
        h1 {
            margin: 0 0 12px;
            font-size: 30px;
            line-height: 1.15;
        }
        p {
            margin: 0 0 14px;
            line-height: 1.55;
        }
        .code {
            display: inline-block;
            margin: 10px 0 18px;
            padding: 10px 14px;
            border-radius: 12px;
            background: #1d1d1f;
            color: #fff;
            font-weight: 700;
            letter-spacing: 0.08em;
        }
        .btn {
            display: inline-block;
            margin-top: 8px;
            padding: 14px 18px;
            border-radius: 12px;
            background: #0f7b46;
            color: #fff;
            text-decoration: none;
            font-weight: 700;
        }
        .muted {
            color: #5f6368;
            font-size: 14px;
        }
    </style>
</head>
<body>
<div class="wrap">
    <div class="card">
        <h1>Install PicPose</h1>
        <p>Get PicPose: AI Prompts &amp; Posing Guide from Google Play, then open the Rewards tab and apply your referral code.</p>
        <?php if ($codeExists): ?>
            <p>Your referral code:</p>
            <div class="code"><?php echo htmlspecialchars($code, ENT_QUOTES, 'UTF-8'); ?></div>
            <p class="muted">After installing, open <strong>Rewards</strong> and tap <strong>Apply Code</strong>.</p>
        <?php elseif ($code !== ''): ?>
            <p class="muted">That referral code was not recognized, but you can still install the app from Google Play below.</p>
        <?php endif; ?>
        <a class="btn" href="<?php echo htmlspecialchars($playUrl, ENT_QUOTES, 'UTF-8'); ?>">Install from Google Play</a>
    </div>
</div>
</body>
</html>
