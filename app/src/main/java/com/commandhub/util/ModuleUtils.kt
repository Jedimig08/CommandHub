package com.commandhub.util

import androidx.compose.runtime.snapshots.SnapshotStateList
import com.commandhub.data.ModuleConfig

fun moveModule(
    modules: SnapshotStateList<ModuleConfig>,
    fromIndex: Int,
    toIndex: Int,
) {
    val item = modules.removeAt(fromIndex)
    modules.add(toIndex, item)
}
