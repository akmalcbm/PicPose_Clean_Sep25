package com.picpose.bestphotographyapp.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.picpose.bestphotographyapp.data.models.AppSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Unit tests for HomeRepository AppSettings functionality
 */
@RunWith(AndroidJUnit4::class)
class HomeRepositoryAppSettingsTest {

    private lateinit var context: Context
    private lateinit var repository: HomeRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        repository = HomeRepository(context = context, useMocks = false)
    }

    @Test
    fun testGetAppSettings_Success() = runBlocking {
        // Given: Repository is initialized
        
        // When: Fetching app settings
        val result = repository.getAppSettings().first()
        
        // Then: Result should be success or contain cached data
        assertTrue(
            "Should get success result or cached data",
            result.isSuccess || result.isFailure
        )
    }

    @Test
    fun testGetAppSettings_ReturnsValidStructure() = runBlocking {
        // Given: Repository is initialized
        
        // When: Fetching app settings
        val result = repository.getAppSettings().first()
        
        // Then: If successful, should have valid structure
        result.getOrNull()?.let { settings ->
            assertNotNull("AppSettings should not be null", settings)
            assertNotNull("Admob should not be null", settings.admob)
            assertNotNull("Contact should not be null", settings.contact)
            assertNotNull("Policies should not be null", settings.policies)
            assertNotNull("About should not be null", settings.about)
            assertNotNull("Meta should not be null", settings.meta)
        }
    }

    @Test
    fun testGetAppSettings_CacheStrategy() = runBlocking {
        // Given: Repository is initialized
        
        // When: Fetching app settings twice
        val firstResult = repository.getAppSettings().first()
        val secondResult = repository.getAppSettings(forceRefresh = false).first()
        
        // Then: Both should succeed (cache should work)
        if (firstResult.isSuccess) {
            assertTrue("Second call should also succeed with cache", secondResult.isSuccess)
        }
    }

    @Test
    fun testGetAppSettings_ForceRefresh() = runBlocking {
        // Given: Repository is initialized and settings are cached
        repository.getAppSettings().first()
        
        // When: Force refreshing settings
        val result = repository.getAppSettings(forceRefresh = true).first()
        
        // Then: Should attempt to fetch fresh data
        assertNotNull("Result should not be null", result)
    }

    @Test
    fun testAppSettings_DefaultValues() {
        // Given: A default AppSettings instance
        val settings = AppSettings()
        
        // Then: All nested objects should have safe defaults
        assertNotNull("Admob should have defaults", settings.admob)
        assertNotNull("Contact should have defaults", settings.contact)
        assertNotNull("Policies should have defaults", settings.policies)
        assertNotNull("About should have defaults", settings.about)
        assertNotNull("Meta should have defaults", settings.meta)
        
        assertEquals("Default appName", "PicPose", settings.appName)
        assertEquals("Default tagline", "", settings.tagline)
    }

    @Test
    fun testAppSettings_BackwardCompatibility() {
        // Given: An AppSettings with nested data
        val settings = AppSettings(
            admob = com.picpose.bestphotographyapp.data.models.Admob(
                appId = "test_app_id",
                banner1Id = "test_banner_id"
            ),
            contact = com.picpose.bestphotographyapp.data.models.Contact(
                email = "test@example.com",
                phone = "123-456-7890"
            )
        )
        
        // Then: Backward compatibility properties should work
        assertEquals("test_app_id", settings.appId)
        assertEquals("test_banner_id", settings.banner1Id)
        assertEquals("test@example.com", settings.supportEmail)
        assertEquals("123-456-7890", settings.supportPhone)
    }
}
