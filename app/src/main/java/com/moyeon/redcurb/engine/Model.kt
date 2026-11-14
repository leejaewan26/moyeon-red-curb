package com.moyeon.redcurb.engine

/** 엔진이 쓰는 기어. AAOS 표준 GEAR_SELECTION(android.car.VehicleGear) 값을 어댑터가 이것으로 바꿔 넣는다(엔진은 Android·SDK 의존 없음). */
enum class Gear { PARK, DRIVE, REVERSE, NEUTRAL, UNKNOWN }

/** 시설유형(서울 OA-21306 시설유형코드 01~05 를 줄인 것). */
enum class FacilityType(val label: String, val noun: String) {
    GROUND("지상식 소화전", "소화전"),
    UNDERGROUND("지하식 소화전(도로 위 맨홀)", "소화전"),
    TOWER("급수탑", "급수탑"),
    RESERVOIR("저수조", "저수조"),
    FIRE_LANE("소방차 전용구역", "소방차 전용구역");

    companion object {
        fun parse(s: String): FacilityType? = entries.firstOrNull { it.name == s.trim() }
    }
}

/** 알림 대상: 소화전(공공데이터) 또는 내가 등록한 소방차 전용구역. */
data class Target(
    val id: String,
    val lat: Double,
    val lon: Double,
    val type: FacilityType,
    val detail: String,
    val usable: String = "",
    val createdAt: Long = 0L,
) {
    val isFireLane get() = type == FacilityType.FIRE_LANE
}

/** FusedLocation onChanged 또는 시나리오 재생이 넣는 위치. heading 은 도(0=북, 시계방향). */
data class LocationFix(
    val lat: Double,
    val lon: Double,
    val heading: Double,
    val confidence: Float,
    val timeMillis: Long,
)

enum class AlertMode { STOP, PARK }

/** 4-3 알고리즘 결과. */
data class Geometry(
    val distance: Double,
    val relBearing: Double,
    val along: Double,
    val lateral: Double,
    val moveBack: Double,
    val moveForward: Double,
    /** 권할 이동 거리(0.5m 단위 올림, 최소 1m). 이미 CLEAR_R 밖이면 null. */
    val suggestMeters: Double?,
    /** true=뒤로, false=앞으로. suggestMeters 가 null 이면 의미 없음. */
    val suggestBack: Boolean,
    val side: Side,
) {
    enum class Side(val word: String) { FRONT("앞쪽"), BACK("뒤쪽"), SIDE("옆쪽") }
}

enum class Outcome(val label: String) { CLEARED("비웠어요"), ALERT_ONLY("알림만") }

data class Record(val timeMillis: Long, val targetId: String, val detail: String, val outcome: Outcome, val demo: Boolean = false)

data class ActiveAlert(
    val target: Target,
    val geometry: Geometry,
    val mode: AlertMode,
    val lowConfidence: Boolean,
    val alertedAt: Long,
    /** 무드등을 빨강으로 바꾼 상태인지 */
    val lightOn: Boolean,
) {
    /** d >= CLEAR_R : 이동 안내 없이 확인만 권하는 경우 */
    val infoOnly get() = geometry.suggestMeters == null && !target.isFireLane
}

/** 엔진 출력(명세 8-2: Silent / StopAlert / ParkAlert / Cleared + 무시하고 떠남). speech 가 null 이면 말하지 않는다. */
sealed interface Decision {
    data object Silent : Decision
    data class StopAlert(val alert: ActiveAlert, val speech: String?) : Decision
    data class ParkAlert(val alert: ActiveAlert, val speech: String?, val lightRed: Boolean) : Decision
    data class Cleared(val target: Target, val monthCount: Int, val speech: String) : Decision
    /** 알림을 끝낸다(무시하고 떠남, 닫기, 여기는 아니에요). 무드등을 켰었다면 restoreLight=true */
    data class Ended(val reason: String, val restoreLight: Boolean) : Decision
}
