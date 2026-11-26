package com.example

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.service.RiderModeService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h915dp-xxhdpi")
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private fun waitForText(text: String) {
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.mainClock.advanceTimeBy(350L)
        composeTestRule.onNodeWithText(text).assertIsDisplayed()
    }

    @Test
    fun readAppNameFromContext() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Ultimate Rider Booster", appName)
    }

    @Test
    fun navigationTabsAndSettingsFlowWork() {
        composeTestRule.waitForIdle()
        waitForText("ULTIMATE RIDER BOOSTER")

        composeTestRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("start_rider_mode_button"))
        composeTestRule.onNodeWithTag("start_rider_mode_button").assertIsDisplayed()
        composeTestRule.onNodeWithText("Monitoring Stopped").assertIsDisplayed()

        composeTestRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("boost_now_button"))
        composeTestRule.onNodeWithTag("boost_now_button").assertIsDisplayed()

        composeTestRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("smart_gps_recovery_card"))
        composeTestRule.onNodeWithTag("smart_gps_recovery_card").assertIsDisplayed()

        composeTestRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("talabat_detection_card"))
        composeTestRule.onNodeWithTag("talabat_detection_card").assertIsDisplayed()

        composeTestRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("app_update_card"))
        composeTestRule.onNodeWithTag("app_update_card").assertIsDisplayed()

        composeTestRule.onNodeWithTag("nav_gps").performClick()
        waitForText("GPS PERFORMANCE ENGINE")

        composeTestRule.onNodeWithTag("nav_network").performClick()
        waitForText("NETWORK DIAGNOSTICS")

        composeTestRule.onNodeWithTag("nav_rider").performClick()
        waitForText("TALABAT RIDER CHECK")

        composeTestRule.onNodeWithTag("nav_tools").performClick()
        waitForText("RIDER TOOLS SUITE")

        composeTestRule.onNodeWithTag("nav_home").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("settings_button"))
        waitForText("ULTIMATE RIDER BOOSTER")

        composeTestRule.onNodeWithTag("settings_button").performClick()
        waitForText("SETTINGS & PRIVACY")
        composeTestRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("settings_update_section"))
        composeTestRule.onNodeWithTag("settings_update_section").assertIsDisplayed()

        composeTestRule.onNodeWithTag("settings_back_button").performClick()
        waitForText("ULTIMATE RIDER BOOSTER")
    }

    @Test
    fun riderModeStartAndStopLifecycleAndNotificationWork() {
        val app = ApplicationProvider.getApplicationContext<RiderBoosterApplication>()
        val shadowApp = Shadows.shadowOf(app as Application)
        shadowApp.grantPermissions(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS
        )

        val controller = app.container.riderModeController
        controller.refreshPermissionSnapshot()
        assertTrue(controller.state.value.hasForegroundLocationPermission)
        assertTrue(controller.state.value.hasNotificationPermission)

        // Start RiderModeService via Robolectric ServiceController
        val serviceController = Robolectric.buildService(
            RiderModeService::class.java,
            Intent(app, RiderModeService::class.java).apply {
                action = RiderModeService.ACTION_START_RIDER_MODE
            }
        ).create().startCommand(0, 1)

        val service = serviceController.get()
        assertTrue(controller.state.value.isActive)
        assertEquals("Rider Mode Active", controller.state.value.statusLabel)

        val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowNm = Shadows.shadowOf(nm)
        val ongoingNotification = shadowNm.getNotification(RiderModeService.ONGOING_NOTIFICATION_ID)
        assertNotNull(ongoingNotification)
        val title = ongoingNotification.extras.getString(android.app.Notification.EXTRA_TITLE)
        assertEquals("ULTIMATE RIDER BOOSTER — Rider Mode Active", title)

        // Stop RiderModeService immediately via ACTION_STOP_RIDER_MODE
        service.onStartCommand(
            Intent(app, RiderModeService::class.java).apply {
                action = RiderModeService.ACTION_STOP_RIDER_MODE
            },
            0,
            2
        )

        assertFalse(controller.state.value.isActive)
        assertEquals("Monitoring Stopped", controller.state.value.statusLabel)
    }
}
