package com.dk.appblocker

/** Display coordinates only: never place a blocking surface over recovery UI or system bars. */
object PaneProtection {
    data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width get() = right - left
        val height get() = bottom - top
        fun intersect(other: Bounds): Bounds? = Bounds(maxOf(left, other.left), maxOf(top, other.top),
            minOf(right, other.right), minOf(bottom, other.bottom)).takeIf { it.width > 0 && it.height > 0 }
        fun subtract(other: Bounds): List<Bounds> {
            val cut = intersect(other) ?: return listOf(this)
            return listOf(Bounds(left, top, right, cut.top), Bounds(left, cut.bottom, right, bottom),
                Bounds(left, cut.top, cut.left, cut.bottom), Bounds(cut.right, cut.top, right, cut.bottom))
                .filter { it.width > 0 && it.height > 0 }
        }
    }

    fun regions(blocked: Bounds, recovery: List<Bounds>, safeDisplay: Bounds): List<Bounds> {
        var pieces = listOfNotNull(blocked.intersect(safeDisplay))
        for (safe in recovery) {
            pieces = pieces.flatMap { it.subtract(safe) }
            if (pieces.size > 64) return emptyList() // Recovery has priority over an ambiguous layout.
        }
        return pieces
    }
}
