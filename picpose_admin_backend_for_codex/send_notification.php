<?php
declare(strict_types=1);

session_start();

require __DIR__ . '/config.php';
require __DIR__ . '/services/PushCampaignService.php';

if (!isset($_SESSION['admin'])) {
    header('Location: login.php');
    exit();
}

if (empty($_SESSION['csrf_token'])) {
    $_SESSION['csrf_token'] = bin2hex(random_bytes(32));
}
$csrfToken = $_SESSION['csrf_token'];

$service = new PushCampaignService($conn);
$message = '';
$messageType = 'info';
$sendReport = null;

$defaults = [
    'title' => 'PicPose Update',
    'body' => '',
    'image_url' => '',
    'deep_link' => 'app://home',
    'type' => 'general',
    'target_type' => 'all',
    'topic_name' => 'all',
    'target_token' => '',
];

$form = $defaults;

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $postedCsrf = $_POST['csrf_token'] ?? '';
    if (!hash_equals($csrfToken, (string)$postedCsrf)) {
        $message = 'Invalid CSRF token.';
        $messageType = 'danger';
    } else {
        $action = $_POST['action'] ?? 'send_campaign';

        $form['title'] = trim((string)($_POST['title'] ?? ''));
        $form['body'] = trim((string)($_POST['body'] ?? ''));
        $form['image_url'] = trim((string)($_POST['image_url'] ?? ''));
        $form['deep_link'] = trim((string)($_POST['deep_link'] ?? ''));
        $form['type'] = trim((string)($_POST['type'] ?? 'general'));
        $form['target_type'] = trim((string)($_POST['target_type'] ?? 'all'));
        $form['topic_name'] = trim((string)($_POST['topic_name'] ?? 'all'));
        $form['target_token'] = trim((string)($_POST['target_token'] ?? ''));

        $createdBy = (int)($_SESSION['admin']['id'] ?? 0);

        try {
            if ($action === 'send_test') {
                $token = trim((string)($_POST['test_token'] ?? ''));
                if ($token === '') {
                    throw new InvalidArgumentException('Test token is required');
                }

                $sendReport = $service->sendTestToToken($token, [
                    'title' => $form['title'] !== '' ? $form['title'] : 'PicPose Test Notification',
                    'body' => $form['body'] !== '' ? $form['body'] : 'If you got this, your notification pipeline is healthy.',
                    'image_url' => $form['image_url'],
                    'deep_link' => $form['deep_link'] !== '' ? $form['deep_link'] : 'app://home',
                    'type' => $form['type'],
                ], $createdBy);

                $message = 'Test notification sent.';
                $messageType = $sendReport['success'] ? 'success' : 'warning';
            } else {
                $campaignId = $service->createCampaign([
                    'title' => $form['title'],
                    'body' => $form['body'],
                    'image_url' => $form['image_url'],
                    'deep_link' => $form['deep_link'],
                    'target_type' => $form['target_type'],
                    'topic_name' => $form['target_type'] === 'topic' ? $form['topic_name'] : null,
                    'status' => 'draft',
                    'scheduled_at' => null,
                ], $createdBy);

                $sendReport = $service->sendCampaignNow($campaignId, [
                    'title' => $form['title'],
                    'body' => $form['body'],
                    'image_url' => $form['image_url'],
                    'deep_link' => $form['deep_link'],
                    'type' => $form['type'],
                    'target_type' => $form['target_type'],
                    'topic_name' => $form['topic_name'],
                    'target_token' => $form['target_token'],
                ]);

                $message = 'Campaign processed.';
                $messageType = $sendReport['success'] ? 'success' : 'warning';
            }
        } catch (Throwable $e) {
            $message = $e->getMessage();
            $messageType = 'danger';
            error_log('[send_notification.php] ' . $e->getMessage());
        }
    }
}

$recentCampaigns = [];
try {
    $recentCampaigns = $service->fetchRecentCampaigns(10);
} catch (Throwable $e) {
    error_log('[send_notification.php] fetchRecentCampaigns failed: ' . $e->getMessage());
}

include __DIR__ . '/includes/header.php';
?>

<div class="container-fluid">
    <div class="d-flex flex-wrap justify-content-between align-items-center mb-3">
        <div>
            <h3 class="mb-1">Push Notifications</h3>
            <div class="text-muted">Enterprise FCM HTTP v1 campaign sender</div>
        </div>
    </div>

    <?php if ($message !== ''): ?>
        <div class="alert alert-<?= htmlspecialchars($messageType) ?>"><?= htmlspecialchars($message) ?></div>
    <?php endif; ?>

    <div class="row g-4">
        <div class="col-lg-8">
            <div class="card shadow-sm border-0">
                <div class="card-body">
                    <form method="POST" id="campaignForm">
                        <input type="hidden" name="csrf_token" value="<?= htmlspecialchars($csrfToken) ?>">
                        <input type="hidden" name="action" value="send_campaign">

                        <div class="row g-3">
                            <div class="col-md-6">
                                <label class="form-label">Title</label>
                                <input class="form-control" name="title" maxlength="255" required value="<?= htmlspecialchars($form['title']) ?>">
                            </div>
                            <div class="col-md-6">
                                <label class="form-label">Notification Type</label>
                                <select class="form-select" name="type" id="notifType">
                                    <option value="general" <?= $form['type'] === 'general' ? 'selected' : '' ?>>General</option>
                                    <option value="guide" <?= $form['type'] === 'guide' ? 'selected' : '' ?>>Guide</option>
                                    <option value="prompt" <?= $form['type'] === 'prompt' ? 'selected' : '' ?>>Prompt</option>
                                </select>
                            </div>
                            <div class="col-12">
                                <label class="form-label">Body</label>
                                <textarea class="form-control" rows="3" name="body" maxlength="2000" required><?= htmlspecialchars($form['body']) ?></textarea>
                            </div>
                            <div class="col-md-6">
                                <label class="form-label">Image URL (optional)</label>
                                <input class="form-control" name="image_url" placeholder="https://..." value="<?= htmlspecialchars($form['image_url']) ?>">
                            </div>
                            <div class="col-md-6">
                                <label class="form-label">Deep Link</label>
                                <input class="form-control" name="deep_link" id="deepLinkInput" list="deepLinkPresets" placeholder="app://home or app://guides/123" value="<?= htmlspecialchars($form['deep_link']) ?>" required>
                                <datalist id="deepLinkPresets">
                                    <option value="app://home">Home Screen</option>
                                    <option value="app://prompts/123">Prompt Detail (replace 123)</option>
                                    <option value="app://guides/123">Guide Detail (replace 123)</option>
                                    <option value="app://category/Portrait">Prompt Category (replace Portrait)</option>
                                </datalist>
                                <div class="form-text">Use only in-app links supported by Android app navigation.</div>
                            </div>

                            <div class="col-12">
                                <label class="form-label mb-1">Deep Link Quick Pick</label>
                                <div class="d-flex gap-2 flex-wrap">
                                    <button type="button" class="btn btn-sm btn-outline-secondary deep-link-preset" data-link="app://home">Home</button>
                                    <button type="button" class="btn btn-sm btn-outline-secondary deep-link-preset" data-link="app://prompts/123">Prompt Detail</button>
                                    <button type="button" class="btn btn-sm btn-outline-secondary deep-link-preset" data-link="app://guides/123">Guide Detail</button>
                                    <button type="button" class="btn btn-sm btn-outline-secondary deep-link-preset" data-link="app://category/Portrait">Category</button>
                                </div>
                            </div>

                            <div class="col-md-4">
                                <label class="form-label">Target</label>
                                <select class="form-select" name="target_type" id="targetType">
                                    <option value="all" <?= $form['target_type'] === 'all' ? 'selected' : '' ?>>All Users (topic: all)</option>
                                    <option value="topic" <?= $form['target_type'] === 'topic' ? 'selected' : '' ?>>Specific Topic</option>
                                    <option value="token" <?= $form['target_type'] === 'token' ? 'selected' : '' ?>>Single Token</option>
                                </select>
                            </div>

                            <div class="col-md-4" id="topicField">
                                <label class="form-label">Topic Name</label>
                                <input class="form-control" name="topic_name" placeholder="android" value="<?= htmlspecialchars($form['topic_name']) ?>">
                            </div>

                            <div class="col-md-8" id="tokenField">
                                <label class="form-label">Device Token</label>
                                <textarea class="form-control" rows="2" name="target_token" placeholder="Paste FCM token for token target"><?= htmlspecialchars($form['target_token']) ?></textarea>
                            </div>

                            <div class="col-12 d-flex gap-2 flex-wrap">
                                <button type="submit" class="btn btn-primary">Send Campaign Now</button>
                            </div>
                        </div>
                    </form>
                </div>
            </div>

            <div class="card shadow-sm border-0 mt-4">
                <div class="card-body">
                    <h5 class="mb-3">Test Send Tool</h5>
                    <form method="POST">
                        <input type="hidden" name="csrf_token" value="<?= htmlspecialchars($csrfToken) ?>">
                        <input type="hidden" name="action" value="send_test">
                        <div class="row g-3 align-items-end">
                            <div class="col-md-9">
                                <label class="form-label">Single Token</label>
                                <textarea class="form-control" rows="2" name="test_token" placeholder="Paste token for known-good delivery path"></textarea>
                            </div>
                            <div class="col-md-3">
                                <button type="submit" class="btn btn-outline-primary w-100">Send Test</button>
                            </div>
                        </div>
                    </form>
                </div>
            </div>
        </div>

        <div class="col-lg-4">
            <div class="card shadow-sm border-0 mb-4">
                <div class="card-body">
                    <h5 class="mb-3">Payload Preview</h5>
                    <pre id="payloadPreview" class="bg-dark text-light p-3 rounded" style="font-size:12px; white-space:pre-wrap;"></pre>
                </div>
            </div>

            <?php if ($sendReport !== null): ?>
                <div class="card shadow-sm border-0 mb-4">
                    <div class="card-body">
                        <h5 class="mb-3">Last Send Report</h5>
                        <div>Success: <strong><?= (int)($sendReport['success_count'] ?? 0) ?></strong></div>
                        <div>Failed: <strong><?= (int)($sendReport['failure_count'] ?? 0) ?></strong></div>
                        <hr>
                        <div style="max-height: 260px; overflow:auto;">
                            <pre class="small"><?= htmlspecialchars((string)json_encode($sendReport, JSON_PRETTY_PRINT | JSON_UNESCAPED_SLASHES)) ?></pre>
                        </div>
                    </div>
                </div>
            <?php endif; ?>
        </div>
    </div>

    <div class="card shadow-sm border-0 mt-4">
        <div class="card-body">
            <h5 class="mb-3">Recent Campaigns</h5>
            <div class="table-responsive">
                <table class="table table-sm align-middle">
                    <thead>
                    <tr>
                        <th>ID</th>
                        <th>Title</th>
                        <th>Body</th>
                        <th>Target</th>
                        <th>Status</th>
                        <th>Success</th>
                        <th>Fail</th>
                        <th>Created</th>
                    </tr>
                    </thead>
                    <tbody>
                    <?php foreach ($recentCampaigns as $row): ?>
                        <tr>
                            <td><?= (int)$row['id'] ?></td>
                            <td><?= htmlspecialchars((string)$row['title']) ?></td>
                            <td>
                                <?php
                                $bodyText = (string)($row['body'] ?? '');
                                $shortBody = strlen($bodyText) > 70 ? substr($bodyText, 0, 67) . '...' : $bodyText;
                                ?>
                                <?= htmlspecialchars($shortBody) ?>
                            </td>
                            <td>
                                <?= htmlspecialchars((string)$row['target_type']) ?>
                                <?php if (!empty($row['topic_name'])): ?>
                                    <small class="text-muted">(<?= htmlspecialchars((string)$row['topic_name']) ?>)</small>
                                <?php endif; ?>
                            </td>
                            <td><?= htmlspecialchars((string)$row['status']) ?></td>
                            <td><?= (int)$row['success_count'] ?></td>
                            <td><?= (int)$row['failure_count'] ?></td>
                            <td><?= htmlspecialchars((string)$row['created_at']) ?></td>
                        </tr>
                    <?php endforeach; ?>
                    </tbody>
                </table>
            </div>
        </div>
    </div>
</div>

<script>
(function () {
    var form = document.getElementById('campaignForm');
    if (!form) return;

    var targetType = document.getElementById('targetType');
    var topicField = document.getElementById('topicField');
    var tokenField = document.getElementById('tokenField');
    var payloadPreview = document.getElementById('payloadPreview');
    var deepLinkInput = document.getElementById('deepLinkInput');

    function toggleTargetFields() {
        var value = targetType.value;
        topicField.style.display = value === 'topic' ? 'block' : 'none';
        tokenField.style.display = value === 'token' ? 'block' : 'none';
    }

    function updatePreview() {
        var data = {
            title: form.title.value,
            body: form.body.value,
            image_url: form.image_url.value,
            deep_link: form.deep_link.value,
            type: form.type.value,
            target_type: form.target_type.value,
            topic_name: form.topic_name.value,
            target_token: form.target_token.value ? '***redacted***' : ''
        };
        payloadPreview.textContent = JSON.stringify(data, null, 2);
    }

    form.addEventListener('input', updatePreview);

    document.querySelectorAll('.deep-link-preset').forEach(function (btn) {
        btn.addEventListener('click', function () {
            deepLinkInput.value = btn.getAttribute('data-link') || 'app://home';
            updatePreview();
        });
    });
    targetType.addEventListener('change', function () {
        toggleTargetFields();
        updatePreview();
    });

    toggleTargetFields();
    updatePreview();
})();
</script>

<?php include __DIR__ . '/includes/footer.php'; ?>
