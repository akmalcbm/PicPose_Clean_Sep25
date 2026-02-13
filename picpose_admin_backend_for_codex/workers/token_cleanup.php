<?php
// workers/token_cleanup.php
declare(strict_types=1);

if (php_sapi_name() !== 'cli') {
    die('Access denied');
}

require_once __DIR__ . '/../config.php';

class TokenCleanup {
    private mysqli $db;
    
    public function __construct(mysqli $db) {
        $this->db = $db;
    }
    
    public function cleanupInactiveTokens(int $daysInactive = 30): int {
        $cutoffDate = date('Y-m-d H:i:s', strtotime("-{$daysInactive} days"));
        
        $stmt = $this->db->prepare("
            UPDATE device_tokens 
            SET is_active = 0 
            WHERE last_active < ? AND is_active = 1
        ");
        $stmt->bind_param("s", $cutoffDate);
        $stmt->execute();
        $affected = $stmt->affected_rows;
        $stmt->close();
        
        return $affected;
    }
    
    public function removeDuplicateTokens(): int {
        // Keep the most recent duplicate tokens
        $this->db->query("
            CREATE TEMPORARY TABLE tokens_to_keep AS
            SELECT MAX(id) as id 
            FROM device_tokens 
            WHERE is_active = 1 
            GROUP BY fcm_token
        ");
        
        $this->db->query("
            UPDATE device_tokens 
            SET is_active = 0 
            WHERE is_active = 1 
              AND id NOT IN (SELECT id FROM tokens_to_keep)
        ");
        
        $affected = $this->db->affected_rows;
        
        $this->db->query("DROP TEMPORARY TABLE tokens_to_keep");
        
        return $affected;
    }
}

// Run cleanup
$cleanup = new TokenCleanup($conn);
$inactive = $cleanup->cleanupInactiveTokens();
$duplicates = $cleanup->removeDuplicateTokens();

echo "[" . date('Y-m-d H:i:s') . "] Cleanup completed:\n";
echo "  - Deactivated {$inactive} inactive tokens\n";
echo "  - Removed {$duplicates} duplicate tokens\n";

$conn->close();