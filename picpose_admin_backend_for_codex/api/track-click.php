<?php
// api/track-click.php
declare(strict_types=1);
header('Content-Type: application/json');

require_once __DIR__ . '/../config.php';

// CORS headers
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: POST, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type, Authorization');

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(200);
    exit();
}

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    http_response_code(405);
    echo json_encode(['error' => 'Method not allowed']);
    exit();
}

$input = json_decode(file_get_contents('php://input'), true);

if (!$input || !isset($input['notification_id'])) {
    http_response_code(400);
    echo json_encode(['error' => 'Notification ID required']);
    exit();
}

$notificationId = (int)$input['notification_id'];
$deviceToken = $input['device_token'] ?? null;
$userId = $input['user_id'] ?? null;
$deepLink = $input['deep_link'] ?? null;
$screenOpened = $input['screen_opened'] ?? null;

try {
    // Log click
    $stmt = $conn->prepare("
        INSERT INTO notification_clicks 
        (notification_id, device_token, user_id, deep_link, screen_opened, clicked_at)
        VALUES (?, ?, ?, ?, ?, NOW())
    ");
    
    $stmt->bind_param(
        "isiss",
        $notificationId,
        $deviceToken,
        $userId,
        $deepLink,
        $screenOpened
    );
    
    if ($stmt->execute()) {
        // Update notification click count
        $conn->query("
            UPDATE push_notifications 
            SET click_count = click_count + 1 
            WHERE id = {$notificationId}
        ");
        
        echo json_encode([
            'success' => true,
            'message' => 'Click tracked successfully'
        ]);
    } else {
        http_response_code(500);
        echo json_encode(['error' => 'Failed to track click']);
    }
    
    $stmt->close();
    
} catch (Exception $e) {
    http_response_code(500);
    echo json_encode(['error' => 'Server error: ' . $e->getMessage()]);
}

$conn->close();