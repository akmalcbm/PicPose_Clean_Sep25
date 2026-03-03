<?php

require_once __DIR__ . '/_common.php';

v2_tool_render_header('V2 Smoke Test Suite');

$config = v2_tool_load_config();
$apiKey = (string)$config['API_KEY'];
$token = (string)$config['TEST_TOKEN'];
$results = [
    'passed' => 0,
    'failed' => 0,
    'skipped' => 0,
    'total' => 0,
];
$premiumPostId = null;
$premiumPostLocked = null;
$lastKnownPointsBalance = null;

function smoke_record(string $status, string $name, string $details): void
{
    global $results;
    $results['total']++;
    if ($status === 'PASS') {
        $results['passed']++;
        v2_tool_pass($name, $details);
        return;
    }
    if ($status === 'FAIL') {
        $results['failed']++;
        v2_tool_fail($name, $details);
        return;
    }
    $results['skipped']++;
    v2_tool_skip($name, $details);
}

function smoke_assert_json(array $resp, string $name): ?array
{
    if (!$resp['ok']) {
        smoke_record('FAIL', $name, 'HTTP request failed: ' . $resp['error']);
        return null;
    }
    if (!is_array($resp['json'])) {
        smoke_record('FAIL', $name, 'Response is not valid JSON, HTTP ' . $resp['status']);
        return null;
    }
    return $resp['json'];
}

if ($apiKey === '') {
    smoke_record('FAIL', 'Configuration', 'API_KEY is not configured in tools config or environment');
    v2_tool_info('Summary', 'Aborting smoke tests because API_KEY is required for both V1 and V2 endpoints');
    v2_tool_finish(1);
}

$v1Resp = v2_tool_http_request('GET', 'api/ai_posts/get_ai_posts.php', [
    'query' => ['api_key' => $apiKey, 'limit' => 5, 'offset' => 0],
]);
$v1Json = smoke_assert_json($v1Resp, 'V1 health');
if ($v1Json !== null && !empty($v1Json['success']) && isset($v1Json['data']) && is_array($v1Json['data'])) {
    smoke_record('PASS', 'V1 health', 'Legacy endpoint returned JSON with success and data array');
} elseif ($v1Json !== null) {
    smoke_record('FAIL', 'V1 health', 'Legacy endpoint JSON shape did not include success=true and data array');
}

$v2ListResp = v2_tool_http_request('GET', 'api/v2/ai_posts/get_ai_posts.php', [
    'query' => ['api_key' => $apiKey, 'limit' => 5, 'offset' => 0],
]);
$v2ListJson = smoke_assert_json($v2ListResp, 'V2 list');
if ($v2ListJson !== null && !empty($v2ListJson['success']) && isset($v2ListJson['data']) && is_array($v2ListJson['data'])) {
    foreach ($v2ListJson['data'] as $item) {
        if (($item['tier'] ?? '') === 'PREMIUM') {
            $premiumPostId = (int)($item['id'] ?? 0);
            break;
        }
    }
    smoke_record('PASS', 'V2 list', 'Returned ' . count($v2ListJson['data']) . ' items');
} elseif ($v2ListJson !== null) {
    smoke_record('FAIL', 'V2 list', 'V2 list did not return success=true with data array');
}

$potdResp = v2_tool_http_request('GET', 'api/v2/ai_posts/get_prompt_of_the_day.php', [
    'query' => ['api_key' => $apiKey],
]);
$potdJson = smoke_assert_json($potdResp, 'POTD');
if ($potdJson !== null && !empty($potdJson['success']) && !empty($potdJson['post']['id']) && !empty($potdJson['potd_mode'])) {
    smoke_record('PASS', 'POTD', 'Prompt of the day returned post.id=' . $potdJson['post']['id'] . ' mode=' . $potdJson['potd_mode']);
} elseif ($potdJson !== null) {
    smoke_record('FAIL', 'POTD', 'POTD response missing post.id or potd_mode');
}

if ($token === '' && $config['TEST_EMAIL'] !== '' && $config['TEST_PASS'] !== '' && v2_tool_endpoint_exists('auth/login.php')) {
    $loginResp = v2_tool_http_request('POST', 'api/v2/auth/login.php', [
        'query' => ['api_key' => $apiKey],
        'body' => [
            'email' => $config['TEST_EMAIL'],
            'password' => $config['TEST_PASS'],
        ],
    ]);
    $loginJson = smoke_assert_json($loginResp, 'Auth login');
    if ($loginJson !== null && ($loginJson['status'] ?? '') === 'success' && !empty($loginJson['token'])) {
        $token = (string)$loginJson['token'];
        smoke_record('PASS', 'Auth login', 'Authenticated test user and captured bearer token ' . v2_tool_mask_secret($token));
    } elseif ($loginJson !== null) {
        smoke_record('FAIL', 'Auth login', 'Login endpoint did not return status=success and token');
    }
} elseif ($token !== '') {
    smoke_record('PASS', 'Auth token', 'Using configured bearer token ' . v2_tool_mask_secret($token));
} else {
    smoke_record('SKIP', 'Auth bootstrap', 'No TEST_TOKEN and no TEST_EMAIL/TEST_PASS configured');
}

if ($token === '') {
    foreach (['Streak status', 'Claim daily login first call', 'Claim daily login second call', 'Reward ad points', 'Premium prompt locked flow', 'Referrals', 'Rewards hub'] as $name) {
        smoke_record('SKIP', $name, 'Auth token unavailable');
    }
} else {
    $streakResp = v2_tool_http_request('GET', 'api/v2/wallet/get_streak_status.php', [
        'query' => ['api_key' => $apiKey],
        'token' => $token,
    ]);
    $streakJson = smoke_assert_json($streakResp, 'Streak status');
    if ($streakJson !== null && array_key_exists('streak_count', $streakJson) && !empty($streakJson['rewards_schedule']) && count((array)$streakJson['rewards_schedule']) >= 7) {
        $lastKnownPointsBalance = isset($streakJson['points_balance']) ? (int)$streakJson['points_balance'] : null;
        smoke_record('PASS', 'Streak status', 'Streak status returned schedule length ' . count((array)$streakJson['rewards_schedule']));
    } elseif ($streakJson !== null) {
        smoke_record('FAIL', 'Streak status', 'Missing streak_count or rewards_schedule length < 7');
    }

    $claimResp1 = v2_tool_http_request('POST', 'api/v2/wallet/claim_daily_login.php', [
        'query' => ['api_key' => $apiKey],
        'token' => $token,
    ]);
    $claimJson1 = smoke_assert_json($claimResp1, 'Claim daily login first call');
    if ($claimJson1 !== null && !empty($claimJson1['success']) && (isset($claimJson1['claimed']) || !empty($claimJson1['already_claimed']))) {
        $lastKnownPointsBalance = isset($claimJson1['points_balance']) ? (int)$claimJson1['points_balance'] : $lastKnownPointsBalance;
        smoke_record('PASS', 'Claim daily login first call', 'Endpoint returned claimed=' . json_encode($claimJson1['claimed'] ?? false) . ' already_claimed=' . json_encode($claimJson1['already_claimed'] ?? false));
    } elseif ($claimJson1 !== null) {
        smoke_record('FAIL', 'Claim daily login first call', 'Unexpected daily claim response shape');
    }

    $claimResp2 = v2_tool_http_request('POST', 'api/v2/wallet/claim_daily_login.php', [
        'query' => ['api_key' => $apiKey],
        'token' => $token,
    ]);
    $claimJson2 = smoke_assert_json($claimResp2, 'Claim daily login second call');
    if ($claimJson2 !== null && !empty($claimJson2['success']) && !empty($claimJson2['already_claimed'])) {
        smoke_record('PASS', 'Claim daily login second call', 'Second call correctly reports already_claimed=true');
    } elseif ($claimJson2 !== null) {
        smoke_record('FAIL', 'Claim daily login second call', 'Second call did not return already_claimed=true');
    }

    $adResp = v2_tool_http_request('POST', 'api/v2/wallet/reward_ad_points.php', [
        'query' => ['api_key' => $apiKey],
        'token' => $token,
        'body' => ['ad_reward_id' => 'test_ad_' . time() . '_' . random_int(1000, 9999)],
    ]);
    $adJson = smoke_assert_json($adResp, 'Reward ad points');
    if ($adJson !== null && !empty($adJson['success']) && array_key_exists('points_added', $adJson) && array_key_exists('points_balance', $adJson)) {
        $lastKnownPointsBalance = (int)$adJson['points_balance'];
        $pointsAdded = (int)$adJson['points_added'];
        if (in_array($pointsAdded, [10, 15], true)) {
            smoke_record('PASS', 'Reward ad points', 'Added ' . $pointsAdded . ' points and returned updated balance');
        } else {
            smoke_record('FAIL', 'Reward ad points', 'Unexpected points_added=' . $pointsAdded . ' (expected baseline 10 or active AB variant 15)');
        }
    } elseif ($adJson !== null) {
        smoke_record('FAIL', 'Reward ad points', 'Response missing points_added or points_balance');
    }

    if ($premiumPostId === null) {
        $findPremiumResp = v2_tool_http_request('GET', 'api/v2/ai_posts/get_ai_posts.php', [
            'query' => ['api_key' => $apiKey, 'limit' => 20, 'offset' => 0],
            'token' => $token,
        ]);
        $findPremiumJson = smoke_assert_json($findPremiumResp, 'Premium prompt search');
        if ($findPremiumJson !== null && !empty($findPremiumJson['data']) && is_array($findPremiumJson['data'])) {
            foreach ($findPremiumJson['data'] as $item) {
                if (($item['tier'] ?? '') === 'PREMIUM') {
                    $premiumPostId = (int)($item['id'] ?? 0);
                    break;
                }
            }
        }
    }

    if ($premiumPostId === null || $premiumPostId <= 0) {
        smoke_record('SKIP', 'Premium prompt locked flow', 'No published PREMIUM prompt found in list response');
    } else {
        $detailRespBefore = v2_tool_http_request('GET', 'api/v2/ai_posts/get_ai_post.php', [
            'query' => ['api_key' => $apiKey, 'id' => $premiumPostId],
            'token' => $token,
        ]);
        $detailJsonBefore = smoke_assert_json($detailRespBefore, 'Premium prompt detail before unlock');
        $tryUnlock = false;
        if ($detailJsonBefore !== null && !empty($detailJsonBefore['success']) && isset($detailJsonBefore['data'])) {
            $premiumPostLocked = !empty($detailJsonBefore['data']['isLocked']);
            if ($premiumPostLocked && empty($detailJsonBefore['data']['fullPrompt'])) {
                smoke_record('PASS', 'Premium prompt detail before unlock', 'Premium prompt is locked and fullPrompt is hidden');
                $tryUnlock = true;
            } elseif (!$premiumPostLocked) {
                smoke_record('PASS', 'Premium prompt detail before unlock', 'Prompt already unlocked for test user');
            } else {
                smoke_record('FAIL', 'Premium prompt detail before unlock', 'Locked prompt still exposed fullPrompt content');
                $tryUnlock = true;
            }
        } elseif ($detailJsonBefore !== null) {
            smoke_record('FAIL', 'Premium prompt detail before unlock', 'Detail endpoint did not return data');
        }

        if ($tryUnlock) {
            $cost = (int)($detailJsonBefore['data']['premiumUnlockCostPoints'] ?? 200);
            $unlockResp = null;
            if ($lastKnownPointsBalance !== null && $lastKnownPointsBalance >= $cost) {
                $unlockResp = v2_tool_http_request('POST', 'api/v2/ai_posts/unlock_prompt_points.php', [
                    'query' => ['api_key' => $apiKey],
                    'token' => $token,
                    'body' => ['post_id' => $premiumPostId],
                ]);
                $unlockJson = smoke_assert_json($unlockResp, 'Premium unlock via points');
                if ($unlockJson !== null && !empty($unlockJson['success']) && !empty($unlockJson['unlocked'])) {
                    $lastKnownPointsBalance = isset($unlockJson['points_balance']) ? (int)$unlockJson['points_balance'] : $lastKnownPointsBalance;
                    smoke_record('PASS', 'Premium unlock via points', 'Unlocked prompt with points cost=' . (int)($unlockJson['cost'] ?? $cost));
                } elseif ($unlockJson !== null) {
                    smoke_record('FAIL', 'Premium unlock via points', 'Points unlock did not return success=true and unlocked=true');
                }
            } else {
                $unlockResp = v2_tool_http_request('POST', 'api/v2/ai_posts/unlock_prompt_ad.php', [
                    'query' => ['api_key' => $apiKey],
                    'token' => $token,
                    'body' => [
                        'post_id' => $premiumPostId,
                        'ad_reward_id' => 'test_ad_unlock_' . time() . '_' . random_int(1000, 9999),
                    ],
                ]);
                $unlockJson = smoke_assert_json($unlockResp, 'Premium unlock via ad');
                if ($unlockJson !== null && !empty($unlockJson['success']) && !empty($unlockJson['unlocked'])) {
                    smoke_record('PASS', 'Premium unlock via ad', 'Unlocked prompt with ad reward path');
                } elseif ($unlockJson !== null) {
                    smoke_record('FAIL', 'Premium unlock via ad', 'Ad unlock did not return success=true and unlocked=true');
                }
            }

            $detailRespAfter = v2_tool_http_request('GET', 'api/v2/ai_posts/get_ai_post.php', [
                'query' => ['api_key' => $apiKey, 'id' => $premiumPostId],
                'token' => $token,
            ]);
            $detailJsonAfter = smoke_assert_json($detailRespAfter, 'Premium prompt detail after unlock');
            if ($detailJsonAfter !== null && !empty($detailJsonAfter['success']) && isset($detailJsonAfter['data'])) {
                $isLocked = !empty($detailJsonAfter['data']['isLocked']);
                $fullPrompt = $detailJsonAfter['data']['fullPrompt'] ?? null;
                if (!$isLocked || !empty($fullPrompt)) {
                    smoke_record('PASS', 'Premium prompt detail after unlock', 'Prompt is now readable after unlock flow');
                } else {
                    smoke_record('FAIL', 'Premium prompt detail after unlock', 'Prompt remained locked after unlock attempt');
                }
            } elseif ($detailJsonAfter !== null) {
                smoke_record('FAIL', 'Premium prompt detail after unlock', 'Detail endpoint did not return data after unlock');
            }
        }
    }

    $packsResp = v2_tool_http_request('GET', 'api/v2/packs/get_packs.php', [
        'query' => ['api_key' => $apiKey],
        'token' => $token,
    ]);
    $packsJson = smoke_assert_json($packsResp, 'Packs list');
    $firstPackId = null;
    if ($packsJson !== null && !empty($packsJson['success']) && isset($packsJson['data']) && is_array($packsJson['data'])) {
        if (!empty($packsJson['data'][0]['id'])) {
            $firstPackId = (int)$packsJson['data'][0]['id'];
            smoke_record('PASS', 'Packs list', 'Returned ' . count($packsJson['data']) . ' active packs');
        } else {
            smoke_record('SKIP', 'Packs list', 'No active packs available to inspect');
        }
    } elseif ($packsJson !== null) {
        smoke_record('FAIL', 'Packs list', 'Packs endpoint did not return success=true with data array');
    }
    if ($firstPackId !== null) {
        $packDetailResp = v2_tool_http_request('GET', 'api/v2/packs/get_pack_details.php', [
            'query' => ['api_key' => $apiKey, 'id' => $firstPackId],
            'token' => $token,
        ]);
        $packDetailJson = smoke_assert_json($packDetailResp, 'Pack details');
        if ($packDetailJson !== null && !empty($packDetailJson['success']) && isset($packDetailJson['items']) && is_array($packDetailJson['items'])) {
            smoke_record('PASS', 'Pack details', 'Pack details returned ' . count($packDetailJson['items']) . ' items');
        } elseif ($packDetailJson !== null) {
            smoke_record('FAIL', 'Pack details', 'Pack detail response missing items array');
        }
    }

    $refResp = v2_tool_http_request('GET', 'api/v2/referrals/get_my_code.php', [
        'query' => ['api_key' => $apiKey],
        'token' => $token,
    ]);
    $refJson = smoke_assert_json($refResp, 'Referrals');
    if ($refJson !== null && !empty($refJson['success']) && !empty($refJson['code'])) {
        smoke_record('PASS', 'Referrals', 'Referral code returned');
    } elseif ($refJson !== null) {
        smoke_record('FAIL', 'Referrals', 'Referral code response missing code');
    }

    $hubResp = v2_tool_http_request('GET', 'api/v2/rewards/hub.php', [
        'query' => ['api_key' => $apiKey],
        'token' => $token,
    ]);
    $hubJson = smoke_assert_json($hubResp, 'Rewards hub');
    $hubHasCore = $hubJson !== null
        && !empty($hubJson['success'])
        && array_key_exists('points_balance', $hubJson)
        && isset($hubJson['streak_count'])
        && array_key_exists('today_claimed', $hubJson)
        && isset($hubJson['prompt_of_the_day'])
        && isset($hubJson['referral'])
        && isset($hubJson['packs'])
        && isset($hubJson['progress'])
        && isset($hubJson['token_balances']);
    if ($hubHasCore) {
        smoke_record('PASS', 'Rewards hub', 'Rewards hub returned core sections' . (isset($hubJson['ab_flags']) ? ' including AB flags' : ''));
    } elseif ($hubJson !== null) {
        smoke_record('FAIL', 'Rewards hub', 'Rewards hub response missing one or more required sections');
    }
}

v2_tool_info('Summary', sprintf('total=%d passed=%d failed=%d skipped=%d', $results['total'], $results['passed'], $results['failed'], $results['skipped']));
if ($results['failed'] > 0) {
    v2_tool_fail('Final Result', 'Smoke test suite has failures');
    v2_tool_finish(1);
}

v2_tool_pass('Final Result', 'Smoke test suite passed with no failures');
v2_tool_finish(0);
