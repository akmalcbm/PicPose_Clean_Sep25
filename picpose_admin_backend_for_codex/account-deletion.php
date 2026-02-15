<?php
declare(strict_types=1);

header('Content-Type: text/html; charset=utf-8');
header('X-Content-Type-Options: nosniff');
?>
<!doctype html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>PicPose Account & Data Deletion</title>
    <meta name="description" content="How to request account and data deletion for PicPose.">
    <style>
        :root { color-scheme: light; }
        body {
            margin: 0;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
            background: #f6f8fb;
            color: #1f2937;
            line-height: 1.65;
        }
        .wrap {
            max-width: 900px;
            margin: 28px auto;
            padding: 0 16px 24px;
        }
        .card {
            background: #ffffff;
            border: 1px solid #e5e7eb;
            border-radius: 14px;
            box-shadow: 0 8px 22px rgba(15, 23, 42, 0.06);
            padding: 26px 22px;
        }
        h1 {
            margin: 0 0 8px;
            font-size: 30px;
            line-height: 1.2;
        }
        h2 {
            margin-top: 24px;
            margin-bottom: 8px;
            font-size: 21px;
            line-height: 1.3;
        }
        p, li { font-size: 16px; }
        .muted {
            color: #6b7280;
            font-size: 14px;
            margin: 0 0 20px;
        }
        .note {
            background: #eff6ff;
            border: 1px solid #bfdbfe;
            color: #1e3a8a;
            border-radius: 10px;
            padding: 12px 14px;
        }
        .warning {
            background: #fffbeb;
            border: 1px solid #fde68a;
            color: #92400e;
            border-radius: 10px;
            padding: 12px 14px;
        }
        a {
            color: #0f5ed7;
            text-decoration: none;
        }
        a:hover { text-decoration: underline; }
        code {
            background: #f3f4f6;
            border: 1px solid #e5e7eb;
            border-radius: 6px;
            padding: 2px 6px;
        }
    </style>
</head>
<body>
<main class="wrap">
    <article class="card">
        <h1>PicPose Account & Data Deletion</h1>
        <p class="muted">
            App: <strong>PicPose</strong> | Developer: <strong>Akmal Ansari</strong><br>
            Last updated: <?= htmlspecialchars(gmdate('Y-m-d'), ENT_QUOTES, 'UTF-8') ?>
        </p>

        <p>
            This page explains how users of <strong>PicPose</strong> can request deletion of their account
            and associated data.
        </p>

        <h2>How to Request Account Deletion</h2>
        <ol>
            <li>Send an email to <a href="mailto:picposeapp@gmail.com">picposeapp@gmail.com</a> with subject line <code>Account Deletion Request</code>.</li>
            <li>Include the email address used in PicPose login (or your social login provider and display name).</li>
            <li>Include your user ID (if available) and request text: <code>Please delete my PicPose account and associated data.</code></li>
            <li>We may ask for additional verification to confirm account ownership before deletion.</li>
        </ol>

        <div class="note">
            If you cannot access your account email, mention this in your request and provide details that help verify ownership.
        </div>

        <h2>What Data Is Deleted</h2>
        <ul>
            <li>Account profile data (such as name, email, profile image, bio).</li>
            <li>Account-linked app activity data stored on our server (such as favorites/engagement records tied to your account).</li>
            <li>Registered push notification tokens linked to the account/device.</li>
        </ul>

        <h2>What May Be Retained</h2>
        <ul>
            <li>Security, fraud-prevention, and legal compliance records where retention is required by law.</li>
            <li>Technical backup records for a limited period before automatic overwrite/removal.</li>
            <li>Aggregated or de-identified analytics that cannot be linked back to you.</li>
        </ul>

        <h2>Retention Timeline</h2>
        <ul>
            <li>Primary account deletion target: within <strong>7 business days</strong> after successful verification.</li>
            <li>Backup/log retention: up to <strong>90 days</strong> before final purge/rotation.</li>
        </ul>

        <div class="warning">
            Account deletion is permanent and cannot be undone after processing is completed.
        </div>

        <h2>Questions</h2>
        <p>
            Contact: <a href="mailto:picposeapp@gmail.com">picposeapp@gmail.com</a>
        </p>
    </article>
</main>
</body>
</html>
