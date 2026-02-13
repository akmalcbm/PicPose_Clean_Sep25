<?php
// services/NotificationService.php
declare(strict_types=1);

require_once __DIR__ . '/FcmV1Service.php';

class NotificationService {

    private mysqli $db;
    private FcmV1Service $fcm;

    public function __construct(mysqli $dbConnection) {
        $this->db = $dbConnection;
        $this->fcm = new FcmV1Service();
    }

    /**
     * Send notification immediately (ALL / TOPIC / SPECIFIC)
     * 🔒 Normalized response for worker safety
     */
    public function sendImmediately(int $notificationId, array $data): array {

        $notification = [
            'title' => $data['title'],
            'body'  => $data['message'],
            'image' => $data['image_url'] ?? null
        ];

        $fcmData = [
            'title'           => $data['title'],
            'message'         => $data['message'],
            'notification_id' => (string)$notificationId,
            'deep_link'       => $data['deep_link'] ?? '',
            'image_url'       => $data['image_url'] ?? '',
            'click_action'    => 'OPEN_APP',
            'timestamp'       => (string)time()
        ];

        $targetType  = $data['target_type'] ?? 'all';
        $targetValue = $data['target_value'] ?? '';

        try {

            switch ($targetType) {

                case 'all':
                    $rawResult = $this->fcm->sendToAll($fcmData, $notification);
                    break;

                case 'topic':
                    $rawResult = $this->fcm->sendToTopic($targetValue, $fcmData, $notification);
                    break;

                case 'specific':
                    $tokens = array_filter(array_map('trim', explode(',', $targetValue)));
                    $rawResult = $this->fcm->sendToDevices($tokens, $fcmData, $notification);

                    if (!empty($rawResult['invalid_tokens'])) {
                        $this->handleInvalidTokens($rawResult['invalid_tokens']);
                    }
                    break;

                default:
                    throw new Exception("Invalid target type: {$targetType}");
            }

            /* ---------- 🔥 NORMALIZE RESULT (MOST IMPORTANT FIX) ---------- */

            $success       = false;
            $successCount  = 0;
            $failureCount  = 0;
            $results       = [];
            $invalidTokens = [];

            // Case 1: Topic / All
            if (isset($rawResult['success'])) {
                $success      = (bool)$rawResult['success'];
                $successCount = $success ? 1 : 0;
                $failureCount = $success ? 0 : 1;
            }

            // Case 2: Specific devices
            if (isset($rawResult['success_count'])) {
                $success       = $rawResult['success_count'] > 0;
                $successCount  = (int)$rawResult['success_count'];
                $failureCount  = (int)($rawResult['failure_count'] ?? 0);
                $results       = $rawResult['results'] ?? [];
                $invalidTokens = $rawResult['invalid_tokens'] ?? [];
            }

            return [
                'success'        => $success,
                'success_count'  => $successCount,
                'failure_count'  => $failureCount,
                'results'        => $results,
                'invalid_tokens' => $invalidTokens
            ];

        } catch (Throwable $e) {
            error_log('[NotificationService] sendImmediately error: ' . $e->getMessage());
            return [
                'success' => false,
                'success_count' => 0,
                'failure_count' => 1,
                'error' => $e->getMessage()
            ];
        }
    }

    /**
     * Handle invalid FCM tokens
     */
    private function handleInvalidTokens(array $invalidTokens): void {

        foreach ($invalidTokens as $invalid) {

            $token  = $invalid['token'];
            $reason = $invalid['reason'] ?? 'NotRegistered';

            $stmt = $this->db->prepare("
                UPDATE device_tokens
                SET is_active = 0,
                    deactivation_reason = ?,
                    deactivated_at = NOW()
                WHERE fcm_token = ?
            ");
            $stmt->bind_param("ss", $reason, $token);
            $stmt->execute();
            $stmt->close();

            $this->logInvalidToken($token, $reason);
        }
    }

    /**
     * Log invalid tokens
     */
    private function logInvalidToken(string $token, string $reason): void {

        $stmt = $this->db->prepare("
            INSERT INTO invalid_tokens (fcm_token, reason, detected_at)
            VALUES (?, ?, NOW())
            ON DUPLICATE KEY UPDATE
                reason = VALUES(reason),
                detected_at = VALUES(detected_at)
        ");
        $stmt->bind_param("ss", $token, $reason);
        $stmt->execute();
        $stmt->close();
    }

    /**
     * Process queue safely
     */
    public function processQueue(int $limit = 50): array {

        try {

            $items = $this->getPendingNotifications($limit);

            if (empty($items)) {
                return ['success' => true, 'processed' => 0];
            }

            $processed = [];

            foreach ($items as $item) {

                $this->markAsProcessing($item['queue_id']);

                $sendResult = $this->sendImmediately(
                            (int)$item['notification_id'],
                            $item['data']
                        );
                        
                        // 🔒 SAFETY: ensure structure
                        if (!isset($sendResult['success'])) {
                            $sendResult = [
                                'success' => false,
                                'success_count' => 0,
                                'failure_count' => 1,
                                'error' => 'Unknown FCM failure'
                            ];
                        }


                $this->updateQueueResult(
                    (int)$item['queue_id'],
                    (int)$item['notification_id'],
                    $sendResult
                );

                $processed[] = [
                    'queue_id' => $item['queue_id'],
                    'success'  => $sendResult['success']
                ];
            }

            return [
                'success'   => true,
                'processed' => count($processed),
                'details'   => $processed
            ];

        } catch (Throwable $e) {
            error_log('[NotificationService] processQueue error: ' . $e->getMessage());
            return ['success' => false, 'error' => $e->getMessage()];
        }
    }

    /**
     * Fetch pending notifications
     */
    private function getPendingNotifications(int $limit): array {

        $stmt = $this->db->prepare("
            SELECT q.id AS queue_id, q.notification_id, q.data
            FROM notification_queue q
            JOIN push_notifications n ON n.id = q.notification_id
            WHERE q.status = 'pending'
              AND (q.scheduled_at IS NULL OR q.scheduled_at <= NOW())
              AND q.attempts < q.max_attempts
            ORDER BY n.priority ASC, q.created_at ASC
            LIMIT ?
        ");

        $stmt->bind_param("i", $limit);
        $stmt->execute();
        $res = $stmt->get_result();

        $rows = [];
        while ($row = $res->fetch_assoc()) {
            $row['data'] = json_decode($row['data'], true);
            $rows[] = $row;
        }

        $stmt->close();
        return $rows;
    }

    /**
     * Mark queue item as processing
     */
    private function markAsProcessing(int $queueId): void {

        $this->db->query("
            UPDATE notification_queue
            SET status = 'processing',
                processing_started = NOW(),
                attempts = attempts + 1
            WHERE id = {$queueId}
        ");
    }

    /**
 * Update queue + notification result (SAFE & FINAL)
 */
private function updateQueueResult(
    int $queueId,
    int $notificationId,
    array $sendResult
): void {

    $this->db->begin_transaction();

    try {

        // 🔒 SAFETY: force boolean
        $success = !empty($sendResult['success']);

        $queueStatus = $success ? 'completed' : 'failed';

        $json = json_encode($sendResult, JSON_UNESCAPED_UNICODE);

        $stmt = $this->db->prepare("
            UPDATE notification_queue
            SET status = ?,
                processing_ended = NOW(),
                result = ?
            WHERE id = ?
        ");
        $stmt->bind_param("ssi", $queueStatus, $json, $queueId);
        $stmt->execute();
        $stmt->close();

        // 🔥 ALWAYS update push_notifications (even on failure)
        $stmt2 = $this->db->prepare("
            UPDATE push_notifications
            SET status = ?,
                sent_at = IF(? = 1, NOW(), sent_at),
                success_count = ?,
                failure_count = ?
            WHERE id = ?
        ");

        $pnStatus = $success ? 'sent' : 'failed';
        $successCount = (int)($sendResult['success_count'] ?? 0);
        $failureCount = (int)($sendResult['failure_count'] ?? 1);

        $stmt2->bind_param(
            "siiii",
            $pnStatus,
            $success,
            $successCount,
            $failureCount,
            $notificationId
        );
        $stmt2->execute();
        $stmt2->close();

        $this->db->commit();

    } catch (Throwable $e) {
        $this->db->rollback();
        error_log('[NotificationService] updateQueueResult fatal: ' . $e->getMessage());
    }

    
  }

}
