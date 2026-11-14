package com.moyeon.redcurb.engine

import kotlin.math.cos
import kotlin.math.floor

/**
 * 0.5km 격자 해시맵. 앱 시작 때 한 번 적재하고, P 때 주변 9칸만 본다(명세 6).
 * 격자는 서울 위도(37.5도) 기준 등장방형 근사: 경도 1도 ≈ 111.32km·cos(37.5°).
 */
class TargetIndex(private val cellMeters: Double = 500.0) {
    private val cells = HashMap<Long, MutableList<Target>>()
    private val byId = HashMap<String, Target>()
    val size get() = byId.size

    private val mPerDegLat = 110_574.0
    private val mPerDegLon = 111_320.0 * cos(Math.toRadians(37.5))

    private fun cx(lon: Double) = floor(lon * mPerDegLon / cellMeters).toLong()
    private fun cy(lat: Double) = floor(lat * mPerDegLat / cellMeters).toLong()
    private fun key(x: Long, y: Long) = (y shl 32) xor (x and 0xffffffffL)

    fun add(t: Target) {
        byId[t.id] = t
        cells.getOrPut(key(cx(t.lon), cy(t.lat))) { mutableListOf() }.add(t)
    }

    fun addAll(ts: Iterable<Target>) = ts.forEach(::add)

    fun get(id: String): Target? = byId[id]

    /** 주변 9칸의 후보 */
    fun candidates(lat: Double, lon: Double): List<Target> {
        val x = cx(lon)
        val y = cy(lat)
        val out = ArrayList<Target>()
        for (dy in -1L..1L) for (dx in -1L..1L) cells[key(x + dx, y + dy)]?.let(out::addAll)
        return out
    }

    companion object {
        /** assets/hydrants.csv: id,lat,lon,type,detail,usable (머리글 1줄). 깨진 줄은 건너뛴다. */
        fun parseCsv(lines: Sequence<String>): List<Target> = lines.drop(1).mapNotNull { line ->
            val c = line.split(',')
            if (c.size < 6) return@mapNotNull null
            val lat = c[1].toDoubleOrNull() ?: return@mapNotNull null
            val lon = c[2].toDoubleOrNull() ?: return@mapNotNull null
            val type = FacilityType.parse(c[3]) ?: return@mapNotNull null
            Target(c[0], lat, lon, type, c[4], c[5])
        }.toList()
    }
}
