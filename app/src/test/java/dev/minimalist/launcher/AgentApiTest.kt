package dev.minimalist.launcher

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertEquals
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

    @Test fun absentSectionsAreLeftAlone() {
        AgentApi.apply(repo, JSONObject("""{"favourites":["a/A"]}"""), known)
        AgentApi.apply(repo, JSONObject("""{"hidden":["b/B"]}"""), known)
        assertEquals(listOf("a/A"), repo.favourites())
        assertEquals(setOf("b/B"), repo.hidden())
    }
}
