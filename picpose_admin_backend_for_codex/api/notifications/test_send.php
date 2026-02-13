<?php
declare(strict_types=1);

session_start();
header('Content-Type: application/json');

require_once __DIR__ . '/../../config.php';
require_once __DIR__ . '/../../services/PushCampaignService.php';

if (!isset($_SESSION['admin'])) {
    http_response_code(401);
    echo json_encode(['success' => false, 'error' => 'Unauthorized']);
    exit();
}

if ($_SERVER['REQUEST_METHOD'] !== 'GET') {
    http_response_code(405);
    echo json_encode(['success' => false, 'error' => 'Method not allowed']);
    exit();
}

$token = trim((string)($_GET['token'] ?? ''));
if ($token === '') {
    http_response_code(400);
    echo json_encode(['success' => false, 'error' => 'token is required']);
    exit();
}

$service = new PushCampaignService($conn);

try {
    $createdBy = (int)($_SESSION['admin']['id'] ?? 0);
    $result = $service->sendTestToToken($token, [
        'title' => 'PicPose Test Notification',
        'body' => 'Known-good notification path check',
        'deep_link' => 'app://home',
        'type' => 'general',
    ], $createdBy);

    echo json_encode([
        'success' => (bool)$result['success'],
        'campaign_id' => $result['campaign_id'] ?? null,
        'success_count' => $result['success_count'] ?? 0,
        'failure_count' => $result['failure_count'] ?? 0,
        'results' => $result['results'] ?? [],
    ]);
} catch (Throwable $e) {
    http_response_code(500);
    echo json_encode(['success' => false, 'error' => $e->getMessage()]);
}
