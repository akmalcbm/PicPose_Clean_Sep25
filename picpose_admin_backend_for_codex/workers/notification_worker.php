<?php
// workers/notification_worker.php
declare(strict_types=1);

// Run as CLI only
if (php_sapi_name() !== 'cli') {
    die('Access denied');
}

require_once __DIR__ . '/../config.php';
require_once __DIR__ . '/../services/NotificationService.php';

class NotificationWorker {
    private NotificationService $service;
    private int $maxRuntime = 300; // 5 minutes
    private int $sleepTime = 5; // seconds between cycles
    private int $batchSize = 50;
    
    public function __construct() {
        global $conn;
        $this->service = new NotificationService($conn);
        
        // Handle signals
        pcntl_async_signals(true);
        pcntl_signal(SIGTERM, [$this, 'shutdown']);
        pcntl_signal(SIGINT, [$this, 'shutdown']);
    }
    
    public function run(): void {
        $startTime = time();
        $processedTotal = 0;
        
        echo "[" . date('Y-m-d H:i:s') . "] Notification worker started\n";
        
        while ((time() - $startTime) < $this->maxRuntime) {
            try {
                $result = $this->service->processQueue($this->batchSize);
                
                if ($result['success']) {
                    $processed = $result['processed'];
                    $processedTotal += $processed;
                    
                    if ($processed > 0) {
                        echo "[" . date('Y-m-d H:i:s') . "] Processed {$processed} notifications\n";
                    }
                } else {
                    echo "[" . date('Y-m-d H:i:s') . "] Error: " . $result['error'] . "\n";
                }
                
                // Sleep if no work
                $processed = $result['processed'] ?? 0;

                if ($processed === 0) {
                    sleep($this->sleepTime);
                }
                
            } catch (Exception $e) {
                echo "[" . date('Y-m-d H:i:s') . "] Exception: " . $e->getMessage() . "\n";
                sleep($this->sleepTime);
            }
        }
        
        echo "[" . date('Y-m-d H:i:s') . "] Worker finished. Total processed: {$processedTotal}\n";
    }
    
    public function shutdown(int $signal): void {
        echo "[" . date('Y-m-d H:i:s') . "] Received signal {$signal}, shutting down...\n";
        exit(0);
    }
}

// Run worker
$worker = new NotificationWorker();
$worker->run();