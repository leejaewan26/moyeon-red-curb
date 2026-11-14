package com.moyeon.redcurb.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParkingEngineTest {
    private val hydrant = Target("H1", 37.547432, 126.926362, FacilityType.UNDERGROUND, "독막로20나길 21 · 우강빌딩")
    private val t0 = 1_700_000_000_000L // 2023-11-15 KST

    private fun engine(vararg extra: Target) = ParkingEngine(TargetIndex().apply { add(hydrant); addAll(extra.toList()) })

    /** 소화전에서 bearing 방향으로 m 떨어진 자리, 진행방향 heading */
    private fun fixAt(t: Target, bearingFromTarget: Double, m: Double, heading: Double, conf: Float = 0.9f, time: Long = t0): LocationFix {
        val (la, lo) = Geo.offset(t.lat, t.lon, bearingFromTarget, m)
        return LocationFix(la, lo, heading, conf, time)
    }

    /** 소화전 4m 뒤(북쪽 진행) */
    private fun ParkingEngine.arrive(now: Long = t0, m: Double = 4.0, conf: Float = 0.9f) {
        onSeat(true, now); onDoor(false, now); onGear(Gear.DRIVE, now)
        onLocation(fixAt(hydrant, 180.0, m, 0.0, conf, now), now)
    }

    @Test fun geometry_front4m_suggestsBack4m() {
        val g = Geo.geometry(fixAt(hydrant, 180.0, 4.0, 0.0), hydrant)
        assertEquals(4.0, g.distance, 0.01)
        assertEquals(Geometry.Side.FRONT, g.side)
        assertEquals(4.0, g.moveBack, 0.05)
        assertEquals(12.0, g.moveForward, 0.05)
        assertEquals(4.0, g.suggestMeters!!, 0.0)
        assertTrue(g.suggestBack)
    }

    @Test fun geometry_targetBehind_isBack_suggestsForward() {
        // 소화전이 차 뒤 3m: 차는 소화전 북쪽 3m, 북쪽 진행
        val g = Geo.geometry(fixAt(hydrant, 0.0, 3.0, 0.0), hydrant)
        assertEquals(Geometry.Side.BACK, g.side)
        assertTrue(g.along < 0)
        assertTrue(!g.suggestBack)
        assertEquals(5.0, g.suggestMeters!!, 0.0) // s=8, along=-3 → forward 5
    }

    @Test fun geometry_side_and_roundingHalfMeter() {
        val g = Geo.geometry(fixAt(hydrant, 270.0, 3.0, 0.0), hydrant) // 소화전이 오른쪽 3m
        assertEquals(Geometry.Side.SIDE, g.side)
        // lat=3 → s=sqrt(64-9)=7.416 → 7.5
        assertEquals(7.5, g.suggestMeters!!, 0.0)
    }

    @Test fun drivingIsSilent() {
        val e = engine()
        e.arrive()
        assertTrue(e.onDoor(true, t0).isEmpty())
        assertTrue(e.onSeat(false, t0).isEmpty())
        assertNull(e.alert)
    }

    @Test fun stopThenPark_transition() {
        val e = engine()
        e.arrive()
        val stop = e.onGear(Gear.PARK, t0 + 1000).single() as Decision.StopAlert
        assertEquals(AlertMode.STOP, stop.alert.mode)
        assertEquals("여기는 잠깐 서는 것도 안 되는 자리예요. 소화전이 앞쪽 4미터쯤 있어요.", stop.speech)
        assertTrue(!stop.alert.lightOn)

        val park = e.onDoor(true, t0 + 5000).single() as Decision.ParkAlert
        assertEquals(AlertMode.PARK, park.alert.mode)
        assertTrue(park.lightRed)
        assertEquals("잠깐만요. 앞쪽 4미터쯤 소화전이 있어요. 불이 나면 소방차가 물을 대는 자리예요. 뒤로 4미터만 옮기면 괜찮아요.", park.speech)
        assertEquals("앞쪽 4m · 소화전", Phrases.headline(park.alert))
        assertEquals("뒤로 약 4m 옮기면 벗어나요", Phrases.moveLine(park.alert))
    }

    @Test fun seatFalseAlsoMeansPark() {
        val e = engine()
        e.arrive()
        e.onGear(Gear.PARK, t0)
        val d = e.onSeat(false, t0 + 1000).single()
        assertTrue(d is Decision.ParkAlert)
    }

    @Test fun radiusBoundary_11_9_alerts_12_1_silent() {
        val e1 = engine(); e1.arrive(m = 11.9)
        assertTrue(e1.onGear(Gear.PARK, t0).single() is Decision.StopAlert)
        val e2 = engine(); e2.arrive(m = 12.1)
        assertEquals(Decision.Silent, e2.onGear(Gear.PARK, t0).single())
    }

    @Test fun beyondClearRadius_isInfoOnly_noLight_onceForStopAndPark() {
        val e = engine(); e.arrive(m = 10.0)
        val s = e.onGear(Gear.PARK, t0).single() as Decision.StopAlert
        assertEquals("앞쪽 약 10미터에 소화전이 있어요. 5미터 밖이면 괜찮아요, 한 번 확인해 주세요.", s.speech)
        val p = e.onDoor(true, t0 + 1000).single() as Decision.ParkAlert
        assertTrue(!p.lightRed)
        assertNull(p.speech) // 정차·주차 모두 합쳐 1회
    }

    @Test fun frontVsBack_wording() {
        val e = engine()
        e.onSeat(true, t0); e.onGear(Gear.DRIVE, t0)
        e.onLocation(fixAt(hydrant, 0.0, 4.0, 0.0), t0) // 소화전이 뒤쪽 4m
        val s = e.onGear(Gear.PARK, t0).single() as Decision.StopAlert
        assertEquals(Geometry.Side.BACK, s.alert.geometry.side)
        assertTrue(s.speech!!.contains("뒤쪽 4미터쯤"))
    }

    @Test fun lowConfidence_dropsNumbers_andLight() {
        val e = engine(); e.arrive(conf = 0.2f)
        e.onGear(Gear.PARK, t0)
        val p = e.onDoor(true, t0 + 1000).single() as Decision.ParkAlert
        assertTrue(!p.lightRed)
        assertEquals("근처 · 소화전", Phrases.headline(p.alert))
        val s = (ParkingEngine(TargetIndex().apply { add(hydrant) }).run {
            arrive(conf = 0.2f); onGear(Gear.PARK, t0).single() as Decision.StopAlert
        }).speech
        assertEquals("근처에 소화전이 있어요. 위치가 흔들려서, 주변을 한 번 봐 주세요.", s)
    }

    @Test fun cleared_withinTenMinutes() {
        val e = engine()
        e.arrive()
        e.onGear(Gear.PARK, t0)
        e.onDoor(true, t0 + 1000)
        e.onDoor(false, t0 + 2000)
        e.onGear(Gear.REVERSE, t0 + 3000)
        e.onLocation(fixAt(hydrant, 180.0, 8.0, 0.0), t0 + 4000)
        val out = e.onGear(Gear.PARK, t0 + 9 * 60_000)
        val c = out.first() as Decision.Cleared
        assertEquals(1, c.monthCount)
        assertEquals("비웠어요. 소방차 물 대는 자리를 지켰어요. 이번 달 첫 번째예요.", c.speech)
        assertEquals(1, out.size) // 비운 소화전은 다시 알리지 않는다
        assertEquals(Outcome.CLEARED, e.records().first().outcome)
    }

    @Test fun notCleared_afterTenMinutes() {
        val e = engine()
        e.arrive()
        e.onGear(Gear.PARK, t0)
        e.onGear(Gear.DRIVE, t0 + 1000)
        e.onLocation(fixAt(hydrant, 180.0, 30.0, 0.0), t0 + 2000)
        val out = e.onGear(Gear.PARK, t0 + 10 * 60_000 + 1)
        assertTrue(out.none { it is Decision.Cleared })
        assertEquals(0, e.monthCount(t0))
    }

    @Test fun notCleared_ifStillInside() {
        val e = engine()
        e.arrive()
        e.onGear(Gear.PARK, t0)
        e.onGear(Gear.REVERSE, t0 + 1000)
        e.onLocation(fixAt(hydrant, 180.0, 6.0, 0.0), t0 + 2000)
        val out = e.onGear(Gear.PARK, t0 + 3000)
        assertTrue(out.single() is Decision.StopAlert)
    }

    @Test fun speech_oncePerDay_cardEveryTime() {
        val e = engine()
        e.arrive()
        assertNotNull((e.onGear(Gear.PARK, t0).single() as Decision.StopAlert).speech)
        e.onGear(Gear.DRIVE, t0 + 1000)
        val again = e.onGear(Gear.PARK, t0 + 60 * 60_000).filterIsInstance<Decision.StopAlert>().single()
        assertNull(again.speech)
        e.onGear(Gear.DRIVE, t0 + 61 * 60_000)
        val nextDay = e.onGear(Gear.PARK, t0 + 24 * 60 * 60_000L).filterIsInstance<Decision.StopAlert>().single()
        assertNotNull(nextDay.speech)
    }

    @Test fun ignoreAndLeave_after60s_restoresLight() {
        val e = engine()
        e.arrive()
        e.onGear(Gear.PARK, t0)
        e.onDoor(true, t0 + 1000)
        e.onSeat(false, t0 + 2000)
        assertTrue(e.tick(t0 + 61_000).isEmpty())
        val end = e.tick(t0 + 62_000).single() as Decision.Ended
        assertTrue(end.restoreLight)
        assertEquals(Outcome.ALERT_ONLY, e.records().first().outcome)
    }

    @Test fun mute_30days() {
        val e = engine()
        e.arrive()
        e.onGear(Gear.PARK, t0)
        e.muteCurrent(t0 + 1000)
        e.onGear(Gear.DRIVE, t0 + 2000)
        assertEquals(Decision.Silent, e.onGear(Gear.PARK, t0 + 3000).single())
        e.onGear(Gear.DRIVE, t0 + 4000)
        val after = e.onGear(Gear.PARK, t0 + ParkingEngine.MUTE_MS + 5000).single()
        assertTrue(after is Decision.StopAlert)
    }

    @Test fun fireLane_onlyInPark_andAlertsLater() {
        val e = ParkingEngine(TargetIndex())
        e.onGear(Gear.DRIVE, t0)
        val here = LocationFix(37.5, 127.0, 90.0, 0.9f, t0)
        e.onLocation(here, t0)
        assertNull(e.registerFireLane("우리 아파트 101동 앞", t0))
        e.onGear(Gear.PARK, t0)
        assertNotNull(e.registerFireLane("우리 아파트 101동 앞", t0))
        e.onGear(Gear.DRIVE, t0 + 1000)
        e.onSeat(true, t0)
        val s = e.onGear(Gear.PARK, t0 + 2000).single() as Decision.StopAlert
        assertEquals(Phrases.FIRE_LANE, s.speech)
    }

    @Test fun locationArrivingAfterPark_isEvaluatedOnce() {
        val e = engine()
        e.onSeat(true, t0); e.onGear(Gear.DRIVE, t0)
        assertEquals(Decision.Silent, e.onGear(Gear.PARK, t0).single())
        val d = e.onLocation(fixAt(hydrant, 180.0, 4.0, 0.0), t0 + 500)
        assertTrue(d.single() is Decision.StopAlert)
        assertTrue(e.onLocation(fixAt(hydrant, 180.0, 4.0, 0.0), t0 + 900).isEmpty())
    }

    @Test fun nearestWins() {
        val near = Target("H2", 0.0, 0.0, FacilityType.GROUND, "가까운 것").let {
            val (la, lo) = Geo.offset(hydrant.lat, hydrant.lon, 180.0, 6.0); it.copy(lat = la, lon = lo)
        }
        val e = engine(near)
        e.arrive(m = 7.0) // H1 7m 앞, H2 1m 앞
        val s = e.onGear(Gear.PARK, t0).single() as Decision.StopAlert
        assertEquals("H2", s.alert.target.id)
    }

    @Test fun unusableHydrantIgnored() {
        val e = ParkingEngine(TargetIndex().apply { add(hydrant.copy(usable = "N")) })
        e.arrive()
        assertEquals(Decision.Silent, e.onGear(Gear.PARK, t0).single())
    }

    @Test fun demoScenario_endToEnd() {
        val e = ParkingEngine(TargetIndex().apply { add(Scenario.DEMO_FALLBACK) })
        e.seedDemoRecords(t0, 2)
        val all = mutableListOf<Decision>()
        for (s in Scenario.steps(Scenario.DEMO_FALLBACK)) {
            val now = t0 + s.atMs
            all += when (val a = s.action) {
                is ScenarioAction.SetGear -> e.onGear(a.gear, now)
                is ScenarioAction.SetDoor -> e.onDoor(a.open, now)
                is ScenarioAction.SetSeat -> e.onSeat(a.occupied, now)
                is ScenarioAction.SetLocation -> e.onLocation(LocationFix(a.lat, a.lon, a.heading, a.confidence, now), now)
                else -> emptyList()
            }
        }
        assertTrue(all[0] is Decision.StopAlert)
        assertTrue(all[1] is Decision.ParkAlert)
        val c = all[2] as Decision.Cleared
        assertEquals(3, c.monthCount)
        assertEquals("비웠어요. 소방차 물 대는 자리를 지켰어요. 이번 달 세 번째예요.", c.speech)
        assertEquals(3, all.size)
    }

    @Test fun csvParse_andGridSearch() {
        val lines = sequenceOf(
            "id,lat,lon,type,detail,usable",
            "A,37.547432,126.926362,UNDERGROUND,독막로 · 빌딩,",
            "broken,line",
            "B,37.6,127.0,GROUND,먼 곳,",
        )
        val ts = TargetIndex.parseCsv(lines)
        assertEquals(2, ts.size)
        val idx = TargetIndex().apply { addAll(ts) }
        assertEquals(listOf("A"), idx.candidates(37.5474, 126.9263).map { it.id })
    }

    @Test fun particles() {
        assertEquals("이", Phrases.iGa("소화전"))
        assertEquals("가", Phrases.iGa("저수조"))
        assertEquals("세 번째", Phrases.ordinal(3))
    }
}
