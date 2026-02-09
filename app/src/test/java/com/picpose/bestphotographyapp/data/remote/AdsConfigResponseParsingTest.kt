package com.picpose.bestphotographyapp.data.remote

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdsConfigResponseParsingTest {

    private val gson = Gson()

    @Test
    fun parse_latest_ads_config_and_map_to_domain_with_safe_defaults() {
        val json = """
            {
              "success": true,
              "data": {
                "global": {
                  "ads_enabled": true,
                  "environment": "production",
                  "cmp_required": true,
                  "default_frequency_per_hour": 6,
                  "use_test_ads": false,
                  "config_updated_at": "2026-02-09T10:15:30Z"
                },
                "placements": [
                  {
                    "key": "native_1",
                    "ad_type": "native",
                    "enabled": true,
                    "refresh_seconds": 120,
                    "frequency": 4,
                    "auto_disabled": false,
                    "units": [
                      {
                        "ad_unit_id": "ca-app-pub-xxx/native-live-1",
                        "priority": 1,
                        "is_test": false,
                        "is_live": true,
                        "network": "admob",
                        "sdk_required": true
                      },
                      {
                        "ad_unit_id": "ca-app-pub-xxx/native-test-1",
                        "priority": 2,
                        "is_test": true,
                        "is_live": false,
                        "network": "admob",
                        "sdk_required": true
                      }
                    ]
                  },
                  {
                    "key": "banner_1",
                    "ad_type": "banner",
                    "enabled": true,
                    "auto_disabled": false
                  },
                  {
                    "key": "rewarded_1",
                    "ad_type": "rewarded",
                    "enabled": false,
                    "units": []
                  }
                ]
              }
            }
        """.trimIndent()

        val dto = gson.fromJson(json, AdsConfigResponse::class.java)
        val domain = dto.toDomainOrNull()

        assertNotNull(domain)
        assertEquals(true, domain!!.global.adsEnabled)
        assertEquals("production", domain.global.environment)
        assertEquals(true, domain.global.cmpRequired)
        assertEquals(6, domain.global.defaultFrequencyPerHour)
        assertEquals(false, domain.global.useTestAds)

        assertEquals(3, domain.placements.size)

        val native = domain.findPlacement("native_1")
        assertNotNull(native)
        assertEquals("native", native!!.adType)
        assertEquals(2, native.units.size)
        assertEquals("ca-app-pub-xxx/native-live-1", native.units.first().adUnitId)

        val banner = domain.findPlacement("banner_1")
        assertNotNull(banner)
        assertTrue(banner!!.units.isEmpty()) // missing units must map to empty list safely

        val rewarded = domain.findPlacement("rewarded_1")
        assertNotNull(rewarded)
        assertTrue(rewarded!!.units.isEmpty())
    }

    @Test
    fun map_handles_missing_nodes_without_crashing() {
        val json = """
            {
              "success": true,
              "data": {
                "placements": [
                  { "key": "native_1", "ad_type": "native" }
                ]
              }
            }
        """.trimIndent()

        val dto = gson.fromJson(json, AdsConfigResponse::class.java)
        val domain = dto.toDomainOrNull()

        assertNotNull(domain)
        assertEquals(true, domain!!.global.adsEnabled)
        assertEquals("test", domain.global.environment)
        assertEquals(1, domain.placements.size)
        assertTrue(domain.placements.first().units.isEmpty())
    }
}
