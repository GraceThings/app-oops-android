package io.github.gracethings.appoops.data.model

enum class RuleLevel(val key: String) {
    GENTLE("gentle"),
    BALANCED("balanced"),
    STRICT("strict");

    companion object {
        fun fromKey(raw: String?): RuleLevel {
            return when (raw?.trim()?.lowercase()) {
                "gentle", "mild", "light" -> GENTLE
                "strict", "aggressive", "hard" -> STRICT
                else -> BALANCED
            }
        }
    }
}

data class RuleSubscription(
    val id: String,
    val name: String,
    val url: String,
    val repoUrl: String? = null,
    val isEnabled: Boolean = true,
    val lastUpdated: Long = 0L,
    val ruleCount: Int = 0,
    val lastError: String? = null
)

data class CommunityRule(
    val id: String,
    val subscriptionId: String,
    val subscriptionName: String,
    val packageName: String,
    val title: String,
    val description: String,
    val author: String,
    val level: RuleLevel = RuleLevel.BALANCED,
    val recommended: Boolean = false,
    val updatedAt: String = "",
    val path: String? = null,
    val ops: Map<String, OpMode> = emptyMap(),
    val unsupportedOpsCount: Int = 0
)

data class AppRuleGroup(
    val packageName: String,
    val displayAppName: String,
    val installedAppItem: AppItem,
    val rules: List<CommunityRule>
)

data class AppliedRuleInfo(
    val ruleId: String,
    val ruleTitle: String,
    val subscriptionId: String = "",
    val subscriptionName: String = "",
    val appliedAt: Long = 0L
)
