# Rewards UI V3 Manual Test Plan

1. Entry and loading
   - Open Rewards tab from bottom navigation.
   - Verify pull-to-refresh works.
   - Verify loading skeleton appears before content.

2. Logged-out state
   - Logout and open Rewards.
   - Verify top header shows login CTA.
   - Verify claim/watch/apply/claim-referral actions route to Login.

3. Progress header
   - Login and open Rewards.
   - Verify Level, XP progress bar, and points chip show correctly.

4. Wallet card
   - Verify points animate to latest value.
   - Verify token chips render when token balances exist.
   - Verify quick actions (Claim / Watch Ad) are visible.

5. Daily streak module
   - Verify day 1..7 stepper renders with rewards.
   - Validate rules:
   - completedDay = day <= streak_count when today_claimed=true
   - completedDay = day < streak_count when today_claimed=false
   - currentDay = streak_count when today_claimed=true, else streak_count+1 (clamped 1..7)
   - Verify current day pulse/highlight.

6. Claim reward flow
   - Tap claim button and verify loading state.
   - On success verify:
   - confetti animation plays
   - points counter increases
   - snackbar shows `+{points} credits added`

7. Earn card
   - Verify Watch Ad button pulses before ad reward is earned.
   - Complete ad flow and verify ad reward snackbar appears.
   - If backend provides ad cap, verify progress text/bar.

8. Prompt of the Day
   - Verify thumbnail displays (fallback placeholder when missing).
   - Verify FREE/DISCOUNT badge and cost chip.
   - Verify Open CTA works.
   - Verify Unlock Forever / Unlock for X button is shown for FREE/DISCOUNT modes.

9. Referral module
   - Verify code chip loads and can be copied.
   - Verify Share uses Play Store URL + code message.
   - Open apply-code bottom sheet and submit code.
   - Verify helper text:
   - `Rewards unlock after qualifying action (e.g., first premium unlock or first generation).`

10. Packs row
   - Verify horizontal pack carousel renders with price and owned badge.
   - Verify Browse all packs button opens packs flow.

11. Accessibility
   - Verify tap targets are comfortable.
   - Verify contrast in light/dark themes.
   - Verify screen-reader labels exist for key actions (share/copy/claim/open/watch).
