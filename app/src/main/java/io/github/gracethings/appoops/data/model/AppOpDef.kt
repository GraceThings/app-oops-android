package io.github.gracethings.appoops.data.model

import androidx.annotation.StringRes

data class AppOpDef(
    val opCode: Int,
    val opName: String,
    val title: String,
    val description: String,
    val category: OpCategory,
    val isRiskDefault: Boolean = false,
    @StringRes val titleRes: Int? = null,
    @StringRes val descRes: Int? = null
)

