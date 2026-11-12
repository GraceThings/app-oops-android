package io.github.gracethings.appoops.data.repository

import android.content.Context
import android.net.Uri
import android.util.Base64
import io.github.gracethings.appoops.data.model.CommunityRule
import io.github.gracethings.appoops.data.model.OpMode
import io.github.gracethings.appoops.data.model.RuleLevel
import io.github.gracethings.appoops.data.model.RuleSubscription
import io.github.gracethings.appoops.data.preference.SubscriptionPreferences
import io.github.gracethings.appoops.data.shizuku.AppOpsController
import io.github.gracethings.appoops.util.AppLogger
import io.github.gracethings.appoops.util.AppOpsConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

object CommunityRuleRepository {

    private const val MAX_RESPONSE_BYTES = 5 * 1024 * 1024 // 5 MB safety cap
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 12_000

    private val _rulesFlow = MutableStateFlow<List<CommunityRule>>(emptyList())
    val rulesFlow: StateFlow<List<CommunityRule>> = _rulesFlow.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /**
     * Loads cached subscription indexes from internal storage without making network requests.
     */
    fun loadCachedRules(context: Context) {
        val subs = SubscriptionPreferences.subscriptions.value
        val aggregated = mutableListOf<CommunityRule>()
        for (sub in subs) {
            if (!sub.isEnabled) continue
            val cached = SubscriptionPreferences.readCachedJson(context, sub.id) ?: continue
            runCatching {
                val parsed = parseIndexJson(cached, sub)
                aggregated.addAll(parsed.rules)
            }.onFailure {
                AppLogger.w("Failed parsing cached JSON for subscription ${sub.name}: ${it.message}")
            }
        }
        _rulesFlow.value = sortRules(aggregated)
    }

    /**
     * Adds a new subscription by validating & fetching its rules immediately.
     */
    suspend fun addSubscription(
        context: Context,
        rawUrlInput: String,
        customName: String? = null
    ): Result<RuleSubscription> = withContext(Dispatchers.IO) {
        val trimmed = rawUrlInput.trim()
        if (trimmed.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Subscription link cannot be empty"))
        }

        val candidates = resolveCandidateUrls(trimmed)
        if (candidates.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Invalid subscription URL format"))
        }

        // Prevent duplicate URLs
        val existing = SubscriptionPreferences.subscriptions.value
        val normalizedCanonical = candidates.first()
        if (existing.any { it.url.equals(trimmed, ignoreCase = true) || resolveCandidateUrls(it.url).firstOrNull() == normalizedCanonical }) {
            return@withContext Result.failure(IllegalStateException("This subscription URL already exists"))
        }

        val subId = UUID.randomUUID().toString()
        val inferredName = customName?.trim()?.takeIf { it.isNotEmpty() } ?: inferRepoDisplayName(trimmed)

        val fetchRes = fetchFromCandidates(candidates)
        if (fetchRes.isFailure) {
            return@withContext Result.failure(fetchRes.exceptionOrNull() ?: Exception("Fetch failed"))
        }

        val (fetchedJson, resolvedUrl) = fetchRes.getOrThrow()
        val draftSub = RuleSubscription(
            id = subId,
            name = inferredName,
            url = trimmed,
            repoUrl = inferRepoWebUrl(trimmed),
            isEnabled = true,
            lastUpdated = System.currentTimeMillis()
        )

        val parseRes = runCatching { parseIndexJson(fetchedJson, draftSub) }
        if (parseRes.isFailure) {
            return@withContext Result.failure(
                IllegalArgumentException("Invalid rules format: ${parseRes.exceptionOrNull()?.message}")
            )
        }

        val parsedIndex = parseRes.getOrThrow()
        val finalName = customName?.trim()?.takeIf { it.isNotEmpty() }
            ?: parsedIndex.repoName?.takeIf { it.isNotBlank() }
            ?: inferredName

        val finalSub = draftSub.copy(
            name = finalName,
            url = if (trimmed.startsWith("http", ignoreCase = true)) trimmed else resolvedUrl,
            repoUrl = parsedIndex.repoUrl?.takeIf { it.isNotBlank() } ?: draftSub.repoUrl,
            ruleCount = parsedIndex.rules.size,
            lastError = null
        )

        SubscriptionPreferences.writeCachedJson(context, subId, fetchedJson)
        val updatedList = existing + finalSub
        SubscriptionPreferences.saveSubscriptions(context, updatedList)
        loadCachedRules(context)

        Result.success(finalSub)
    }

    /**
     * Refreshes one or all enabled subscriptions from the network.
     */
    suspend fun refreshSubscriptions(context: Context, targetSubId: String? = null): Result<Int> = withContext(Dispatchers.IO) {
        _isRefreshing.value = true
        try {
            val currentList = SubscriptionPreferences.subscriptions.value.toMutableList()
            if (currentList.isEmpty()) {
                _rulesFlow.value = emptyList()
                return@withContext Result.success(0)
            }

            var successCount = 0
            var lastError: Throwable? = null

            for (i in currentList.indices) {
                val sub = currentList[i]
                if (targetSubId != null && sub.id != targetSubId) continue
                if (!sub.isEnabled && targetSubId == null) continue

                val candidates = resolveCandidateUrls(sub.url)
                val fetchRes = fetchFromCandidates(candidates)
                if (fetchRes.isSuccess) {
                    val (jsonText, _) = fetchRes.getOrThrow()
                    val parseRes = runCatching { parseIndexJson(jsonText, sub) }
                    if (parseRes.isSuccess) {
                        val parsed = parseRes.getOrThrow()
                        SubscriptionPreferences.writeCachedJson(context, sub.id, jsonText)
                        currentList[i] = sub.copy(
                            name = parsed.repoName?.takeIf { it.isNotBlank() } ?: sub.name,
                            repoUrl = parsed.repoUrl?.takeIf { it.isNotBlank() } ?: sub.repoUrl,
                            lastUpdated = System.currentTimeMillis(),
                            ruleCount = parsed.rules.size,
                            lastError = null
                        )
                        successCount++
                    } else {
                        val err = parseRes.exceptionOrNull()
                        lastError = err
                        currentList[i] = sub.copy(lastError = err?.message ?: "Parse error")
                    }
                } else {
                    val err = fetchRes.exceptionOrNull()
                    lastError = err
                    currentList[i] = sub.copy(lastError = err?.message ?: "Network error")
                }
            }

            SubscriptionPreferences.saveSubscriptions(context, currentList)
            loadCachedRules(context)

            if (successCount == 0 && lastError != null) {
                Result.failure(lastError)
            } else {
                Result.success(successCount)
            }
        } finally {
            _isRefreshing.value = false
        }
    }

    fun toggleSubscription(context: Context, subId: String, enabled: Boolean) {
        val current = SubscriptionPreferences.subscriptions.value.map {
            if (it.id == subId) it.copy(isEnabled = enabled) else it
        }
        SubscriptionPreferences.saveSubscriptions(context, current)
        loadCachedRules(context)
    }

    fun removeSubscription(context: Context, subId: String) {
        val current = SubscriptionPreferences.subscriptions.value.filterNot { it.id == subId }
        SubscriptionPreferences.deleteCachedJson(context, subId)
        SubscriptionPreferences.saveSubscriptions(context, current)
        loadCachedRules(context)
    }

    /**
     * If a subscription index omitted inline `ops` and only provided `path` or external URL,
     * fetches the individual rule JSON on demand.
     */
    suspend fun resolveRuleOpsIfNeeded(rule: CommunityRule): Result<CommunityRule> = withContext(Dispatchers.IO) {
        if (rule.ops.isNotEmpty() || rule.path.isNullOrBlank()) {
            return@withContext Result.success(rule)
        }
        val sub = SubscriptionPreferences.subscriptions.value.firstOrNull { it.id == rule.subscriptionId }
            ?: return@withContext Result.failure(IllegalStateException("Subscription not found"))

        val targetUrls = resolveChildRuleUrls(sub.url, rule.path)
        val fetchRes = fetchFromCandidates(targetUrls)
        if (fetchRes.isFailure) {
            return@withContext Result.failure(fetchRes.exceptionOrNull() ?: Exception("Failed to load rule detail"))
        }

        return@withContext runCatching {
            val obj = JSONObject(fetchRes.getOrThrow().first)
            val opsJson = obj.optJSONObject("ops")
                ?: obj.optJSONObject("permissions")
                ?: obj.optJSONObject("rules")
                ?: throw IllegalArgumentException("Rule JSON missing 'ops' object")
            val (sanitized, unsupported) = sanitizeOps(opsJson)
            val updated = rule.copy(
                description = obj.optString("description", rule.description),
                ops = sanitized,
                unsupportedOpsCount = unsupported
            )
            // Update in memory list
            _rulesFlow.value = _rulesFlow.value.map {
                if (it.id == rule.id && it.subscriptionId == rule.subscriptionId) updated else it
            }
            updated
        }
    }

    /**
     * Applies all safe whitelisted AppOp rules of a [CommunityRule] to an installed app.
     */
    suspend fun applyRule(
        context: Context,
        rule: CommunityRule,
        appUid: Int,
        resetBeforeApply: Boolean = false
    ): Result<Int> = withContext(Dispatchers.IO) {
        val hydratedRes = resolveRuleOpsIfNeeded(rule)
        if (hydratedRes.isFailure) {
            return@withContext Result.failure(hydratedRes.exceptionOrNull() ?: Exception("Failed to resolve rule"))
        }
        val finalRule = hydratedRes.getOrThrow()
        if (finalRule.ops.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("This rule has no supported permissions to apply"))
        }

        if (resetBeforeApply) {
            AppOpsController.resetToDefault(finalRule.packageName, appUid)
        }

        var appliedCount = 0
        var lastError: Throwable? = null

        for ((opName, mode) in finalRule.ops) {
            val opCode = AppOpsConstants.getOpCode(opName) ?: continue
            val res = AppOpsController.setMode(
                packageName = finalRule.packageName,
                uid = appUid,
                opCode = opCode,
                opName = opName,
                mode = mode
            )
            if (res.isSuccess) {
                appliedCount++
            } else {
                lastError = res.exceptionOrNull()
            }
        }

        AppRepository.loadApps(context, forceRefresh = true)

        if (appliedCount == 0 && lastError != null) {
            Result.failure(lastError)
        } else {
            Result.success(appliedCount)
        }
    }

    // ---- Rule Export & Issue URL Builders (Contribution Flow 1) ----

    fun buildRuleJson(
        ruleId: String,
        packageName: String,
        title: String,
        description: String,
        author: String,
        level: RuleLevel,
        ops: Map<String, OpMode>
    ): String {
        val cleanId = ruleId.trim()
            .lowercase()
            .replace(Regex("[^a-z0-9._-]"), "-")
            .trim('-')
            .ifEmpty { "custom-rule" }

        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val opsJson = JSONObject()
        ops.forEach { (opName, mode) ->
            if (AppOpsConstants.getOpCode(opName) != null) {
                opsJson.put(opName, mode.name)
            }
        }

        return JSONObject().apply {
            put("id", cleanId)
            put("packageName", packageName)
            put("title", title.trim())
            put("description", description.trim())
            put("author", author.trim().ifEmpty { "Anonymous" })
            put("level", level.key)
            put("updatedAt", dateStr)
            put("ops", opsJson)
        }.toString(2)
    }

    fun buildGitHubIssueUrl(
        packageName: String,
        appName: String,
        ruleTitle: String,
        ruleJson: String
    ): String {
        val issueTitle = "[Rule] $appName ($packageName) - $ruleTitle"
        return "https://github.com/${SubscriptionPreferences.OFFICIAL_OWNER}/${SubscriptionPreferences.OFFICIAL_REPO}/issues/new" +
            "?template=submit_rule.yml" +
            "&labels=rule-submission" +
            "&title=${Uri.encode(issueTitle)}" +
            "&package_name=${Uri.encode(packageName)}" +
            "&rule_json=${Uri.encode(ruleJson)}"
    }

    // ---- Internal Parsing & Network Helpers ----

    private data class ParsedIndex(
        val repoName: String?,
        val repoUrl: String?,
        val rules: List<CommunityRule>
    )

    private fun parseIndexJson(jsonText: String, sub: RuleSubscription): ParsedIndex {
        val trimmed = jsonText.trim()
        var repoName: String? = null
        var repoUrl: String? = null

        val rulesArray: JSONArray = if (trimmed.startsWith("[")) {
            JSONArray(trimmed)
        } else {
            val root = JSONObject(trimmed)
            repoName = root.optString("name").takeIf { it.isNotBlank() }
            repoUrl = root.optString("repoUrl").takeIf { it.isNotBlank() }
            root.optJSONArray("rules") ?: JSONArray()
        }

        val list = mutableListOf<CommunityRule>()
        for (i in 0 until rulesArray.length()) {
            val obj = rulesArray.optJSONObject(i) ?: continue
            val pkg = obj.optString("packageName").trim()
            if (pkg.isBlank() || !pkg.contains('.') || pkg.contains(' ') || pkg.contains('/')) {
                continue
            }

            val id = obj.optString("id").trim().ifEmpty { "${pkg}_$i" }
            val title = obj.optString("title").trim().ifEmpty { "Community Preset" }
            val description = obj.optString("description").trim()
            val author = obj.optString("author").trim().ifEmpty { "Community" }
            val level = RuleLevel.fromKey(obj.optString("level"))
            val recommended = obj.optBoolean("recommended", false)
            val updatedAt = obj.optString("updatedAt").trim()
            val path = obj.optString("path").trim().ifEmpty {
                obj.optString("url").trim().ifEmpty { null }
            }

            val opsObj = obj.optJSONObject("ops")
                ?: obj.optJSONObject("permissions")
                ?: obj.optJSONObject("rules")

            val (ops, unsupported) = if (opsObj != null) sanitizeOps(opsObj) else (emptyMap<String, OpMode>() to 0)
            // Keep entry if either inline ops exist or a remote path exists
            if (ops.isEmpty() && path.isNullOrBlank()) continue

            list.add(
                CommunityRule(
                    id = id,
                    subscriptionId = sub.id,
                    subscriptionName = repoName ?: sub.name,
                    packageName = pkg,
                    title = title,
                    description = description,
                    author = author,
                    level = level,
                    recommended = recommended,
                    updatedAt = updatedAt,
                    path = path,
                    ops = ops,
                    unsupportedOpsCount = unsupported
                )
            )
        }

        return ParsedIndex(repoName = repoName, repoUrl = repoUrl, rules = list)
    }

    private fun sanitizeOps(opsObj: JSONObject): Pair<Map<String, OpMode>, Int> {
        val clean = linkedMapOf<String, OpMode>()
        var unsupportedCount = 0
        val keys = opsObj.keys()
        while (keys.hasNext()) {
            val rawKey = keys.next()
            val canonicalOpName = rawKey.trim().uppercase()
            val rawMode = opsObj.optString(rawKey).trim().uppercase()

            if (AppOpsConstants.getOpCode(canonicalOpName) == null) {
                unsupportedCount++
                continue
            }

            val parsedMode = when (rawMode) {
                "ALLOW", "ALLOWED", "0" -> OpMode.ALLOW
                "IGNORE", "IGNORED", "1" -> OpMode.IGNORE
                "DENY", "ERROR", "ERRORED", "2" -> OpMode.DENY
                "DEFAULT", "3" -> OpMode.DEFAULT
                else -> {
                    unsupportedCount++
                    null
                }
            }
            if (parsedMode != null) {
                clean[canonicalOpName] = parsedMode
            }
        }
        return clean to unsupportedCount
    }

    private fun sortRules(rules: List<CommunityRule>): List<CommunityRule> {
        return rules.sortedWith(
            compareByDescending<CommunityRule> { it.recommended }
                .thenByDescending { it.updatedAt }
                .thenBy { it.title }
        )
    }

    /**
     * Converts shorthand `Owner/Repo`, GitHub homepage, GitHub `/blob/` URL,
     * Gitee homepage, or direct JSON URLs into ordered fallback candidates.
     */
    fun resolveCandidateUrls(input: String): List<String> {
        val raw = input.trim()
        if (raw.isEmpty()) return emptyList()

        // Disallow non-http schemes when a scheme is supplied
        if (raw.contains("://") &&
            !raw.startsWith("https://", ignoreCase = true) &&
            !raw.startsWith("http://", ignoreCase = true)
        ) {
            return emptyList()
        }

        // Shorthand `Owner/Repo`
        val shorthandRegex = Regex("^([a-zA-Z0-9_.-]+)/([a-zA-Z0-9_.-]+)$")
        shorthandRegex.matchEntire(raw)?.let { match ->
            val owner = match.groupValues[1]
            val repo = match.groupValues[2].removeSuffix(".git")
            return listOf(
                "https://raw.githubusercontent.com/$owner/$repo/main/index.json",
                "https://cdn.jsdelivr.net/gh/$owner/$repo@main/index.json",
                "https://raw.githubusercontent.com/$owner/$repo/master/index.json",
                "https://gitee.com/api/v5/repos/$owner/$repo/contents/index.json"
            )
        }

        val cleaned = raw.trimEnd('/').removeSuffix(".git")

        // GitHub homepage: https://github.com/owner/repo
        val ghHome = Regex("^https?://github\\.com/([^/]+)/([^/]+)$", RegexOption.IGNORE_CASE).matchEntire(cleaned)
        if (ghHome != null) {
            val owner = ghHome.groupValues[1]
            val repo = ghHome.groupValues[2]
            return listOf(
                "https://raw.githubusercontent.com/$owner/$repo/main/index.json",
                "https://cdn.jsdelivr.net/gh/$owner/$repo@main/index.json",
                "https://raw.githubusercontent.com/$owner/$repo/master/index.json"
            )
        }

        // GitHub blob link: https://github.com/owner/repo/blob/branch/path
        val ghBlob = Regex("^https?://github\\.com/([^/]+)/([^/]+)/blob/([^/]+)/(.+)$", RegexOption.IGNORE_CASE).matchEntire(cleaned)
        if (ghBlob != null) {
            val (owner, repo, branch, path) = ghBlob.destructured
            return listOf(
                "https://raw.githubusercontent.com/$owner/$repo/$branch/$path",
                "https://cdn.jsdelivr.net/gh/$owner/$repo@$branch/$path"
            )
        }

        // GitHub raw link -> add jsDelivr fallback
        val ghRaw = Regex("^https?://raw\\.githubusercontent\\.com/([^/]+)/([^/]+)/([^/]+)/(.+)$", RegexOption.IGNORE_CASE).matchEntire(cleaned)
        if (ghRaw != null) {
            val (owner, repo, branch, path) = ghRaw.destructured
            return listOf(
                raw,
                "https://cdn.jsdelivr.net/gh/$owner/$repo@$branch/$path"
            )
        }

        // Gitee homepage: https://gitee.com/owner/repo
        val giteeHome = Regex("^https?://gitee\\.com/([^/]+)/([^/]+)$", RegexOption.IGNORE_CASE).matchEntire(cleaned)
        if (giteeHome != null) {
            val owner = giteeHome.groupValues[1]
            val repo = giteeHome.groupValues[2]
            return listOf(
                "https://gitee.com/api/v5/repos/$owner/$repo/contents/index.json",
                "https://gitee.com/$owner/$repo/raw/main/index.json",
                "https://gitee.com/$owner/$repo/raw/master/index.json"
            )
        }

        // Gitee blob/raw link -> prefer Gitee API v5 + raw
        val giteeFile = Regex("^https?://gitee\\.com/([^/]+)/([^/]+)/(?:blob|raw)/([^/]+)/(.+)$", RegexOption.IGNORE_CASE).matchEntire(cleaned)
        if (giteeFile != null) {
            val (owner, repo, branch, path) = giteeFile.destructured
            return listOf(
                "https://gitee.com/api/v5/repos/$owner/$repo/contents/$path?ref=$branch",
                "https://gitee.com/$owner/$repo/raw/$branch/$path"
            )
        }

        return listOf(raw)
    }

    private fun resolveChildRuleUrls(subInputUrl: String, childPath: String): List<String> {
        if (childPath.startsWith("http://", ignoreCase = true) || childPath.startsWith("https://", ignoreCase = true)) {
            return resolveCandidateUrls(childPath)
        }
        val cleanChild = childPath.trimStart('/')
        val parentCandidates = resolveCandidateUrls(subInputUrl)
        val out = mutableListOf<String>()
        for (parent in parentCandidates) {
            when {
                parent.contains("gitee.com/api/v5/repos/") -> {
                    val base = parent.substringBefore("/contents/")
                    out.add("$base/contents/$cleanChild")
                }
                parent.endsWith(".json", ignoreCase = true) -> {
                    val base = parent.substringBeforeLast('/')
                    out.add("$base/$cleanChild")
                }
                else -> {
                    out.add("${parent.trimEnd('/')}/$cleanChild")
                }
            }
        }
        return out.distinct()
    }

    private fun inferRepoDisplayName(input: String): String {
        val cleaned = input.trim().trimEnd('/').removeSuffix(".git")
        val gh = Regex("github\\.com/([^/]+/[^/]+)").find(cleaned)?.groupValues?.get(1)
        if (gh != null) return gh
        val gt = Regex("gitee\\.com/([^/]+/[^/]+)").find(cleaned)?.groupValues?.get(1)
        if (gt != null) return "$gt (Gitee)"
        val jsd = Regex("cdn\\.jsdelivr\\.net/gh/([^/@]+/[^/@]+)").find(cleaned)?.groupValues?.get(1)
        if (jsd != null) return "$jsd (CDN)"
        return Uri.parse(cleaned).host ?: cleaned
    }

    private fun inferRepoWebUrl(input: String): String? {
        val cleaned = input.trim().trimEnd('/').removeSuffix(".git")
        Regex("(?:raw\\.githubusercontent|github)\\.com/([^/]+)/([^/]+)").find(cleaned)?.let {
            return "https://github.com/${it.groupValues[1]}/${it.groupValues[2]}"
        }
        Regex("gitee\\.com/(?:api/v5/repos/)?([^/]+)/([^/]+)").find(cleaned)?.let {
            return "https://gitee.com/${it.groupValues[1]}/${it.groupValues[2]}"
        }
        Regex("cdn\\.jsdelivr\\.net/gh/([^/@]+)/([^/@]+)").find(cleaned)?.let {
            return "https://github.com/${it.groupValues[1]}/${it.groupValues[2]}"
        }
        return null
    }

    private fun fetchFromCandidates(candidates: List<String>): Result<Pair<String, String>> {
        var lastError: Throwable? = null
        for (url in candidates) {
            val res = httpGetString(url)
            if (res.isSuccess) {
                val rawBody = res.getOrThrow()
                val decoded = unwrapContentsApiIfNeeded(rawBody)
                if (decoded != null) {
                    return Result.success(decoded to url)
                } else {
                    lastError = IllegalArgumentException("Unexpected response body from $url")
                }
            } else {
                lastError = res.exceptionOrNull()
            }
        }
        return Result.failure(lastError ?: Exception("Failed to download from all sources"))
    }

    /**
     * Automatically unwraps Gitee API v5 / GitHub Contents API JSON wrapper if `encoding == "base64"`.
     */
    private fun unwrapContentsApiIfNeeded(body: String): String? {
        val trimmed = body.trim()
        if (trimmed.startsWith("{")) {
            val maybeObj = runCatching { JSONObject(trimmed) }.getOrNull() ?: return null
            if (maybeObj.optString("type") == "file" &&
                maybeObj.optString("encoding").equals("base64", ignoreCase = true) &&
                maybeObj.has("content")
            ) {
                val b64 = maybeObj.optString("content").replace("\n", "").replace("\r", "")
                return runCatching {
                    String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8)
                }.getOrNull()
            }
        }
        return if (trimmed.startsWith("{") || trimmed.startsWith("[")) trimmed else null
    }

    private fun httpGetString(urlString: String, redirectDepth: Int = 0): Result<String> {
        if (redirectDepth > 3) return Result.failure(Exception("Too many redirects"))
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(urlString)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "AppOops-Android/1.0")
                setRequestProperty("Accept", "application/json, text/plain, */*")
            }
            val code = conn.responseCode
            if (code in 301..308) {
                val location = conn.getHeaderField("Location")
                    ?: return Result.failure(Exception("Redirect without Location header"))
                val nextUrl = URL(url, location).toString()
                return httpGetString(nextUrl, redirectDepth + 1)
            }
            if (code !in 200..299) {
                return Result.failure(Exception("HTTP $code from ${url.host}"))
            }

            BufferedInputStream(conn.inputStream).use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    total += read
                    if (total > MAX_RESPONSE_BYTES) {
                        return Result.failure(Exception("Response exceeds 5 MB limit"))
                    }
                    out.write(buffer, 0, read)
                }
                Result.success(out.toString(Charsets.UTF_8.name()))
            }
        } catch (e: Throwable) {
            Result.failure(e)
        } finally {
            conn?.disconnect()
        }
    }
}
