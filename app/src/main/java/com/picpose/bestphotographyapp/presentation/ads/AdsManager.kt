package com.picpose.bestphotographyapp.presentation.ads

import android.content.Context
import com.picpose.bestphotographyapp.core.constants.Constants
import com.picpose.bestphotographyapp.data.datastore.DeviceIdStore
import com.picpose.bestphotographyapp.data.models.AdConfig
import com.picpose.bestphotographyapp.data.models.AdsConfig
import com.picpose.bestphotographyapp.data.models.PlacementConfig
import com.picpose.bestphotographyapp.data.models.UnitConfig
import com.picpose.bestphotographyapp.data.network.AdsApiService
import com.picpose.bestphotographyapp.data.network.RetrofitClient
import com.picpose.bestphotographyapp.data.remote.localFallbackAdsConfig
import com.picpose.bestphotographyapp.data.repository.AdsConfigResult
import com.picpose.bestphotographyapp.data.repository.AdsConfigSource
import com.picpose.bestphotographyapp.data.repository.AdsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface AdsConfigState {
    data object Loading : AdsConfigState
    data class Ready(
        val source: AdsConfigSource,
        val configVersion: String,
        val timestamp: Long,
        val placementsCount: Int
    ) : AdsConfigState

    data class Error(
        val reason: String
    ) : AdsConfigState
}

object AdsManager {

    const val KEY_BANNER_OTHER = "banner_other"
    const val KEY_BANNER_2 = "banner_2"
    const val KEY_INTERSTITIAL_1 = "interstitial_1"
    const val KEY_INTERSTITIAL_2 = "interstitial_2"
    const val KEY_NATIVE_1 = "native_1"
    const val KEY_NATIVE_2 = "native_2"
    const val KEY_NATIVE_3 = "native_3"
    const val KEY_REWARDED_1 = "rewarded_1"
    const val KEY_HOME_BANNER = "home_banner"
    const val KEY_HOME_INTERSTITIAL = "home_interstitial"
    const val KEY_DETAIL_INTERSTITIAL = "detail_interstitial"
    const val KEY_NATIVE_AD = "native_ad"
    const val KEY_REWARDED_AD = "rewarded_ad"
    const val KEY_BANNER_HOME = "banner_home"
    const val KEY_INTERSTITIAL_HOME = "interstitial_home"
    const val KEY_INTERSTITIAL_DETAIL = "interstitial_detail"
    const val KEY_HOME_NATIVE = "home_native"
    const val KEY_DETAIL_NATIVE = "detail_native"
    const val KEY_REWARDED = "rewarded"

    private const val REASON_PLACEMENT_NOT_FOUND = "PLACEMENT_NOT_FOUND"
    private const val REASON_PLACEMENT_DISABLED = "PLACEMENT_DISABLED"
    private const val REASON_AUTO_DISABLED = "AUTO_DISABLED"
    private const val REASON_NO_UNITS = "NO_UNITS"
    private const val REASON_CMP_NOT_READY = "CMP_NOT_READY"
    private const val REASON_FREQUENCY_BLOCK = "FREQUENCY_BLOCK"
    private const val REASON_CONFIG_NOT_READY = "CONFIG_NOT_READY"

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var consentGate: ConsentGate = DefaultConsentGate()

    @Volatile
    private var currentResult: AdsConfigResult? = null
    @Volatile
    private var injectedRepository: AdsRepository? = null
    @Volatile
    private var consentReady: Boolean = false

    private val warmUpMutex = Mutex()
    private val _configState = MutableStateFlow<AdsConfigState>(AdsConfigState.Loading)
    val configState: StateFlow<AdsConfigState> = _configState.asStateFlow()

    private data class ResolvedPlacement(
        val requestedKey: String,
        val matchedKey: String,
        val placement: PlacementConfig
    )

    fun configure(context: Context, gate: ConsentGate? = null) {
        appContext = context.applicationContext
        gate?.let { consentGate = it }
        AdsLog.init(context)
        if (currentResult == null) {
            _configState.value = AdsConfigState.Loading
        }
        AdsLog.i(AdsLog.TAG_MANAGER, "[AdsManager] action=configure gateSet=${gate != null} state=${_configState.value}")
    }

    fun setConsentGate(gate: ConsentGate) {
        consentGate = gate
        AdsLog.i(AdsLog.TAG_MANAGER, "[AdsManager] action=setConsentGate")
    }

    fun bindRepository(repository: AdsRepository) {
        injectedRepository = repository
        AdsLog.i(AdsLog.TAG_MANAGER, "[AdsManager] action=bindRepository")
    }

    suspend fun warmUp(forceRefresh: Boolean = false) {
        warmUpMutex.withLock {
            val context = appContext
            if (context == null) {
                _configState.value = AdsConfigState.Error(REASON_CONFIG_NOT_READY)
                AdsLog.w(AdsLog.TAG_MANAGER, "[AdsManager] action=warmUp status=SKIP reason=context_not_configured")
                return
            }
            _configState.value = AdsConfigState.Loading
            AdsFrequencyManager.initialize(context)

            val repository = injectedRepository ?: AdsRepository(
                api = RetrofitClient.createService(AdsApiService::class.java),
                cache = AdsConfigCache(context),
                deviceIdStore = DeviceIdStore(context)
            )

            val result = runCatching {
                repository.getAdsConfig(forceRefresh = forceRefresh)
            }.getOrElse {
                AdsLog.e(AdsLog.TAG_MANAGER, "[AdsManager] action=warmUp status=FAIL error=${it.message}", it)
                val now = System.currentTimeMillis()
                AdsConfigResult(
                    config = AdsConfigCache(context).get()?.config ?: localFallbackAdsConfig(),
                    source = AdsConfigSource.FALLBACK,
                    timestamp = now,
                    configVersion = "fallback"
                )
            }

            currentResult = result
            consentReady = runCatching { consentGate.isReady() }.getOrDefault(false)
            _configState.value = AdsConfigState.Ready(
                source = result.source,
                configVersion = result.configVersion,
                timestamp = result.timestamp,
                placementsCount = result.config.placements.size
            )

            AdsLog.i(
                AdsLog.TAG_MANAGER,
                "[AdsManager] action=warmUp status=OK source=${result.source} version=${result.configVersion} placementsCount=${result.config.placements.size} updatedAt=${result.config.global.configUpdatedAt} env=${result.config.global.environment} adsEnabled=${result.config.global.adsEnabled} useTestAds=${result.config.global.useTestAds} cmpRequired=${result.config.global.cmpRequired} consentReady=$consentReady"
            )
            AdsLog.i(AdsLog.TAG_MANAGER, "[AdsManager] snapshot=${debugSnapshot()}")
        }
    }

    fun canShowAds(): Boolean {
        val config = currentResult?.config
        if (config == null) {
            AdsLog.d(AdsLog.TAG_MANAGER, "[AdsManager] canShowAds=false reason=$REASON_CONFIG_NOT_READY")
            return false
        }
        if (!config.global.adsEnabled) {
            AdsLog.i(AdsLog.TAG_MANAGER, "[AdsManager] canShowAds=false reason=GLOBAL_DISABLED")
            return false
        }
        if (config.global.cmpRequired && !consentReady) {
            AdsLog.i(AdsLog.TAG_MANAGER, "[AdsManager] canShowAds=false reason=$REASON_CMP_NOT_READY cmpRequired=true consentReady=$consentReady")
            return false
        }
        AdsLog.d(AdsLog.TAG_MANAGER, "[AdsManager] canShowAds=true env=${config.global.environment} useTestAds=${config.global.useTestAds}")
        return true
    }

    fun getAdUnitId(placementKey: String): String? {
        val result = currentResult ?: run {
            logPlacementNullDecision(
                placementKey = placementKey,
                reason = REASON_CONFIG_NOT_READY
            )
            return null
        }
        if (result.config.global.cmpRequired && !consentReady) {
            logPlacementNullDecision(
                placementKey = placementKey,
                reason = REASON_CMP_NOT_READY
            )
            return null
        }

        val resolved = resolvePlacement(placementKey, result.config, requireUnits = true) ?: run {
            logPlacementNullDecision(
                placementKey = placementKey,
                reason = REASON_PLACEMENT_NOT_FOUND
            )
            return null
        }
        val placement = resolved.placement

        if (!placement.enabled) {
            logPlacementDecision(resolved, null, REASON_PLACEMENT_DISABLED)
            return null
        }
        if (placement.autoDisabled) {
            logPlacementDecision(resolved, null, REASON_AUTO_DISABLED)
            return null
        }

        val sortedUnits = placement.units.sortedBy { it.priority }
        if (sortedUnits.isEmpty()) {
            logPlacementDecision(resolved, null, REASON_NO_UNITS)
            return null
        }

        val preferLive = isProductionSelection(result.config)
        val preferred = if (preferLive) sortedUnits.filter { it.isLive } else sortedUnits.filter { it.isTest }
        val selected = preferred.firstOrNull() ?: sortedUnits.firstOrNull()
        val selectedId = selected?.adUnitId?.takeIf { it.isNotBlank() }
        val reason = if (selectedId == null) REASON_NO_UNITS else "OK"

        logPlacementDecision(resolved, selected, reason)
        return selectedId
    }

    fun getPlacement(placementKey: String): PlacementConfig? {
        val config = currentResult?.config ?: return null
        return resolvePlacement(placementKey, config)?.placement
    }

    fun getPlacement(placement: AdPlacement): PlacementConfig? = getPlacement(placement.key)

    fun shouldShowNow(placementKey: String): Boolean {
        val result = currentResult ?: run {
            AdsLog.i(AdsLog.TAG_MANAGER, "[AdsManager] placement=$placementKey shouldShow=false reason=$REASON_CONFIG_NOT_READY")
            return false
        }
        if (result.config.global.cmpRequired && !consentReady) {
            AdsLog.i(AdsLog.TAG_MANAGER, "[AdsManager] placement=$placementKey shouldShow=false reason=$REASON_CMP_NOT_READY")
            return false
        }
        val resolved = resolvePlacement(placementKey, result.config, requireUnits = true) ?: run {
            AdsLog.i(AdsLog.TAG_MANAGER, "[AdsManager] placement=$placementKey shouldShow=false reason=$REASON_PLACEMENT_NOT_FOUND")
            return false
        }
        if (!resolved.placement.enabled) {
            AdsLog.i(AdsLog.TAG_MANAGER, "[AdsManager] placement=$placementKey resolved=${resolved.matchedKey} shouldShow=false reason=$REASON_PLACEMENT_DISABLED")
            return false
        }
        if (resolved.placement.autoDisabled) {
            AdsLog.i(AdsLog.TAG_MANAGER, "[AdsManager] placement=$placementKey resolved=${resolved.matchedKey} shouldShow=false reason=$REASON_AUTO_DISABLED")
            return false
        }

        val adType = resolved.placement.adType.trim().lowercase()
        if (adType == "native") {
            AdsLog.i(
                AdsLog.TAG_MANAGER,
                "[AdsManager] placement=$placementKey resolved=${resolved.matchedKey} shouldShow=true reason=OK_NATIVE_NO_FREQUENCY"
            )
            return true
        }

        val limit = resolved.placement.frequency?.takeIf { it > 0 } ?: result.config.global.defaultFrequencyPerHour
        val canShow = AdsFrequencyManager.canShow(resolved.matchedKey, limit.coerceAtLeast(1))
        AdsLog.i(
            AdsLog.TAG_MANAGER,
            "[AdsManager] placement=$placementKey resolved=${resolved.matchedKey} shouldShow=$canShow limitPerHour=${limit.coerceAtLeast(1)} reason=${if (canShow) "OK" else REASON_FREQUENCY_BLOCK}"
        )
        return canShow
    }

    fun markShown(placementKey: String) {
        runCatching {
            val resolvedKey = resolvePlacement(placementKey, currentResult?.config)?.matchedKey ?: placementKey
            AdsFrequencyManager.markShown(resolvedKey)
            AdsLog.d(AdsLog.TAG_MANAGER, "[AdsManager] placement=$placementKey resolved=$resolvedKey action=markShown status=OK")
        }.onFailure {
            AdsLog.e(AdsLog.TAG_MANAGER, "[AdsManager] placement=$placementKey action=markShown status=FAIL error=${it.message}", it)
        }
    }

    fun debugSnapshot(): String {
        val result = currentResult ?: return "state=${_configState.value} source=NONE version=NONE env=NONE ads_enabled=false placementsCount=0"
        val hasRemoteAppId = !result.config.global.admobAppId.isNullOrBlank()
        return "state=${_configState.value::class.simpleName} source=${result.source} version=${result.configVersion} env=${result.config.global.environment} ads_enabled=${result.config.global.adsEnabled} use_test_ads=${result.config.global.useTestAds} cmp_required=${result.config.global.cmpRequired} consent_ready=$consentReady remote_app_id=$hasRemoteAppId placementsCount=${result.config.placements.size}"
    }

    fun admobAppIdOverrideOrNull(): String? {
        val appId = currentResult?.config?.global?.admobAppId?.trim().orEmpty()
        return appId.takeIf { it.matches(Regex("^ca-app-pub-[0-9]{16}~[0-9]{10}$")) }
    }

    fun isEnabled(placement: AdPlacement): Boolean {
        val resolved = resolvePlacement(placement.key, currentResult?.config) ?: return false
        return resolved.placement.enabled && !resolved.placement.autoDisabled
    }

    fun unitId(placement: AdPlacement): String? = getAdUnitId(placement.key)

    fun canShowInterstitialNow(placement: AdPlacement): Boolean {
        if (placement.format != AdFormat.INTERSTITIAL) return false
        return shouldShowNow(placement.key)
    }

    private fun resolvePlacement(
        requestedKey: String,
        config: AdsConfig?,
        requireUnits: Boolean = false
    ): ResolvedPlacement? {
        if (config == null) return null
        val candidates = candidateKeysFor(requestedKey)
        for (candidate in candidates) {
            val placement = config.findPlacement(candidate)
            if (placement != null) {
                if (requireUnits && placement.units.isEmpty()) {
                    continue
                }
                return ResolvedPlacement(requestedKey = requestedKey, matchedKey = candidate, placement = placement)
            }
        }
        return null
    }

    private fun candidateKeysFor(key: String): List<String> {
        return AdsPlacementRegistry.resolveCandidates(key)
    }

    private fun logPlacementNullDecision(
        placementKey: String,
        reason: String
    ) {
        val result = currentResult
        AdsLog.i(
            AdsLog.TAG_MANAGER,
            "[AdsManager] placement=$placementKey placementExists=false enabled=false autoDisabled=false unitsCount=0 selectedUnit=null reason=$reason source=${result?.source} version=${result?.configVersion} placementsCount=${result?.config?.placements?.size ?: 0}"
        )
    }

    private fun logPlacementDecision(
        resolved: ResolvedPlacement,
        selected: UnitConfig?,
        reason: String
    ) {
        val result = currentResult
        val config = result?.config
        val env = config?.global?.environment ?: "unknown"
        val useTestAds = config?.global?.useTestAds ?: true
        val enabled = resolved.placement.enabled && !resolved.placement.autoDisabled
        val selectedMasked = AdsLog.maskAdUnitId(selected?.adUnitId)
        AdsLog.i(
            AdsLog.TAG_MANAGER,
            "[AdsManager] placement=${resolved.requestedKey} resolved=${resolved.matchedKey} placementExists=true enabled=${resolved.placement.enabled} autoDisabled=${resolved.placement.autoDisabled} unitsCount=${resolved.placement.units.size} selectedUnit=${AdsLog.maskAdUnitId(selected?.adUnitId)} is_test=${selected?.isTest} is_live=${selected?.isLive} priority=${selected?.priority} refreshSeconds=${resolved.placement.refreshSeconds} frequency=${resolved.placement.frequency} reason=$reason source=${result?.source} version=${result?.configVersion} placementsCount=${result?.config?.placements?.size ?: 0}"
        )
        AdsLog.i(
            AdsLog.TAG_MANAGER,
            "[Ads] env=$env useTestAds=$useTestAds placement=${resolved.matchedKey} enabled=$enabled unit=$selectedMasked reason=$reason"
        )
    }

    private fun isProductionSelection(config: AdsConfig): Boolean {
        val envProduction = config.global.environment.equals("production", ignoreCase = true)
        return envProduction && !config.global.useTestAds
    }

    suspend fun bootstrap(context: Context, apiKey: String? = null) {
        configure(context)
        warmUp(forceRefresh = false)
    }

    fun canLoadAnyAd(): Boolean = canShowAds()

    fun canShowPlacement(placementKey: String, expectedType: String? = null): Boolean {
        val placement = getPlacement(placementKey) ?: return false
        if (!expectedType.isNullOrBlank() && !placement.adType.equals(expectedType, ignoreCase = true)) {
            return false
        }
        return shouldShowNow(placementKey)
    }

    fun markPlacementShown(placementKey: String, expectedType: String? = null) {
        val placement = getPlacement(placementKey) ?: return
        if (!expectedType.isNullOrBlank() && !placement.adType.equals(expectedType, ignoreCase = true)) {
            return
        }
        markShown(placementKey)
    }

    fun bannerIdOrNull(): String? = getAdUnitId(KEY_BANNER_OTHER)
    fun bannerId2OrNull(): String? = getAdUnitId(KEY_BANNER_2)
    fun interstitialIdOrNull(): String? = getAdUnitId(KEY_INTERSTITIAL_1)
    fun interstitialId2OrNull(): String? = getAdUnitId(KEY_INTERSTITIAL_2)
    fun nativeIdOrNull(): String? = getAdUnitId(KEY_NATIVE_1)
    fun nativeId2OrNull(): String? = getAdUnitId(KEY_NATIVE_2)
    fun nativeId3OrNull(): String? = getAdUnitId(KEY_NATIVE_3)
    fun rewardedIdOrNull(): String? = getAdUnitId(KEY_REWARDED_1)

    @Deprecated("Use warmUp + getAdUnitId APIs")
    fun init(config: AdConfig) {
        currentResult = AdsConfigResult(
            config = localFallbackAdsConfig().copy(
                placements = listOf(
                    PlacementConfig(KEY_BANNER_OTHER, "banner", true, null, null, false, listOf(UnitConfig(config.bannerId, 1, false, true, "admob", true))),
                    PlacementConfig(KEY_BANNER_2, "banner", true, null, null, false, listOf(UnitConfig(config.bannerId2, 1, false, true, "admob", true))),
                    PlacementConfig(KEY_INTERSTITIAL_1, "interstitial", true, null, null, false, listOf(UnitConfig(config.interstitialId, 1, false, true, "admob", true))),
                    PlacementConfig(KEY_INTERSTITIAL_2, "interstitial", true, null, null, false, listOf(UnitConfig(config.interstitialId2, 1, false, true, "admob", true))),
                    PlacementConfig(KEY_NATIVE_1, "native", true, null, null, false, listOf(UnitConfig(config.nativeId, 1, false, true, "admob", true))),
                    PlacementConfig(KEY_NATIVE_2, "native", true, null, null, false, listOf(UnitConfig(config.nativeId2, 1, false, true, "admob", true))),
                    PlacementConfig(KEY_NATIVE_3, "native", true, null, null, false, listOf(UnitConfig(config.nativeId3, 1, false, true, "admob", true))),
                    PlacementConfig(KEY_REWARDED_1, "rewarded", true, null, null, false, listOf(UnitConfig(config.rewardedId, 1, false, true, "admob", true)))
                )
            ),
            source = AdsConfigSource.FALLBACK,
            timestamp = System.currentTimeMillis(),
            configVersion = "legacy"
        )
        consentReady = true
        _configState.value = AdsConfigState.Ready(
            source = AdsConfigSource.FALLBACK,
            configVersion = "legacy",
            timestamp = System.currentTimeMillis(),
            placementsCount = currentResult?.config?.placements?.size ?: 0
        )
    }

    @Deprecated("Use *OrNull APIs")
    fun appId(): String = Constants.TEST_APP_ID

    @Deprecated("Use bannerIdOrNull")
    fun bannerId(): String = bannerIdOrNull().orEmpty()

    @Deprecated("Use bannerId2OrNull")
    fun bannerId2(): String = bannerId2OrNull().orEmpty()

    @Deprecated("Use interstitialIdOrNull")
    fun interstitialId(): String = interstitialIdOrNull().orEmpty()

    @Deprecated("Use interstitialId2OrNull")
    fun interstitialId2(): String = interstitialId2OrNull().orEmpty()

    @Deprecated("Use nativeIdOrNull")
    fun nativeId(): String = nativeIdOrNull().orEmpty()

    @Deprecated("Use nativeId2OrNull")
    fun nativeId2(): String = nativeId2OrNull().orEmpty()

    @Deprecated("Use nativeId3OrNull")
    fun nativeId3(): String = nativeId3OrNull().orEmpty()

    @Deprecated("Use rewardedIdOrNull")
    fun rewardedId(): String = rewardedIdOrNull().orEmpty()
}
