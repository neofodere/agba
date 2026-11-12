package dev.neofodere.agba.core

import dev.neofodere.agba.R

enum class Platform(
    val id: String,
    val label: String,
    val shortLabel: String,
    val extensions: Set<String>,
    val iconRes: Int,
) {
    GB("gb", "GB", "GB", setOf("gb"), R.drawable.ic_console_gb),
    GBC("gbc", "GBC", "GBC", setOf("gbc", "gb"), R.drawable.ic_console_gbc),
    GBA("gba", "GBA", "GBA", setOf("gba", "agb"), R.drawable.ic_console_gba);

    val hasShoulders: Boolean get() = this == GBA

    companion object {
        fun fromId(id: String?): Platform? = entries.firstOrNull { it.id == id }
    }
}

object Keys {
    const val A = 1 shl 0
    const val B = 1 shl 1
    const val SELECT = 1 shl 2
    const val START = 1 shl 3
    const val RIGHT = 1 shl 4
    const val LEFT = 1 shl 5
    const val UP = 1 shl 6
    const val DOWN = 1 shl 7
    const val R = 1 shl 8
    const val L = 1 shl 9
}
