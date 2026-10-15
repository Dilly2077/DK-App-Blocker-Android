package com.dk.appblocker

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

/** Save phases deliberately kill only this isolated test app immediately after the save returns. */
class SaveDurabilityTest {
    @Test fun savesSurviveImmediateProcessTermination() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        when (InstrumentationRegistry.getArguments().getString("phase")) {
            "rules" -> {
                assertTrue(Prefs.savePlans(context, listOf(BlockPlan(id = "immediate-save", name = "Durable rule"))))
                android.os.Process.killProcess(android.os.Process.myPid())
            }
            "appearance" -> {
                assertTrue(Prefs.saveBlockScreen(context, BlockScreenSettings(title = "Durable appearance", accentHex = "#FFD166", showAppName = false)))
                android.os.Process.killProcess(android.os.Process.myPid())
            }
            "verify" -> {
                assertEquals("immediate-save", Prefs.getPlans(context).single().id)
                assertEquals("Durable appearance", Prefs.getBlockScreen(context).title)
                assertEquals("#FFD166", Prefs.getBlockScreen(context).accentHex)
                assertFalse(Prefs.getBlockScreen(context).showAppName)
                context.getSharedPreferences("dk_blocker_prefs", 0).edit().clear().commit()
            }
            else -> org.junit.Assume.assumeTrue("Run with the local multi-process audit runner", false)
        }
    }
}
