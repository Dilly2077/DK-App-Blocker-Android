package com.dk.appblocker

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

/** Invoked in separate instrumentation processes, then again after an actual emulator reboot. */
class PersistenceDeviceTest {
    @Test fun persistedStateAndRecoveryDeadlines() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val pkg = instrumentation.context.packageName
        when (val phase = InstrumentationRegistry.getArguments().getString("phase")) {
            "prepare" -> {
                context.getSharedPreferences("dk_blocker_prefs", 0).edit().clear().commit()
                Prefs.savePlans(context, listOf(BlockPlan(id = "persisted-rule", triggerType = TriggerType.MANUAL,
                    packages = listOf(pkg), manualActive = true)))
                Prefs.saveBlockScreen(context, BlockScreenSettings(title = "Persisted appearance"))
                Prefs.saveStrict(context, StrictSettings(enabled = true, unlockMethod = "TIMER",
                    lockUntil = System.currentTimeMillis() + 300_000, lockElapsedEnd = SystemClock.elapsedRealtime() + 300_000,
                    lockBoot = Prefs.boot(context)))
                Prefs.startFocus(context, setOf(pkg), 25)
                val token = RuleEngine.shouldBlock(context, pkg).policyToken
                Prefs.allowPackageUntil(context, pkg, System.currentTimeMillis() + 300_000, token)
                // Flush ordinary asynchronous settings before the process terminates.
                assertTrue(context.getSharedPreferences("dk_blocker_prefs", 0).edit()
                    .putInt("prepared_pid", android.os.Process.myPid()).putInt("prepared_boot", Prefs.boot(context)).commit())
            }
            "process" -> {
                assertNotEquals(context.getSharedPreferences("dk_blocker_prefs", 0).getInt("prepared_pid", -1), android.os.Process.myPid())
                assertEquals("persisted-rule", Prefs.getPlans(context).single().id)
                assertEquals("Persisted appearance", Prefs.getBlockScreen(context).title)
                assertTrue(Prefs.getStrict(context).enabled)
                assertTrue(Prefs.focusEnd(context) > System.currentTimeMillis())
                assertFalse("Break must survive process restart", RuleEngine.shouldBlock(context, pkg).blocked)
            }
            "reboot" -> {
                assertNotEquals(context.getSharedPreferences("dk_blocker_prefs", 0).getInt("prepared_boot", -1), Prefs.boot(context))
                assertEquals("persisted-rule", Prefs.getPlans(context).single().id)
                assertEquals("Persisted appearance", Prefs.getBlockScreen(context).title)
                assertFalse("Timer Strict Mode must recover after reboot", Prefs.getStrict(context).enabled)
                assertEquals(0L, Prefs.focusEnd(context))
                assertEquals(0L, Prefs.packageAllowedUntil(context, pkg))
                assertTrue("Persistent manual rules must still block", RuleEngine.shouldBlock(context, pkg).blocked)
                context.getSharedPreferences("dk_blocker_prefs", 0).edit().clear().commit()
            }
            else -> fail("Explicit persistence test phase required: $phase")
        }
    }
}
