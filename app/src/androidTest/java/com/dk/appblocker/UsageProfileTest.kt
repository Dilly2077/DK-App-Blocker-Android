package com.dk.appblocker

import android.app.UiAutomation
import android.os.Debug
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class UsageProfileTest {
    @Test fun profileSharedUsageQueriesAcrossManyDailyRules() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val pkg = instrumentation.context.packageName
        val hadAccess = hasUsageAccess(context)
        instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
            .executeShellCommand("appops set ${context.packageName} GET_USAGE_STATS allow").use {
                android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText()
            }
        try {
        assertTrue("Usage permission is required for a meaningful profile", hasUsageAccess(context))
        Prefs.savePlans(context, List(20) { BlockPlan(name = "Profile $it", triggerType = TriggerType.DAILY_LIMIT,
            packages = listOf(pkg), dailyLimitMinutes = 1440) })
        val queries = RuleEngine.usageQueryCount()
        val started = SystemClock.elapsedRealtime()
        val cpuStarted = Debug.threadCpuTimeNanos()
        repeat(20) { RuleEngine.shouldBlock(context, pkg) }
        val elapsed = SystemClock.elapsedRealtime() - started
        val cpu = Debug.threadCpuTimeNanos() - cpuStarted
        val loads = RuleEngine.usageQueryCount() - queries
        assertTrue("Profile must exercise the real usage-event query", loads >= 1)
        assertTrue("Each rule must not query usage history separately", loads <= elapsed / 5000 + 2)
        java.io.File(context.getExternalFilesDir(null), "usage-profile.txt").writeText(
            "20 decisions x 20 daily-limit rules\nElapsed ms=$elapsed\nTest-thread CPU ms=${cpu / 1_000_000}\nOS usage queries=$loads\n" +
                "Service idle scan interval=2000 ms; usage sample TTL=5000 ms. This is a CPU/query profile, not a physical battery-life measurement.\n")
        } finally {
            context.getSharedPreferences("dk_blocker_prefs", 0).edit().clear().commit()
            if (!hadAccess) instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
                .executeShellCommand("appops set ${context.packageName} GET_USAGE_STATS default").use {
                    android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText()
                }
        }
    }
}
