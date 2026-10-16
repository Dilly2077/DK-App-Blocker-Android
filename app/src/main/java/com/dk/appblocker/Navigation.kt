package com.dk.appblocker

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class MainTab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Rounded.Home), RULES("Rules", Icons.Rounded.FormatListBulleted),
    INSIGHTS("Insights", Icons.Rounded.BarChart), SETTINGS("Settings", Icons.Rounded.Settings)
}
internal enum class Route { MAIN, CREATE_TYPE, EDITOR, STRICT }

@Composable
internal fun AppRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val states = rememberSaveableStateHolder()
    var route by rememberSaveable { mutableStateOf(Route.MAIN) }
    var strictReturn by rememberSaveable { mutableStateOf(Route.MAIN) }
    var tab by rememberSaveable { mutableStateOf(MainTab.HOME) }
    var editingJson by rememberSaveable { mutableStateOf("") }
    var existing by rememberSaveable { mutableStateOf(false) }
    val editing = remember(editingJson) { runCatching { Gson().fromJson(editingJson, BlockPlan::class.java) }.getOrNull() }
    var plans by remember { mutableStateOf(Prefs.getPlans(context).toList()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val tick = rememberVisibleTick()
    fun strict() { strictReturn = route; route = Route.STRICT }
    fun back() {
        if (busy) return
        error = null
        when (route) {
            Route.EDITOR -> if (existing) { route = Route.MAIN; tab = MainTab.RULES } else route = Route.CREATE_TYPE
            Route.CREATE_TYPE -> route = Route.MAIN
            Route.STRICT -> route = strictReturn
            Route.MAIN -> tab = MainTab.HOME
        }
    }
    BackHandler(route != Route.MAIN || tab != MainTab.HOME) { back() }
    fun change(operation: () -> PolicyWrite, success: () -> Unit = {}) {
        if (busy) return
        busy = true; error = null
        scope.launch {
            val (result, latest) = withContext(Dispatchers.IO) { operation() to Prefs.getPlans(context).toList() }
            busy = false
            when (result) {
                PolicyWrite.SAVED -> { plans = latest; success() }
                PolicyWrite.LOCKED -> strict()
                PolicyWrite.STORAGE_ERROR -> error = "Could not save to storage. Your previous settings are retained. Please retry."
            }
        }
    }
    states.SaveableStateProvider(if (route == Route.EDITOR) "editor:${editing?.id}" else route.name) {
        when (route) {
            Route.CREATE_TYPE -> CreateRuleTypeScreen(::back, { type ->
                if (!Prefs.canEditRules(context)) strict() else {
                    editingJson = Gson().toJson(defaultPlanFor(type)); existing = false; route = Route.EDITOR
                }
            }, ::strict)
            Route.EDITOR -> RuleEditorScreen(editing ?: defaultPlanFor(TriggerType.SCHEDULE), busy, ::back, { plan ->
                change({ RuleRepository(context).save(plan) }) { tab = MainTab.RULES; route = Route.MAIN; error = null }
            }, error = error, existing = existing)
            Route.STRICT -> StrictModeScreen(::back) { chosen -> tab = chosen; route = Route.MAIN }
            Route.MAIN -> MainShell(tab, plans, tick, busy, error, { if (!busy) tab = it },
                { if (Prefs.canEditRules(context)) route = Route.CREATE_TYPE else strict() },
                { plan ->
                    if (!Prefs.canEditRules(context)) strict() else {
                        editingJson = Gson().toJson(plan); existing = true; route = Route.EDITOR
                    }
                }, ::strict, { plan, action -> change({ RuleRepository(context).perform(plan.id, action) }) })
        }
    }
}

internal fun defaultPlanFor(type: TriggerType): BlockPlan = when (type) {
    TriggerType.DAILY_LIMIT -> BlockPlan(name = "App Limit", triggerType = type, dailyLimitMinutes = 30)
    TriggerType.SCHEDULE -> BlockPlan(name = "Schedule", triggerType = type)
    TriggerType.LOCATION -> BlockPlan(name = "Location", triggerType = type, radiusMeters = 200f)
    TriggerType.MANUAL -> BlockPlan(name = "Focus Mode", triggerType = type, manualActive = true)
}

@Composable
internal fun MainShell(tab: MainTab, plans: List<BlockPlan>, tick: Long, busy: Boolean, error: String?,
                       onTab: (MainTab) -> Unit, onCreateRule: () -> Unit, onEdit: (BlockPlan) -> Unit,
                       onStrict: () -> Unit, onAction: (BlockPlan, RuleAction) -> Unit) {
    val pager = rememberPagerState(initialPage = tab.ordinal) { MainTab.entries.size }
    LaunchedEffect(tab) { if (pager.currentPage != tab.ordinal) pager.scrollToPage(tab.ordinal) }
    LaunchedEffect(pager) { snapshotFlow { pager.settledPage }.distinctUntilChanged().collect { onTab(MainTab.entries[it]) } }
    Scaffold(containerColor = Ink, bottomBar = { MainBottomBar(tab, onTab) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            error?.let { Text(it, color = Danger, modifier = Modifier.fillMaxWidth().padding(DKSpace.card).testTag("storageError")) }
            HorizontalPager(pager, modifier = Modifier.fillMaxSize().background(Ink).testTag("mainPager")) { page ->
                when (MainTab.entries[page]) {
                    MainTab.HOME -> HomeScreen(plans, tick, onCreateRule, onEdit, onStrict) { onTab(MainTab.RULES) }
                    MainTab.RULES -> RulesScreen(plans, tick, busy, onCreateRule, onEdit, onAction)
                    MainTab.INSIGHTS -> InsightsScreen(tick)
                    MainTab.SETTINGS -> SettingsScreen(tick, onStrict)
                }
            }
        }
    }
}

@Composable
internal fun MainBottomBar(selected: MainTab, onTab: (MainTab) -> Unit) {
    val largeText = androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f
    NavigationBar(modifier = Modifier.heightIn(min = if (largeText) 104.dp else 80.dp), containerColor = Ink2, tonalElevation = 0.dp) {
        MainTab.entries.forEach { tab ->
            NavigationBarItem(selected = selected == tab, onClick = { onTab(tab) },
                colors = NavigationBarItemDefaults.colors(selectedIconColor = Green, selectedTextColor = Green,
                    indicatorColor = Green.copy(alpha = .1f), unselectedIconColor = Muted, unselectedTextColor = Muted),
                icon = { Icon(tab.icon, null) }, label = { Text(tab.label, fontSize = if (largeText) 9.sp else 11.sp,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) })
        }
    }
}
