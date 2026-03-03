# Android V3 Test Plan

## Build and launch
- Install a debug build for app version `3.0.0`.
- Verify app starts without crashes on a fresh install.
- Verify `Explore`, `Home`, `Profile`, login, and existing V1 browse flows still open.

## Logged out flows
- Open `Rewards` from bottom navigation.
- Verify login prompt card is shown.
- Verify daily streak schedule still renders.
- Verify Prompt of the Day teaser loads.
- Verify claim, rewarded ad, referral claim, and pack unlock actions redirect to login instead of crashing.
- Open `All Prompts` and verify premium cards render with `Premium` lock state and teaser text.
- Open a locked prompt detail and verify full prompt is hidden with unlock CTAs.

## Logged in flows
- Login with a valid account and confirm `Rewards` refreshes into hub data.
- Verify points balance and token balances render.
- Verify daily streak card shows current streak and claim state.
- Tap `Claim today's reward` and verify success message plus updated balance/state.
- Re-open claim on same day and verify duplicate-safe response.

## Rewarded ads
- Open `Rewards`, tap `Watch Ad (+credits)`, complete the ad, and verify:
- A server reward is granted only after the ad reward callback.
- Duplicate retry with same ad reward is blocked gracefully.
- Points balance refreshes from backend.
- Open a locked premium prompt, tap `Watch Ad`, complete the ad, and verify the prompt unlocks and refetches.

## Premium unlocks
- Open a locked premium prompt detail.
- Unlock with credits and verify prompt content becomes visible after refetch.
- If token unlock is deployed, use `Use Unlock Token` and verify token balance decreases and prompt unlocks.
- If token unlock endpoint is unavailable, verify user sees a stable feature-unavailable message.

## Prompt of the Day
- Verify `Rewards` shows POTD mode (`FREE`, `DISCOUNT`, or premium).
- Open the POTD prompt and verify mode-specific behavior:
- `FREE`: prompt opens unlocked.
- `DISCOUNT`: detail shows discounted credit cost.

## Referrals
- Verify `My code` loads for logged-in users.
- Tap `Share` and verify Android Sharesheet opens with referral code text.
- Apply a valid referral code and verify success state or duplicate-safe state.
- Claim referral reward after qualification and verify success or clear qualified/pending message.

## Packs
- Open `Rewards` → `Browse packs`.
- Verify pack list loads for logged-in and logged-out users.
- Open a pack detail screen and verify included prompts load.
- Unlock a pack with points and verify:
- Success message is shown.
- Pack reloads as owned.
- Included prompts show unlocked state.

## Progress and polish
- Verify `Rewards` progress card shows level, XP, and progress bar.
- Verify refresh icon reloads hub data without crashing.
- Verify no blank-screen regressions during fast navigation between Rewards, Packs, Prompt detail, and Login.

## Regression checks
- Browse V1-backed `Home` and `Explore` prompt cards.
- Open prompt detail from Home/Explore and verify route still works.
- Run `./gradlew :app:compileDebugKotlin`.
