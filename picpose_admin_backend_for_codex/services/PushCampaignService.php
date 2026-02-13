<?php
declare(strict_types=1);

require_once __DIR__ . '/FcmV1Service.php';

class PushCampaignService {

    private mysqli $db;
    private FcmV1Service $fcm;

    public function __construct(mysqli $db) {
        $this->db = $db;
        $this->fcm = new FcmV1Service();
    }

    public function createCampaign(array $input, int $createdBy): int {
        $stmt = $this->db->prepare(
            'INSERT INTO notification_campaigns
                (title, body, image_url, deep_link, target_type, topic_name, status, scheduled_at, created_by)
             VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)'
        );

        $title = trim((string)($input['title'] ?? ''));
        $body = trim((string)($input['body'] ?? ''));
        $imageUrl = $this->normalizeNullableString($input['image_url'] ?? null);
        $deepLink = $this->normalizeNullableString($input['deep_link'] ?? null);
        $targetType = (string)($input['target_type'] ?? 'all');
        $topicName = $this->normalizeNullableString($input['topic_name'] ?? null);
        $status = (string)($input['status'] ?? 'draft');
        $scheduledAt = $this->normalizeNullableString($input['scheduled_at'] ?? null);

        $stmt->bind_param(
            'ssssssssi',
            $title,
            $body,
            $imageUrl,
            $deepLink,
            $targetType,
            $topicName,
            $status,
            $scheduledAt,
            $createdBy
        );

        if (!$stmt->execute()) {
            $error = $stmt->error;
            $stmt->close();
            throw new RuntimeException('Failed to create campaign: ' . $error);
        }

        $campaignId = (int)$stmt->insert_id;
        $stmt->close();

        return $campaignId;
    }

    public function sendCampaignNow(int $campaignId, array $input): array {
        $title = trim((string)($input['title'] ?? ''));
        $body = trim((string)($input['body'] ?? ''));
        $imageUrl = $this->normalizeNullableString($input['image_url'] ?? null);
        $deepLink = $this->normalizeNullableString($input['deep_link'] ?? null);
        $targetType = (string)($input['target_type'] ?? 'all');
        $topicName = $this->normalizeNullableString($input['topic_name'] ?? null);
        $targetToken = $this->normalizeNullableString($input['target_token'] ?? null);

        if ($title === '' || $body === '') {
            throw new InvalidArgumentException('Title and body are required');
        }

        $channelId = $this->resolveChannelId($input);
        [$targetKind, $targetId] = $this->deriveTargetFromDeepLink($deepLink);

        $payloadData = [
            'title' => $title,
            'message' => $body,
            'image_url' => $imageUrl ?? '',
            'deep_link' => $deepLink ?? '',
            'deeplink' => $deepLink ?? '',
            'type' => (string)($input['type'] ?? 'general'),
            'target_type' => $targetKind,
            'target_id' => $targetId ?? '',
            'id' => $targetId ?? '',
            'route' => (string)($input['route'] ?? ''),
            'guide_id' => (string)($input['guide_id'] ?? ''),
            'prompt_id' => (string)($input['prompt_id'] ?? ''),
            'channel_id' => $channelId,
            'timestamp' => (string)time(),
        ];

        $notification = [
            'title' => $title,
            'body' => $body,
            'image' => $imageUrl,
        ];

        $result = [
            'success_count' => 0,
            'failure_count' => 0,
            'results' => [],
        ];

        if ($targetType === 'all') {
            $send = $this->fcm->sendToTopic('all', $payloadData, $notification);
            $result['results'][] = [
                'target' => 'topic:all',
                'success' => (bool)$send['success'],
                'fcm_message_id' => $send['message_id'] ?? null,
                'error_code' => $send['error_code'] ?? null,
                'error_message' => $send['error_message'] ?? null,
            ];

            if (!empty($send['success'])) {
                $result['success_count'] = 1;
            } else {
                $result['failure_count'] = 1;
            }
        } elseif ($targetType === 'topic') {
            if ($topicName === null || $topicName === '') {
                throw new InvalidArgumentException('Topic name is required for topic target');
            }

            $send = $this->fcm->sendToTopic($topicName, $payloadData, $notification);
            $result['results'][] = [
                'target' => 'topic:' . $topicName,
                'success' => (bool)$send['success'],
                'fcm_message_id' => $send['message_id'] ?? null,
                'error_code' => $send['error_code'] ?? null,
                'error_message' => $send['error_message'] ?? null,
            ];

            if (!empty($send['success'])) {
                $result['success_count'] = 1;
            } else {
                $result['failure_count'] = 1;
            }
        } elseif ($targetType === 'token') {
            if ($targetToken === null || $targetToken === '') {
                throw new InvalidArgumentException('Token is required for token target');
            }

            $tokens = array_values(array_filter(array_map('trim', explode(',', $targetToken))));
            if (count($tokens) > 1) {
                $batchResult = $this->fcm->sendToTokens($tokens, $payloadData, $notification);
                $result['success_count'] = (int)($batchResult['success_count'] ?? 0);
                $result['failure_count'] = (int)($batchResult['failure_count'] ?? 0);

                foreach (($batchResult['results'] ?? []) as $row) {
                    $result['results'][] = [
                        'target' => (string)($row['token'] ?? ''),
                        'success' => (bool)($row['success'] ?? false),
                        'fcm_message_id' => $row['message_id'] ?? null,
                        'error_code' => $row['error_code'] ?? null,
                        'error_message' => $row['error_message'] ?? null,
                    ];
                }

                foreach (($batchResult['invalid_tokens'] ?? []) as $invalid) {
                    $this->deactivateTokenIfInvalid((string)($invalid['token'] ?? ''), (string)($invalid['reason'] ?? 'UNKNOWN'));
                }
            } else {
                $send = $this->fcm->sendToToken($targetToken, $payloadData, $notification);

                $result['results'][] = [
                    'target' => $targetToken,
                    'success' => (bool)$send['success'],
                    'fcm_message_id' => $send['message_id'] ?? null,
                    'error_code' => $send['error_code'] ?? null,
                    'error_message' => $send['error_message'] ?? null,
                ];

                if (!empty($send['success'])) {
                    $result['success_count'] = 1;
                } else {
                    $result['failure_count'] = 1;
                    $this->deactivateTokenIfInvalid($targetToken, (string)($send['error_code'] ?? 'UNKNOWN'));
                }
            }
        } else {
            throw new InvalidArgumentException('Invalid target type: ' . $targetType);
        }

        $this->persistCampaignResult($campaignId, $result);
        $this->insertNotificationLogs($campaignId, $result['results']);

        return [
            'success' => $result['failure_count'] === 0,
            'success_count' => $result['success_count'],
            'failure_count' => $result['failure_count'],
            'results' => $result['results'],
        ];
    }

    public function sendTestToToken(string $token, array $input, int $createdBy): array {
        if ($token === '') {
            throw new InvalidArgumentException('Token is required');
        }

        $campaignId = $this->createCampaign([
            'title' => $input['title'] ?? 'PicPose Test Notification',
            'body' => $input['body'] ?? 'If you are reading this, FCM delivery is working.',
            'image_url' => $input['image_url'] ?? null,
            'deep_link' => $input['deep_link'] ?? 'app://home',
            'target_type' => 'token',
            'topic_name' => null,
            'status' => 'draft',
            'scheduled_at' => null,
        ], $createdBy);

        $input['target_type'] = 'token';
        $input['target_token'] = $token;

        $result = $this->sendCampaignNow($campaignId, $input);
        $result['campaign_id'] = $campaignId;

        return $result;
    }

    public function fetchRecentCampaigns(int $limit = 20): array {
        $limit = max(1, min(100, $limit));

        $stmt = $this->db->prepare(
            'SELECT id, title, body, target_type, topic_name, status, success_count, failure_count, created_at, sent_at
             FROM notification_campaigns
             ORDER BY id DESC
             LIMIT ?'
        );
        $stmt->bind_param('i', $limit);
        $stmt->execute();
        $res = $stmt->get_result();

        $rows = [];
        while ($row = $res->fetch_assoc()) {
            $rows[] = $row;
        }

        $stmt->close();
        return $rows;
    }

    private function persistCampaignResult(int $campaignId, array $result): void {
        $stmt = $this->db->prepare(
            'UPDATE notification_campaigns
             SET status = ?, sent_at = NOW(), success_count = ?, failure_count = ?, updated_at = NOW()
             WHERE id = ?'
        );

        $status = $result['failure_count'] === 0 ? 'sent' : 'failed';
        $successCount = (int)$result['success_count'];
        $failureCount = (int)$result['failure_count'];

        $stmt->bind_param('siii', $status, $successCount, $failureCount, $campaignId);
        $stmt->execute();
        $stmt->close();
    }

    private function insertNotificationLogs(int $campaignId, array $rows): void {
        $stmt = $this->db->prepare(
            'INSERT INTO notification_logs
                (campaign_id, token, success, fcm_message_id, error_code, error_message)
             VALUES (?, ?, ?, ?, ?, ?)'
        );

        foreach ($rows as $row) {
            $token = (string)($row['target'] ?? '');
            $success = !empty($row['success']) ? 1 : 0;
            $fcmMessageId = $this->normalizeNullableString($row['fcm_message_id'] ?? null);
            $errorCode = $this->normalizeNullableString($row['error_code'] ?? null);
            $errorMessage = $this->normalizeNullableString($row['error_message'] ?? null);

            $stmt->bind_param('isisss', $campaignId, $token, $success, $fcmMessageId, $errorCode, $errorMessage);
            $stmt->execute();
        }

        $stmt->close();
    }

    private function deactivateTokenIfInvalid(string $token, string $errorCode): void {
        $normalized = strtoupper($errorCode);
        if (!in_array($normalized, ['UNREGISTERED', 'NOT_FOUND', 'INVALID_ARGUMENT'], true)) {
            return;
        }

        $stmt = $this->db->prepare(
            'UPDATE device_tokens
             SET is_active = 0,
                 deactivation_reason = ?,
                 deactivated_at = NOW(),
                 updated_at = NOW()
             WHERE (token = ? OR fcm_token = ?)' 
        );
        $stmt->bind_param('sss', $normalized, $token, $token);
        $stmt->execute();
        $stmt->close();
    }

    private function resolveChannelId(array $input): string {
        $type = strtolower(trim((string)($input['type'] ?? 'general')));

        if ($type === 'guide') {
            return 'picpose_guides';
        }

        if ($type === 'prompt') {
            return 'picpose_prompts';
        }

        return 'picpose_general';
    }

    private function normalizeNullableString($value): ?string {
        if (!is_string($value)) {
            return null;
        }
        $trimmed = trim($value);
        return $trimmed === '' ? null : $trimmed;
    }

    private function deriveTargetFromDeepLink(?string $deepLink): array {
        if ($deepLink === null || $deepLink === '') {
            return ['home', null];
        }

        if (str_starts_with($deepLink, 'app://prompts/')) {
            return ['prompt', ltrim(substr($deepLink, strlen('app://prompts/')), '/') ?: null];
        }

        if (str_starts_with($deepLink, 'app://guides/')) {
            return ['guide', ltrim(substr($deepLink, strlen('app://guides/')), '/') ?: null];
        }

        if (str_starts_with($deepLink, 'app://category/')) {
            return ['category', ltrim(substr($deepLink, strlen('app://category/')), '/') ?: null];
        }

        return ['home', null];
    }
}
