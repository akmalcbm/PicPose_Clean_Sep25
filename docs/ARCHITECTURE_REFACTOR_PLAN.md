# PicPose Architecture Refactor Plan

## Goal
Re-organize package structure for a live production app without changing business behavior.

## Package Conventions
- Keep root app entry classes: `MainActivity`, `PicPoseApp`, `di/*`.
- Use root `navigation/*` for route contracts and graph wiring.
- Keep shared reusable UI in root `components/*`.
- Use feature-first organization under `presentation/*`.
- Use layer-first organization under `data/*`.
- Keep technical infrastructure under `core/*`.
- Keep lightweight helper extensions/utilities under root `utils/*`.

## Old -> New Mapping
1. `presentation/navigation/*` -> `navigation/*`
2. `ui/*` -> `presentation/<feature>/*` or `components/*` (when reusable)
3. `presentation/components/common/*` -> `components/common/*`
4. `presentation/components/ads/*` + `ui/ads/*` + UI-facing `presentation/ads/*` -> `components/ads/*`
5. `presentation/components/home/*` -> `presentation/home/components/*`
6. `presentation/screens/*` + `presentation/viewmodels/*` -> feature packages:
   - `presentation/auth/*`
   - `presentation/home/*`
   - `presentation/explore/*`
   - `presentation/create/*`
   - `presentation/profile/*`
   - `presentation/settings/*`
   - `presentation/prompts/*`
   - `presentation/guides/*`
   - `presentation/rewards/*`
   - `presentation/packs/*`
   - `presentation/splash/*`
   - `presentation/about/*`
7. `util/*`:
   - `NetworkMonitor` -> `core/network`
   - `ImageIO` -> `utils`
8. `core/utils/*` split:
   - keep infra (`ConnectivityObserver`, `PKCEUtil`) in `core/utils`
   - move general helpers/extensions to root `utils`
9. `fcm/*` -> `core/notifications/*`
10. `data` cleanup:
    - `data/database/*` -> `data/local/database/*`
    - `data/datastore/*` -> `data/local/datastore/*`
    - Retrofit APIs -> `data/remote/api/*`
    - API responses -> `data/remote/response/*`
    - Auth clients -> `data/remote/auth/*` (kept)
    - rembg services -> `data/service/rembg/*`
    - networking infra (`RetrofitClient`, interceptors, token provider) -> `core/network/*`

## Domain Improvement (Pragmatic)
- Keep `domain/repository` interfaces.
- Introduce `data/repository/AuthRepositoryImpl` and bind it to `domain/repository/AuthRepository`.
- Add `domain/model` and `domain/usecase` incrementally only for high-value flows (auth first), avoiding excessive wrappers.

## Safety Strategy
1. Move files in batches (navigation, utils/core, UI/features, data, domain).
2. Update package declarations and imports immediately after each batch.
3. Run compile check after major batches.
4. Keep duplicate screens only when needed; place old versions in `legacy` package if still retained.

