# Referrals Test Plan

1. Referrer login
   - Open the Rewards tab while logged in as account A.
   - Confirm "My referral code" is visible.
   - Tap Share and verify the sharesheet includes the PicPose link and the referral code.

2. Referee applies code
   - Log in as account B.
   - Open Rewards, tap Apply code, and submit account A's code.
   - Confirm the app shows `Code applied successfully`.
   - Confirm the referral block shows pending status guidance.

3. Qualifying action
   - As account B, complete one qualifying action:
   - Unlock one premium prompt with ad, points, or token.
   - Or copy prompts until the fifth copy is recorded.
   - Refresh Rewards and confirm the referral status becomes `QUALIFIED`.

4. Claim reward
   - As account B, tap Claim reward.
   - Confirm the app shows `Reward credited to your wallet`.
   - Verify both account A and account B wallet balances increase.

5. Ledger verification
   - In backend/admin or DB, confirm two `points_ledger` rows were inserted:
   - `referral_referrer_reward`
   - `referral_referee_reward`

6. Repeat claim protection
   - Tap Claim reward again as account B.
   - Confirm the backend returns already rewarded and no duplicate wallet or ledger credit is added.

7. Referrer counters
   - Log back in as account A and refresh Rewards.
   - Confirm `referred_count`, `pending_count`, and `rewarded_count` reflect the referral lifecycle.
