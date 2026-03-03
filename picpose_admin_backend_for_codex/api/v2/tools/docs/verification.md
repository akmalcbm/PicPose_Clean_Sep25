# V2 Master Verification Suite

## Files
- `api/v2/tools/config.sample.php`
- `api/v2/tools/pre_migration_check.php`
- `api/v2/tools/post_migration_check.php`
- `api/v2/tools/db_integrity_checks.php`
- `api/v2/tools/smoke_test_v2.php`
- `api/v2/tools/run_all_checks.php`

## Configure
1. Copy `api/v2/tools/config.sample.php` to `api/v2/tools/config.php`.
2. Set these values:
   - `BASE_URL`
   - `API_KEY`
   - `TEST_EMAIL`
   - `TEST_PASS`
   - `TEST_TOKEN`
3. You can also override any of them with environment variables.

## Run
From the project root:

```bash
php picpose_admin_backend_for_codex/api/v2/tools/pre_migration_check.php
php picpose_admin_backend_for_codex/api/v2/tools/post_migration_check.php
php picpose_admin_backend_for_codex/api/v2/tools/db_integrity_checks.php
php picpose_admin_backend_for_codex/api/v2/tools/smoke_test_v2.php
php picpose_admin_backend_for_codex/api/v2/tools/run_all_checks.php
```

You can also open these scripts in a browser for a readable HTML report, but CLI is preferred for exit codes and CI usage.

## What Each Check Covers
- `pre_migration_check.php`: confirms required V2 tables and columns exist before rollout.
- `post_migration_check.php`: confirms schema presence, defaults, basic data sanity, and key indexes after migrations.
- `db_integrity_checks.php`: validates wallets, ledgers, dedupe constraints, referral uniqueness, and POTD sanity.
- `smoke_test_v2.php`: exercises legacy V1 plus V2 endpoints end to end with `PASS/FAIL/SKIP` output.
- `run_all_checks.php`: runs the full suite and prints one final readiness conclusion.

## Migration Order Reminder
1. Deploy code first.
2. Apply V2 migrations in production.
3. Run `php picpose_admin_backend_for_codex/api/v2/tools/run_all_checks.php`.
4. Review any `FAIL` or `SKIP` output before opening traffic to the new flows.
5. Keep V1 endpoints unchanged and available during rollout.

## Rollback Notes
- If a migration partially fails, stop rollout and identify the exact table/index that failed.
- If code is already deployed, keep V1 clients on existing endpoints while fixing schema drift.
- Roll back write-heavy V2 features first if ledger or wallet integrity checks fail.
- Preserve ledger and unlock data before manual rollback work; do not delete rows blindly.
- Re-run `pre_migration_check.php`, `post_migration_check.php`, and `db_integrity_checks.php` after any rollback or manual repair.

## Production-Safe Practices
- Use a dedicated test account for auth-required smoke tests.
- Never place real production secrets in logs or screenshots.
- Run the suite against staging before production.
- Prefer maintenance windows for migrations that add constraints or large indexes.
- Watch PHP error logs and MySQL slow query logs during the first post-migration run.
