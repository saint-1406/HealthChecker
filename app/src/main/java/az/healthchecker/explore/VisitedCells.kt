package az.healthchecker.explore

import kotlin.math.*

/** Sparse, local Web Mercator cells. A segment is interpolated only between accepted contiguous fixes. */
class VisitedCells(val cellMeters: Double = 50.0) {
    private val cells = HashSet<Long>()
    private var previous: Pair<Double, Double>? = null
    val size: Int get() = cells.size

    fun add(latitude: Double, longitude: Double, breakBefore: Boolean) {
        val x = RADIUS * Math.toRadians(longitude)
        val y = RADIUS * ln(tan(Math.PI / 4 + Math.toRadians(latitude.coerceIn(-85.0, 85.0)) / 2))
        val old = previous
        if (!breakBefore && old != null) {
            val steps = ceil(max(abs(x - old.first), abs(y - old.second)) / (cellMeters / 2)).toInt().coerceIn(1, 8192)
            for (i in 0..steps) {
                val t = i.toDouble() / steps
                insert(old.first + (x - old.first) * t, old.second + (y - old.second) * t)
            }
        } else insert(x, y)
        previous = x to y
    }

    private fun insert(x: Double, y: Double) {
        val a = floor(x / cellMeters).toInt(); val b = floor(y / cellMeters).toInt()
        cells.add((a.toLong() shl 32) or (b.toLong() and 0xffffffffL))
    }

    fun centers(): Sequence<Pair<Double, Double>> = cells.asSequence().map { key ->
        val x = ((key shr 32).toInt() + 0.5) * cellMeters
        val y = (key.toInt() + 0.5) * cellMeters
        Math.toDegrees(2 * atan(exp(y / RADIUS)) - Math.PI / 2) to Math.toDegrees(x / RADIUS)
    }

    companion object { private const val RADIUS = 6_378_137.0 }
}
