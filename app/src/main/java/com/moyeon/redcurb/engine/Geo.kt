package com.moyeon.redcurb.engine

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

object Geo {
    const val EARTH_R = 6_371_008.8
    const val ALERT_R = 12.0
    const val CLEAR_R = 8.0
    /** 부동소수 오차 허용(5cm). 경계 판정과 0.5m 올림에만 쓴다. */
    const val EPS = 0.05

    fun outsideClear(d: Double) = d >= CLEAR_R - EPS

    fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dp = p2 - p1
        val dl = Math.toRadians(lon2 - lon1)
        val h = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return 2 * EARTH_R * asin(min(1.0, sqrt(h)))
    }

    /** 0..360, 북=0 시계방향 */
    fun bearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dl = Math.toRadians(lon2 - lon1)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    /** -180..180 */
    fun normalize(deg: Double): Double {
        var d = deg % 360.0
        if (d > 180.0) d -= 360.0
        if (d <= -180.0) d += 360.0
        return d
    }

    /** (lat,lon) 에서 bearingDeg 방향으로 meters 만큼 간 점. 시나리오·테스트용. */
    fun offset(lat: Double, lon: Double, bearingDeg: Double, meters: Double): Pair<Double, Double> {
        val br = Math.toRadians(bearingDeg)
        val dr = meters / EARTH_R
        val p1 = Math.toRadians(lat)
        val l1 = Math.toRadians(lon)
        val p2 = asin(sin(p1) * cos(dr) + cos(p1) * sin(dr) * cos(br))
        val l2 = l1 + atan2(sin(br) * sin(dr) * cos(p1), cos(dr) - sin(p1) * sin(p2))
        return Math.toDegrees(p2) to Math.toDegrees(l2)
    }

    /** 명세 4-3 을 그대로 옮긴 것 */
    fun geometry(car: LocationFix, target: Target): Geometry {
        val d = haversine(car.lat, car.lon, target.lat, target.lon)
        val brg = bearing(car.lat, car.lon, target.lat, target.lon)
        val rel = normalize(brg - car.heading)
        val relRad = Math.toRadians(rel)
        val along = d * cos(relRad)
        val lat = abs(d * sin(relRad))
        val s = sqrt(max(CLEAR_R * CLEAR_R - lat * lat, 0.0))
        val moveBack = s - along
        val moveForward = s + along
        val side = when {
            abs(rel) < 60 -> Geometry.Side.FRONT
            abs(rel) > 120 -> Geometry.Side.BACK
            else -> Geometry.Side.SIDE
        }
        val suggestBack = moveBack <= moveForward
        val suggest = if (outsideClear(d)) null else {
            val raw = min(moveBack, moveForward)
            max(1.0, ceil((raw - EPS) * 2.0) / 2.0)
        }
        return Geometry(d, rel, along, lat, moveBack, moveForward, suggest, suggestBack, side)
    }
}
