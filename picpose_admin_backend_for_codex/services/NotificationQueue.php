<?php
// services/NotificationQueue.php
declare(strict_types=1);

class NotificationQueue {
    private mysqli $db;

    public function __construct(mysqli $db) {
        $this->db = $db;
    }

    /**
     * Push notification into queue
     */
    public function enqueue(int $notificationId, array $data): void {
        $stmt = $this->db->prepare("
            INSERT INTO notification_queue (notification_id, data)
            VALUES (?, ?)
        ");

        $jsonData = json_encode($data, JSON_THROW_ON_ERROR);
        $stmt->bind_param("is", $notificationId, $jsonData);
        $stmt->execute();
        $stmt->close();
    }
}
