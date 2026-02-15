<?php
declare(strict_types=1);

header('Content-Type: text/html; charset=utf-8');
header('X-Content-Type-Options: nosniff');

require_once __DIR__ . '/config.php';

$privacyHtml = '';
$updatedAt = '';
$appName = 'PicPose';

try {
    if (!isset($conn) || !($conn instanceof mysqli)) {
        throw new RuntimeException('Database connection is unavailable.');
    }

    $sql = "SELECT app_name, privacy_policy, updated_at
            FROM app_settings
            ORDER BY id DESC
            LIMIT 1";
    $stmt = $conn->prepare($sql);
    if (!$stmt) {
        throw new RuntimeException('Failed to prepare query: ' . $conn->error);
    }

    if (!$stmt->execute()) {
        throw new RuntimeException('Failed to execute query: ' . $stmt->error);
    }

    $row = $stmt->get_result()?->fetch_assoc() ?? null;
    if ($row) {
        $appName = trim((string)($row['app_name'] ?? '')) !== '' ? (string)$row['app_name'] : $appName;
        $privacyHtml = (string)($row['privacy_policy'] ?? '');
        $updatedAt = (string)($row['updated_at'] ?? '');
        // Keep support contact email consistent on the public page.
        $privacyHtml = str_replace(
            ['support@picpose.iamakmal.in', 'support@picpose.com'],
            'picposeapp@gmail.com',
            $privacyHtml
        );
    }
} catch (Throwable $e) {
    http_response_code(500);
    $safeMessage = htmlspecialchars($e->getMessage(), ENT_QUOTES, 'UTF-8');
    echo "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\"><title>Privacy Policy</title></head><body><h1>Unable to load Privacy Policy</h1><p>{$safeMessage}</p></body></html>";
    exit;
}

$safeTitle = htmlspecialchars($appName . ' Privacy Policy', ENT_QUOTES, 'UTF-8');
$safeUpdatedAt = htmlspecialchars($updatedAt, ENT_QUOTES, 'UTF-8');
?>
<!doctype html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title><?= $safeTitle ?></title>
    <style>
        :root {
            color-scheme: light;
        }
        body {
            margin: 0;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
            background: #f6f8fb;
            color: #1f2937;
            line-height: 1.65;
        }
        .wrap {
            max-width: 860px;
            margin: 32px auto;
            padding: 0 16px;
        }
        .card {
            background: #ffffff;
            border: 1px solid #e5e7eb;
            border-radius: 14px;
            box-shadow: 0 8px 22px rgba(15, 23, 42, 0.06);
            padding: 28px 22px;
        }
        h1 {
            margin: 0 0 6px;
            font-size: 30px;
            line-height: 1.2;
        }
        .meta {
            margin: 0 0 24px;
            color: #6b7280;
            font-size: 14px;
        }
        .content h2, .content h3, .content h4 {
            margin-top: 24px;
            margin-bottom: 10px;
            line-height: 1.3;
        }
        .content p, .content li {
            font-size: 16px;
        }
        .content a {
            color: #0f5ed7;
            text-decoration: none;
        }
        .content a:hover {
            text-decoration: underline;
        }
        .empty {
            padding: 18px;
            border-radius: 10px;
            background: #fffbeb;
            border: 1px solid #fef3c7;
            color: #92400e;
        }
    </style>
</head>
<body>
<main class="wrap">
    <article class="card">
        <h1>Privacy Policy</h1>
        <p class="meta">
            App: <?= htmlspecialchars($appName, ENT_QUOTES, 'UTF-8') ?>
            <?= $safeUpdatedAt !== '' ? ' | Updated: ' . $safeUpdatedAt : '' ?>
        </p>
        <section class="content">
            <?php if (trim($privacyHtml) !== ''): ?>
                <?= $privacyHtml ?>
            <?php else: ?>
                <div class="empty">No privacy policy content is available right now.</div>
            <?php endif; ?>
        </section>
    </article>
</main>
</body>
</html>
