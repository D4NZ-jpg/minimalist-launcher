package dev.minimalist.launcher

import org.json.JSONArray
import org.json.JSONObject

/**
 * Read and rewrite the launcher layout as JSON. Used by [AgentReceiver] so a script or
 * coding agent can organise apps over ADB.
 */
object AgentApi {
    fun list(repo: AppRepository): JSONObject {
        val apps = repo.loadApps()
        val members = repo.folderMembers()
        val folders = JSONObject()
        repo.folderNames().forEach { folders.put(it, JSONArray(members[it] ?: emptyList<String>())) }
        return JSONObject()
            .put("apps", JSONArray().also { arr -> apps.forEach { arr.put(JSONObject().put("key", it.key).put("label", it.label)) } })
            .put("favourites", JSONArray(repo.favourites()))
            .put("dock", JSONArray(repo.dock()))
            .put("hidden", JSONArray(repo.hidden().sorted()))
            .put("folders", folders)
            .put("homeFolders", JSONArray(repo.homeFolders()))
            .put("swipe", repo.swipe.name)
    }

    /**
     * Applies a plan. Sections that are present replace the current value, sections that are
     * absent are left alone. Keys that are not installed apps are dropped and reported.
     */
    fun apply(repo: AppRepository, plan: JSONObject, knownKeys: Set<String>): JSONObject {
        val unknown = linkedSetOf<String>()
        fun keys(a: JSONArray): List<String> = (0 until a.length()).map { a.getString(it) }.filter { k ->
            (k in knownKeys).also { if (!it) unknown += k }
        }
        val applied = JSONObject()
        plan.optJSONArray("favourites")?.let { repo.replaceFavourites(keys(it)); applied.put("favourites", true) }
        plan.optJSONArray("dock")?.let { repo.replaceDock(keys(it)); applied.put("dock", true) }
        plan.optJSONArray("hidden")?.let { repo.replaceHidden(keys(it)); applied.put("hidden", true) }
        plan.optJSONObject("folders")?.let { f ->
            repo.replaceFolders(f.keys().asSequence().associateWith { keys(f.getJSONArray(it)) })
            applied.put("folders", true)
        }
        plan.optJSONArray("homeFolders")?.let { a ->
            val names = (0 until a.length()).map { a.getString(it) }.filter { it in repo.folderNames() }
            repo.replaceHomeFolders(names); applied.put("homeFolders", true)
        }
        return JSONObject().put("ok", true).put("applied", applied).put("unknownKeys", JSONArray(unknown.toList()))
    }
}
