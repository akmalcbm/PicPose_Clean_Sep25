<?php
declare(strict_types=1);

class FcmV1Service {

    private string $projectId;
    private string $serviceAccountPath;
    private string $accessToken = '';
    private int $tokenExpiry = 0;
    private array $serviceAccount;

    public function __construct() {
        if (!defined('FIREBASE_PROJECT_ID') || empty(FIREBASE_PROJECT_ID)) {
            throw new RuntimeException('FIREBASE_PROJECT_ID not defined in config.php');
        }

        $this->projectId = FIREBASE_PROJECT_ID;
        $this->serviceAccountPath = $this->resolveServiceAccountPath();

        if (!is_file($this->serviceAccountPath)) {
            throw new RuntimeException('Firebase service account file not found at: ' . $this->serviceAccountPath);
        }

        $json = file_get_contents($this->serviceAccountPath);
        $data = json_decode((string)$json, true);

        if (!$data || !isset($data['private_key'], $data['client_email'])) {
            throw new RuntimeException(
                'Invalid Firebase service account JSON. Use a SERVICE ACCOUNT key file.'
            );
        }

        $this->serviceAccount = $data;
    }

    private function resolveServiceAccountPath(): string {
        $pathFromEnv = getenv('FCM_SERVICE_ACCOUNT_PATH') ?: '';
        if ($pathFromEnv !== '') {
            return $pathFromEnv;
        }

        if (defined('FCM_SERVICE_ACCOUNT_PATH') && is_string(FCM_SERVICE_ACCOUNT_PATH) && FCM_SERVICE_ACCOUNT_PATH !== '') {
            return FCM_SERVICE_ACCOUNT_PATH;
        }

        return dirname(__DIR__) . '/secure/firebase/service-account.json';
    }

    private function getAccessToken(): string {
        if ($this->accessToken !== '' && time() < ($this->tokenExpiry - 300)) {
            return $this->accessToken;
        }

        $now = time();

        $payload = [
            'iss' => $this->serviceAccount['client_email'],
            'scope' => 'https://www.googleapis.com/auth/firebase.messaging',
            'aud' => 'https://oauth2.googleapis.com/token',
            'iat' => $now,
            'exp' => $now + 3600,
        ];

        $jwt = $this->generateJWT(
            ['alg' => 'RS256', 'typ' => 'JWT'],
            $payload,
            $this->serviceAccount['private_key']
        );

        $ch = curl_init('https://oauth2.googleapis.com/token');
        curl_setopt_array($ch, [
            CURLOPT_POST => true,
            CURLOPT_RETURNTRANSFER => true,
            CURLOPT_TIMEOUT => 25,
            CURLOPT_CONNECTTIMEOUT => 10,
            CURLOPT_POSTFIELDS => http_build_query([
                'grant_type' => 'urn:ietf:params:oauth:grant-type:jwt-bearer',
                'assertion' => $jwt,
            ]),
            CURLOPT_HTTPHEADER => ['Content-Type: application/x-www-form-urlencoded'],
        ]);

        $response = curl_exec($ch);
        $curlError = curl_error($ch);
        $code = (int)curl_getinfo($ch, CURLINFO_HTTP_CODE);
        curl_close($ch);

        if ($response === false) {
            throw new RuntimeException('OAuth token fetch cURL failure: ' . $curlError);
        }

        if ($code !== 200) {
            throw new RuntimeException('OAuth token fetch failed (' . $code . '): ' . $response);
        }

        $data = json_decode($response, true);

        if (!is_array($data) || empty($data['access_token']) || empty($data['expires_in'])) {
            throw new RuntimeException('OAuth response missing required fields');
        }

        $this->accessToken = (string)$data['access_token'];
        $this->tokenExpiry = $now + (int)$data['expires_in'];

        return $this->accessToken;
    }

    private function generateJWT(array $header, array $payload, string $privateKey): string {
        $encode = static function (array $value): string {
            return rtrim(strtr(base64_encode((string)json_encode($value, JSON_UNESCAPED_SLASHES)), '+/', '-_'), '=');
        };

        $data = $encode($header) . '.' . $encode($payload);

        $signed = openssl_sign($data, $signature, $privateKey, OPENSSL_ALGO_SHA256);
        if (!$signed) {
            throw new RuntimeException('Failed to sign JWT for Google OAuth');
        }

        return $data . '.' . rtrim(strtr(base64_encode((string)$signature), '+/', '-_'), '=');
    }

    public function sendToAll(array $data, ?array $notification = null): array {
        return $this->sendToTopic('all', $data, $notification);
    }

    public function sendToTopic(string $topic, array $data, ?array $notification = null): array {
        if ($topic === '') {
            return [
                'success' => false,
                'error_code' => 'INVALID_TOPIC',
                'error_message' => 'Topic cannot be empty',
            ];
        }

        $message = $this->buildMessagePayload([
            'topic' => $topic,
            'data' => $data,
            'notification' => $notification,
        ]);

        $result = $this->sendRawMessage($message);

        return [
            'success' => $result['success'],
            'message_id' => $result['message_id'] ?? null,
            'error_code' => $result['error_code'] ?? null,
            'error_message' => $result['error_message'] ?? null,
            'response' => $result['response'] ?? null,
        ];
    }

    public function sendToToken(string $token, array $data, ?array $notification = null, int $maxRetries = 2): array {
        if ($token === '') {
            return [
                'success' => false,
                'token' => $token,
                'error_code' => 'INVALID_TOKEN',
                'error_message' => 'Token cannot be empty',
            ];
        }

        $message = $this->buildMessagePayload([
            'token' => $token,
            'data' => $data,
            'notification' => $notification,
        ]);

        return $this->sendWithRetry($message, $token, $maxRetries);
    }

    public function sendToDevices(array $tokens, array $data, ?array $notification = null): array {
        return $this->sendToTokens($tokens, $data, $notification);
    }

    public function sendToTokens(array $tokens, array $data, ?array $notification = null, int $maxRetries = 2): array {
        $results = [];
        $invalidTokens = [];
        $successCount = 0;
        $failureCount = 0;

        $uniqueTokens = array_values(array_unique(array_filter(array_map('trim', $tokens), static function ($token) {
            return $token !== '';
        })));

        foreach ($uniqueTokens as $token) {
            $result = $this->sendToToken($token, $data, $notification, $maxRetries);
            $results[] = $result;

            if (!empty($result['success'])) {
                $successCount++;
                continue;
            }

            $failureCount++;
            $errorCode = (string)($result['error_code'] ?? 'UNKNOWN');

            if (in_array($errorCode, ['UNREGISTERED', 'NOT_FOUND', 'INVALID_ARGUMENT'], true)) {
                $invalidTokens[] = [
                    'token' => $token,
                    'reason' => $errorCode,
                ];
            }
        }

        return [
            'success' => $failureCount === 0,
            'success_count' => $successCount,
            'failure_count' => $failureCount,
            'results' => $results,
            'invalid_tokens' => $invalidTokens,
        ];
    }

    private function buildMessagePayload(array $target): array {
        $data = isset($target['data']) && is_array($target['data']) ? $target['data'] : [];
        $notification = isset($target['notification']) && is_array($target['notification']) ? $target['notification'] : null;

        $message = [
            'android' => [
                'priority' => 'HIGH',
                'notification' => [
                    'channel_id' => (string)($data['channel_id'] ?? 'picpose_general'),
                ],
            ],
            'apns' => [
                'headers' => ['apns-priority' => '10'],
            ],
            'data' => array_map(static function ($value): string {
                if (is_bool($value)) {
                    return $value ? '1' : '0';
                }
                if ($value === null) {
                    return '';
                }
                return (string)$value;
            }, $data),
        ];

        if (!empty($target['token'])) {
            $message['token'] = (string)$target['token'];
        }

        if (!empty($target['topic'])) {
            $message['topic'] = (string)$target['topic'];
        }

        if ($notification !== null) {
            $clean = [
                'title' => (string)($notification['title'] ?? ''),
                'body' => (string)($notification['body'] ?? ''),
            ];
            if (!empty($notification['image'])) {
                $clean['image'] = (string)$notification['image'];
            }
            $message['notification'] = $clean;

            if (!empty($notification['image'])) {
                $message['android']['notification']['image'] = (string)$notification['image'];
            }
        }

        return $message;
    }

    private function sendWithRetry(array $message, string $token, int $maxRetries): array {
        $attempt = 0;
        $lastResult = [];

        while ($attempt <= $maxRetries) {
            $attempt++;
            $lastResult = $this->sendRawMessage($message);

            if (!empty($lastResult['success'])) {
                return [
                    'success' => true,
                    'token' => $token,
                    'attempt' => $attempt,
                    'message_id' => $lastResult['message_id'] ?? null,
                    'error_code' => null,
                    'error_message' => null,
                    'response' => $lastResult['response'] ?? null,
                ];
            }

            $errorCode = (string)($lastResult['error_code'] ?? 'UNKNOWN');
            if (!$this->isRetryableError($errorCode) || $attempt > $maxRetries) {
                break;
            }

            usleep((int)(250000 * $attempt));
        }

        return [
            'success' => false,
            'token' => $token,
            'attempt' => $attempt,
            'message_id' => null,
            'error_code' => $lastResult['error_code'] ?? 'UNKNOWN',
            'error_message' => $lastResult['error_message'] ?? 'FCM send failed',
            'response' => $lastResult['response'] ?? null,
        ];
    }

    private function sendRawMessage(array $message): array {
        $url = 'https://fcm.googleapis.com/v1/projects/' . $this->projectId . '/messages:send';

        $payload = json_encode(['message' => $message], JSON_UNESCAPED_SLASHES);
        if ($payload === false) {
            return [
                'success' => false,
                'error_code' => 'JSON_ENCODE_FAILED',
                'error_message' => 'Unable to encode FCM payload',
            ];
        }

        $ch = curl_init($url);
        curl_setopt_array($ch, [
            CURLOPT_POST => true,
            CURLOPT_RETURNTRANSFER => true,
            CURLOPT_CONNECTTIMEOUT => 10,
            CURLOPT_TIMEOUT => 25,
            CURLOPT_HTTPHEADER => [
                'Authorization: Bearer ' . $this->getAccessToken(),
                'Content-Type: application/json',
            ],
            CURLOPT_POSTFIELDS => $payload,
        ]);

        $responseBody = curl_exec($ch);
        $curlError = curl_error($ch);
        $httpCode = (int)curl_getinfo($ch, CURLINFO_HTTP_CODE);
        curl_close($ch);

        if ($responseBody === false) {
            return [
                'success' => false,
                'error_code' => 'CURL_ERROR',
                'error_message' => $curlError !== '' ? $curlError : 'Unknown cURL failure',
            ];
        }

        $decoded = json_decode($responseBody, true);

        if ($httpCode >= 200 && $httpCode < 300) {
            return [
                'success' => true,
                'message_id' => is_array($decoded) ? ($decoded['name'] ?? null) : null,
                'response' => $decoded,
            ];
        }

        $parsedError = $this->extractFcmError($decoded, $httpCode, $responseBody);

        return [
            'success' => false,
            'error_code' => $parsedError['error_code'],
            'error_message' => $parsedError['error_message'],
            'response' => $decoded,
        ];
    }

    private function extractFcmError($decoded, int $httpCode, string $rawBody): array {
        $errorCode = 'HTTP_' . $httpCode;
        $errorMessage = 'FCM request failed with status ' . $httpCode;

        if (is_array($decoded) && isset($decoded['error']) && is_array($decoded['error'])) {
            $errorBlock = $decoded['error'];

            if (!empty($errorBlock['message'])) {
                $errorMessage = (string)$errorBlock['message'];
            }

            if (!empty($errorBlock['status'])) {
                $errorCode = (string)$errorBlock['status'];
            }

            if (!empty($errorBlock['details']) && is_array($errorBlock['details'])) {
                foreach ($errorBlock['details'] as $detail) {
                    if (!is_array($detail)) {
                        continue;
                    }

                    if (!empty($detail['errorCode'])) {
                        $errorCode = (string)$detail['errorCode'];
                        break;
                    }
                }
            }
        } elseif ($rawBody !== '') {
            $errorMessage = $rawBody;
        }

        return [
            'error_code' => strtoupper($errorCode),
            'error_message' => $errorMessage,
        ];
    }

    private function isRetryableError(string $errorCode): bool {
        return in_array(
            strtoupper($errorCode),
            ['UNAVAILABLE', 'INTERNAL', 'DEADLINE_EXCEEDED', 'RESOURCE_EXHAUSTED', 'HTTP_429', 'HTTP_500', 'HTTP_503'],
            true
        );
    }
}
