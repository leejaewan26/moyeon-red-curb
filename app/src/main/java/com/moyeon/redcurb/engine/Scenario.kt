package com.moyeon.redcurb.engine

/** 명세 7절 30초 시연. 같은 엔진 입력 함수(onGear/onDoor/onSeat/onLocation)로 흘린다. */
sealed interface ScenarioAction {
    data class SetGear(val gear: Gear) : ScenarioAction
    data class SetDoor(val open: Boolean) : ScenarioAction
    data class SetSeat(val occupied: Boolean) : ScenarioAction
    data class SetLocation(val lat: Double, val lon: Double, val heading: Double, val confidence: Float = 0.9f) : ScenarioAction
    data class Caption(val text: String) : ScenarioAction
    data object OpenRecords : ScenarioAction
    data object End : ScenarioAction
}

data class ScenarioStep(val atMs: Long, val action: ScenarioAction)

object Scenario {
    /** 시연 소화전: 마포구 독막로20나길 21 · 우강빌딩 앞 지하식 소화전(서울 OA-21306, 주변 78m 안에 다른 시설 없음) */
    const val DEMO_TARGET_ID = "마포-현장-001291"
    val DEMO_FALLBACK = Target(DEMO_TARGET_ID, 37.547432, 126.926362, FacilityType.UNDERGROUND, "마포구 독막로20나길 21 · 우강빌딩")

    /** 북쪽(heading 0)으로 달려 와 소화전 4m 뒤에 선다. */
    const val HEADING = 0.0

    fun arrival(t: Target) = Geo.offset(t.lat, t.lon, HEADING + 180.0, 4.0)
    fun movedBack(t: Target) = Geo.offset(t.lat, t.lon, HEADING + 180.0, 8.0)

    fun steps(t: Target): List<ScenarioStep> {
        val (aLat, aLon) = arrival(t)
        val (mLat, mLon) = movedBack(t)
        return listOf(
            ScenarioStep(0, ScenarioAction.SetSeat(true)),
            ScenarioStep(0, ScenarioAction.SetDoor(false)),
            ScenarioStep(0, ScenarioAction.SetGear(Gear.DRIVE)),
            ScenarioStep(0, ScenarioAction.SetLocation(aLat, aLon, HEADING)),
            ScenarioStep(0, ScenarioAction.Caption("골목 도착 · 주행 중엔 아무 말도 안 합니다")),
            ScenarioStep(4_000, ScenarioAction.SetGear(Gear.PARK)),
            ScenarioStep(4_000, ScenarioAction.Caption("P · 운전자 앉아 있음 = 정차")),
            ScenarioStep(10_000, ScenarioAction.SetDoor(true)),
            ScenarioStep(10_000, ScenarioAction.Caption("운전석 문 열림 = 주차")),
            ScenarioStep(17_000, ScenarioAction.SetDoor(false)),
            ScenarioStep(17_000, ScenarioAction.Caption("문 닫고 R, 뒤로 4m")),
            ScenarioStep(18_500, ScenarioAction.SetGear(Gear.REVERSE)),
            ScenarioStep(20_000, ScenarioAction.SetLocation(mLat, mLon, HEADING)),
            ScenarioStep(21_500, ScenarioAction.SetGear(Gear.PARK)),
            ScenarioStep(24_000, ScenarioAction.OpenRecords),
            ScenarioStep(24_000, ScenarioAction.Caption("휘슬은 소화전 5m를 알려 주지 않습니다. 차가 P에 들어가는 순간, 차만 압니다.")),
            ScenarioStep(30_000, ScenarioAction.End),
        )
    }
}
