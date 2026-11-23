package dev.minimalist.launcher

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AgentApiTest {
    private val repo = AppRepository(ApplicationProvider.getApplicationContext<Context>())
    private val known = setOf("a/A", "b/B", "c/C")

    @Test fun appliesSectionsAndReportsUnknownKeys() {
        val plan = JSONObject("""{"favourites":["a/A","x/X"],"dock":["b/B"],"hidden":["c/C"],"folders":{"Work":["a/A","b/B"],"Fun":["b/B"]}}""")
        val out = AgentApi.apply(repo, plan, known)
        assertEquals(listOf("a/A"), repo.favourites())
        assertEquals(listOf("b/B"), repo.dock())
        assertEquals(setOf("c/C"), repo.hidden())
        assertEquals(listOf("Work", "Fun"), repo.folderNames())
        // an app can sit in several folders
        assertEquals(mapOf("Work" to listOf("a/A", "b/B"), "Fun" to listOf("b/B")), repo.folderMembers())
        assertEquals(listOf("x/X"), (0 until out.getJSONArray("unknownKeys").length()).map { out.getJSONArray("unknownKeys").getString(it) })
    }

    @Test fun homeFoldersAndReordering() {
        AgentApi.apply(repo, JSONObject("""{"favourites":["a/A","b/B","c/C"],"folders":{"Work":["a/A"],"Fun":["b/B"]},"homeFolders":["Fun","Missing","Work"]}"""), known)
        assertEquals(listOf("Fun", "Work"), repo.homeFolders())
        repo.moveFavourite("c/C", -2)
        assertEquals(listOf("c/C", "a/A", "b/B"), repo.favourites())
        repo.moveHomeFolder("Fun", 5)
        assertEquals(listOf("Work", "Fun"), repo.homeFolders())
        repo.deleteFolder("Work")
        assertEquals(listOf("Fun"), repo.homeFolders())
    }

    @Test fun appCacheRoundTripsEvenWithOddLabels() {
        val apps = listOf(AppEntry("Plain", "p.a", "p.a.Main"), AppEntry("Tab\there\nnewline", "p.b", "p.b.Main"), AppEntry("Ünïcode ✓", "p.c", "p.c.Main"))
        repo.saveAppCache(apps)
        val back = repo.cachedApps()
        assertEquals(listOf("p.a/p.a.Main", "p.b/p.b.Main", "p.c/p.c.Main"), back.map { it.key })
        assertEquals(listOf("Plain", "Tab here newline", "Ünïcode ✓"), back.map { it.label })
    }

    @Test fun gestureDefaultsAndBindings() {
        assertEquals(GestureAction.Notifications, repo.gesture(Gesture.SwipeDown).action)
        assertEquals(GestureAction.LauncherSettings, repo.gesture(Gesture.LongPress).action)
        assertEquals(GestureAction.None, repo.gesture(Gesture.DoubleTap).action)
        repo.setGesture(Gesture.DoubleTap, GestureBinding(GestureAction.LaunchApp, "pkg.a/pkg.a.Main"))
        assertEquals(GestureBinding(GestureAction.LaunchApp, "pkg.a/pkg.a.Main"), repo.gesture(Gesture.DoubleTap))
    }

    @Test fun theSwipeThatOpensTheListIsAlwaysAppList() {
        repo.swipe = SwipeDir.Left
        assertEquals(GestureAction.AppList, repo.gesture(Gesture.SwipeLeft).action)
        repo.setGesture(Gesture.SwipeLeft, GestureBinding(GestureAction.Flashlight))   // ignored while it opens the list
        assertEquals(GestureAction.AppList, repo.gesture(Gesture.SwipeLeft).action)
        repo.swipe = SwipeDir.Up
        assertEquals(GestureAction.Flashlight, repo.gesture(Gesture.SwipeLeft).action)
        assertEquals(GestureAction.AppList, repo.gesture(Gesture.SwipeUp).action)
    }

    @Test fun renamingKeepsTheOriginalForSearchAndResorts() {
        val raw = listOf(AppEntry("Alarms", "a", "A"), AppEntry("Camera", "c", "C"), AppEntry("Timer", "z", "Z"))
        val named = applyAliases(raw, mapOf("z/Z" to "Meetings", "a/A" to "Alert"))
        assertEquals(listOf("Alert", "Camera", "Meetings"), named.map { it.label })
        assertEquals(listOf("Alarms", "Camera", "Timer"), named.map { it.original })
        assertTrue(matchesQuery(named.first(), "alar"))      // still found by its original name
        assertTrue(matchesQuery(named.first(), "ale"))       // and by the new one
        assertEquals(raw, applyAliases(raw, emptyMap()))
    }

    @Test fun aliasStorageAndAgentPlan() {
        repo.setAlias("a/A", "  Two   factor ")
        assertEquals(mapOf("a/A" to "Two factor"), repo.aliases())
        repo.setAlias("a/A", "  ")
        assertEquals(emptyMap<String, String>(), repo.aliases())
        AgentApi.apply(repo, JSONObject("""{"aliases":{"a/A":"Codes","x/X":"Ghost"}}"""), known)
        assertEquals(mapOf("a/A" to "Codes"), repo.aliases())
    }

    @Test fun absentSectionsAreLeftAlone() {
        AgentApi.apply(repo, JSONObject("""{"favourites":["a/A"]}"""), known)
        AgentApi.apply(repo, JSONObject("""{"hidden":["b/B"]}"""), known)
        assertEquals(listOf("a/A"), repo.favourites())
        assertEquals(setOf("b/B"), repo.hidden())
    }
}
