package io.github.gracethings.appoops.data.preference

import android.content.Context
import io.github.gracethings.appoops.data.model.AppliedRuleInfo
import io.github.gracethings.appoops.data.model.RuleSubscription
import io.github.gracethings.appoops.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object SubscriptionPreferences {
    private const val PREFS_NAME = "app_oops_subscriptions_prefs"
    private const val KEY_SUBSCRIPTIONS = "key_subscriptions_json"
    private const val KEY_APPLIED_RULES = "key_applied_rules_json"
    private const val CACHE_DIR = "community_rules"

    const val OFFICIAL_OWNER = "GraceThings"
    const val OFFICIAL_REPO = "app-oops-rules"
    const val OFFICIAL_GITHUB_WEB = "https://github.com/GraceThings/app-oops-rules"
    const val OFFICIAL_GITEE_WEB = "https://gitee.com/GraceThings/app-oops-rules"
    const val OFFICIAL_GITHUB_RAW = "https://raw.githubusercontent.com/GraceThings/app-oops-rules/main/index.json"
    const val OFFICIAL_JSDELIVR_URL = "https://cdn.jsdelivr.net/gh/GraceThings/app-oops-rules@main/index.json"
    const val OFFICIAL_GITEE_RAW = "https://gitee.com/GraceThings/app-oops-rules/raw/main/index.json"

    private val _subscriptions = MutableStateFlow<List<RuleSubscription>>(emptyList())
    val subscriptions: StateFlow<List<RuleSubscription>> = _subscriptions.asStateFlow()

    private val _appliedRules = MutableStateFlow<Map<String, AppliedRuleInfo>>(emptyMap())
    val appliedRules: StateFlow<Map<String, AppliedRuleInfo>> = _appliedRules.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadAppliedRules(prefs.getString(KEY_APPLIED_RULES, null))

        val raw = prefs.getString(KEY_SUBSCRIPTIONS, null)
        if (raw.isNullOrBlank()) {
            _subscriptions.value = emptyList()
            return
        }
        val list = mutableListOf<RuleSubscription>()
        runCatching {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val id = obj.optString("id")
                val url = obj.optString("url")
                if (id.isBlank() || url.isBlank()) continue
                list.add(
                    RuleSubscription(
                        id = id,
                        name = obj.optString("name", url),
                        url = url,
                        repoUrl = obj.optString("repoUrl").takeIf { it.isNotBlank() },
                        isEnabled = obj.optBoolean("isEnabled", true),
                        lastUpdated = obj.optLong("lastUpdated", 0L),
                        ruleCount = obj.optInt("ruleCount", 0),
                        lastError = obj.optString("lastError").takeIf { it.isNotBlank() }
                    )
                )
            }
        }.onFailure {
            AppLogger.e("Failed to load subscriptions", it)
        }
        _subscriptions.value = list
    }

    fun saveSubscriptions(context: Context, list: List<RuleSubscription>) {
        _subscriptions.value = list
        val arr = JSONArray()
        list.forEach { sub ->
            val obj = JSONObject().apply {
                put("id", sub.id)
                put("name", sub.name)
                put("url", sub.url)
                if (!sub.repoUrl.isNullOrBlank()) put("repoUrl", sub.repoUrl)
                put("isEnabled", sub.isEnabled)
                put("lastUpdated", sub.lastUpdated)
                put("ruleCount", sub.ruleCount)
                if (!sub.lastError.isNullOrBlank()) put("lastError", sub.lastError)
            }
            arr.put(obj)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SUBSCRIPTIONS, arr.toString())
            .apply()
    }

    private fun getCacheFile(context: Context, subId: String): File {
        val dir = File(context.filesDir, CACHE_DIR)
        if (!dir.exists()) dir.mkdirs()
        val safeName = subId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return File(dir, "$safeName.json")
    }

    fun writeCachedJson(context: Context, subId: String, json: String) {
        runCatching {
            getCacheFile(context, subId).writeText(json, Charsets.UTF_8)
        }.onFailure {
            AppLogger.e("Failed to write rule cache for $subId", it)
        }
    }

    fun readCachedJson(context: Context, subId: String): String? {
        return runCatching {
            val file = getCacheFile(context, subId)
            if (file.exists()) file.readText(Charsets.UTF_8) else null
        }.getOrNull()
    }

    fun deleteCachedJson(context: Context, subId: String) {
        runCatching {
            val file = getCacheFile(context, subId)
            if (file.exists()) file.delete()
        }
    }

    fun recordAppliedRule(context: Context, packageName: String, info: AppliedRuleInfo) {
        val updated = _appliedRules.value.toMutableMap().apply {
            put(packageName, info)
        }
        saveAppliedRules(context, updated)
    }

    fun clearAppliedRule(context: Context, packageName: String) {
        if (!_appliedRules.value.containsKey(packageName)) return
        val updated = _appliedRules.value.toMutableMap().apply {
            remove(packageName)
        }
        saveAppliedRules(context, updated)
    }

    private fun loadAppliedRules(raw: String?) {
        if (raw.isNullOrBlank()) {
            _appliedRules.value = emptyMap()
            return
        }
        val map = mutableMapOf<String, AppliedRuleInfo>()
        runCatching {
            val root = JSONObject(raw)
            val keys = root.keys()
            while (keys.hasNext()) {
                val pkg = keys.next()
                val obj = root.optJSONObject(pkg) ?: continue
                val ruleId = obj.optString("ruleId")
                val ruleTitle = obj.optString("ruleTitle")
                if (ruleId.isBlank() || ruleTitle.isBlank()) continue
                map[pkg] = AppliedRuleInfo(
                    ruleId = ruleId,
                    ruleTitle = ruleTitle,
                    subscriptionId = obj.optString("subscriptionId"),
                    subscriptionName = obj.optString("subscriptionName"),
                    appliedAt = obj.optLong("appliedAt", 0L)
                )
            }
        }.onFailure {
            AppLogger.e("Failed to load applied rules", it)
        }
        _appliedRules.value = map
    }

    private fun saveAppliedRules(context: Context, map: Map<String, AppliedRuleInfo>) {
        _appliedRules.value = map
        val root = JSONObject()
        map.forEach { (pkg, info) ->
            val obj = JSONObject().apply {
                put("ruleId", info.ruleId)
                put("ruleTitle", info.ruleTitle)
                if (info.subscriptionId.isNotBlank()) put("subscriptionId", info.subscriptionId)
                if (info.subscriptionName.isNotBlank()) put("subscriptionName", info.subscriptionName)
                put("appliedAt", info.appliedAt)
            }
            root.put(pkg, obj)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_APPLIED_RULES, root.toString())
            .apply()
    }
}
