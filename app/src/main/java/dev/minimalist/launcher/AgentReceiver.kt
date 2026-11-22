package dev.minimalist.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Base64
import org.json.JSONObject

/**
 * Hidden control channel for ADB. It has no UI. The manifest requires android.permission.DUMP,
 * which only the shell user (adb) and the system hold, so other apps cannot send to it.
 *
 * Read the layout:
 *   adb shell am broadcast -n dev.minimalist.launcher/.AgentReceiver -a dev.minimalist.launcher.AGENT --es cmd list
 * Replace sections of the layout (plan is base64 JSON with optional favourites, dock, hidden, folders):
 *   adb shell am broadcast -n dev.minimalist.launcher/.AgentReceiver -a dev.minimalist.launcher.AGENT \
 *     --es cmd apply --es plan "$(base64 < plan.json | tr -d '\n')"
 * The reply is in the `data="..."` part of the broadcast result.
 */
class AgentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val repo = AppRepository(context)
        val reply = runCatching {
            when (val cmd = intent.getStringExtra("cmd")) {
                "list" -> AgentApi.list(repo)
                "apply" -> {
                    val plan = JSONObject(String(Base64.decode(intent.getStringExtra("plan") ?: error("missing plan"), Base64.DEFAULT)))
                    AgentApi.apply(repo, plan, repo.loadApps().map { it.key }.toSet())
                }
                else -> error("unknown cmd: $cmd (use list or apply)")
            }
        }.getOrElse { JSONObject().put("ok", false).put("error", it.message ?: it.toString()) }
        if (isOrderedBroadcast) { resultCode = 0; resultData = reply.toString() }
    }
}
