<?php
// workers/notification_analytics.php
declare(strict_types=1);

if (php_sapi_name() !== 'cli') {
    die('Access denied');
}

require_once __DIR__ . '/../config.php';

class NotificationAnalytics {
    private mysqli $db;
    
    public function __construct(mysqli $db) {
        $this->db = $db;
    }
    
    public function generateWeeklyReport(): void {
        $startDate = date('Y-m-d', strtotime('-7 days'));
        $endDate = date('Y-m-d');
        
        // Get stats
        $stats = $this->getNotificationStats($startDate, $endDate);
        
        echo "=== Weekly Notification Report ({$startDate} to {$endDate}) ===\n";
        echo "Total Sent: {$stats['total_sent']}\n";
        echo "Success Rate: {$stats['success_rate']}%\n";
        echo "Average Click Rate: {$stats['click_rate']}%\n";
        echo "Top Performing Topics: " . implode(', ', $stats['top_topics']) . "\n";
        
        // Log to database
        $this->saveAnalyticsReport($stats, $startDate, $endDate);
    }
    
    private function getNotificationStats(string $startDate, string $endDate): array {
        $stmt = $this->db->prepare("
            SELECT 
                COUNT(*) as total_sent,
                AVG(success_count/(success_count+failure_count)*100) as success_rate,
                AVG(click_count/(success_count)*100) as click_rate
            FROM push_notifications 
            WHERE sent_at BETWEEN ? AND ?
        ");
        
        $stmt->bind_param("ss", $startDate, $endDate . ' 23:59:59');
        $stmt->execute();
        $result = $stmt->get_result()->fetch_assoc();
        $stmt->close();
        
        return [
            'total_sent' => $result['total_sent'] ?? 0,
            'success_rate' => round($result['success_rate'] ?? 0, 2),
            'click_rate' => round($result['click_rate'] ?? 0, 2),
            'top_topics' => $this->getTopTopics($startDate, $endDate)
        ];
    }
    
    private function getTopTopics(string $startDate, string $endDate): array {
        $result = $this->db->query("
            SELECT target_value, COUNT(*) as count
            FROM push_notifications 
            WHERE target_type = 'topic' 
              AND sent_at BETWEEN '{$startDate}' AND '{$endDate} 23:59:59'
            GROUP BY target_value 
            ORDER BY count DESC 
            LIMIT 5
        ");
        
        $topics = [];
        while ($row = $result->fetch_assoc()) {
            $topics[] = "{$row['target_value']} ({$row['count']})";
        }
        
        return $topics;
    }
    
    private function saveAnalyticsReport(array $stats, string $startDate, string $endDate): void {
        $stmt = $this->db->prepare("
            INSERT INTO analytics_reports 
            (report_type, period_start, period_end, data, generated_at)
            VALUES ('weekly_notifications', ?, ?, ?, NOW())
        ");
        
        $data = json_encode($stats);
        $stmt->bind_param("sss", $startDate, $endDate, $data);
        $stmt->execute();
        $stmt->close();
    }
}

// Run analytics
$analytics = new NotificationAnalytics($conn);
$analytics->generateWeeklyReport();

$conn->close();