package com.moyeon.redcurb.engine

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/**
 * 빨간 연석 상태 엔진(순수 Kotlin). 입력: 기어·운전석 문·운전석 착석·위치·시간.
 * 출력: [Decision] 목록. 말하기·무드등·화면은 바깥(어댑터)이 한다.
 *
 * 규칙(명세 4장)
 * - P 진입 때만 판단한다. 주행 중(P 아님)에는 어떤 알림도 내지 않는다.
 * - P + 착석 = 정차(STOP), P + 운전석 문 열림 또는 착석 false = 주차(PARK).
 * - 반경 ALERT_R(12m) 안 최근접(소화전 ∪ 전용구역 − 30일 음소거).
 * - 음성은 같은 대상·같은 상황(정차/주차)에 하루 1회. 카드·무드등은 매번.
 * - 알림 뒤 10분 안에 P→(D/R/N)→P 이고 새 위치가 CLEAR_R(8m) 밖이면 비움.
 * - 착석 false 후 60초가 지나면 알림만으로 끝(무드등 복원).
 */
class ParkingEngine(
    private val index: TargetIndex,
    private val zone: ZoneId = ZoneId.of("Asia/Seoul"),
    private val isLowConfidence: (Float) -> Boolean = { it < LOW_CONFIDENCE },
) {
    companion object {
        /** confidence 단위가 공개 문서에 없어 0..1 로 보고 0.5 미만을 '낮음'으로 둔다 [추정, 실차 테스트로 정함]. */
        const val LOW_CONFIDENCE = 0.5f
        const val CLEAR_WINDOW_MS = 10 * 60 * 1000L
        const val LEAVE_MS = 60 * 1000L
        const val MUTE_MS = 30L * 24 * 60 * 60 * 1000
    }

    private data class Pending(val target: Target, val alertedAt: Long, val lightOn: Boolean)

    var gear: Gear = Gear.UNKNOWN; private set
    var doorOpen: Boolean = false; private set
    var seatOccupied: Boolean? = null; private set
    var location: LocationFix? = null; private set
    var alert: ActiveAlert? = null; private set
    private var pending: Pending? = null
    /** [닫기]로 카드만 닫은 알림. 그 뒤 옮기면 여전히 '비움'으로 센다. */
    private var dismissed: ActiveAlert? = null
    private var seatLeftAt: Long? = null
    private var evaluatedThisPark = false

    private val fireLanes = mutableListOf<Target>()
    private val mutes = HashMap<String, Long>()
    private val spoken = HashMap<String, Long>() // key -> epochDay
    private val records = mutableListOf<Record>()

    // ---- 조회 ----
    fun fireLanes(): List<Target> = fireLanes.toList()
    fun records(): List<Record> = records.sortedByDescending { it.timeMillis }
    fun mutes(): Map<String, Long> = mutes.toMap()
    fun spoken(): Map<String, Long> = spoken.toMap()
    val isMovingOut get() = pending != null

    fun monthCount(now: Long): Int {
        val ym = YearMonth.from(Instant.ofEpochMilli(now).atZone(zone))
        return records.count { it.outcome == Outcome.CLEARED && YearMonth.from(Instant.ofEpochMilli(it.timeMillis).atZone(zone)) == ym }
    }

    /** 저장소에서 읽어 온 값 복원 */
    fun restore(records: List<Record>, fireLanes: List<Target>, mutes: Map<String, Long>, spoken: Map<String, Long>) {
        this.records.clear(); this.records.addAll(records)
        this.fireLanes.clear(); this.fireLanes.addAll(fireLanes)
        this.mutes.clear(); this.mutes.putAll(mutes)
        this.spoken.clear(); this.spoken.putAll(spoken)
    }

    // ---- 입력 ----
    fun onLocation(fix: LocationFix, now: Long): List<Decision> {
        location = fix
        // P 에 들어갔을 때 위치가 아직 없었다면, 첫 위치가 왔을 때 한 번 판단한다.
        if (gear == Gear.PARK && !evaluatedThisPark && alert == null) return listOf(evaluate(now, null, false))
        return emptyList()
    }

    fun onGear(g: Gear, now: Long): List<Decision> {
        val prev = gear
        gear = g
        if (g == Gear.PARK && prev != Gear.PARK) return onParkEntered(now)
        if (prev == Gear.PARK && g != Gear.PARK) {
            evaluatedThisPark = false
            seatLeftAt = null
            (alert ?: dismissed)?.let { pending = Pending(it.target, it.alertedAt, it.lightOn) }
            alert = null
            dismissed = null
        }
        return emptyList()
    }

    fun onDoor(open: Boolean, now: Long): List<Decision> {
        doorOpen = open
        if (open && gear == Gear.PARK) return upgradeToPark(now)
        return emptyList()
    }

    fun onSeat(occupied: Boolean, now: Long): List<Decision> {
        seatOccupied = occupied
        if (gear != Gear.PARK) return emptyList()
        return if (!occupied) {
            if (seatLeftAt == null) seatLeftAt = now
            upgradeToPark(now)
        } else {
            seatLeftAt = null
            emptyList()
        }
    }

    /** 1초마다 불러 준다(60초 떠남, 10분 창 만료). */
    fun tick(now: Long): List<Decision> {
        val out = mutableListOf<Decision>()
        val a = alert
        val left = seatLeftAt
        if (a != null && left != null && gear == Gear.PARK && now - left >= LEAVE_MS) {
            records += Record(now, a.target.id, a.target.detail, Outcome.ALERT_ONLY)
            alert = null
            seatLeftAt = null
            out += Decision.Ended("운전자가 내렸어요", a.lightOn)
        }
        val p = pending
        if (p != null && now - p.alertedAt > CLEAR_WINDOW_MS) {
            records += Record(now, p.target.id, p.target.detail, Outcome.ALERT_ONLY)
            pending = null
            out += Decision.Ended("10분 안에 옮기지 않았어요", p.lightOn)
        }
        return out
    }

    /** [닫기] */
    fun dismiss(): List<Decision> {
        val a = alert ?: return emptyList()
        alert = null
        dismissed = a.copy(lightOn = false)
        return listOf(Decision.Ended("닫기", a.lightOn))
    }

    /** [여기는 아니에요]: 이 대상은 이 차에서 30일간 알리지 않는다. */
    fun muteCurrent(now: Long): List<Decision> {
        val a = alert ?: return emptyList()
        mutes[a.target.id] = now + MUTE_MS
        alert = null
        return listOf(Decision.Ended("여기는 아니에요(30일 음소거)", a.lightOn))
    }

    /** P 일 때만, 지금 자리를 소방차 전용구역으로 등록 */
    fun registerFireLane(label: String, now: Long): Target? {
        val loc = location ?: return null
        if (gear != Gear.PARK) return null
        val t = Target("lane-$now", loc.lat, loc.lon, FacilityType.FIRE_LANE, label, createdAt = now)
        fireLanes += t
        return t
    }

    fun removeFireLane(id: String) { fireLanes.removeAll { it.id == id } }

    /** 시연 재생용: 이 대상의 '오늘 말함'·음소거를 지워 시연을 몇 번이고 다시 할 수 있게 한다. */
    fun resetFor(targetId: String) {
        spoken.keys.removeAll { it.startsWith("$targetId|") }
        mutes.remove(targetId)
        alert = null; pending = null; dismissed = null; seatLeftAt = null
    }

    /** 시연용: 이번 달 기록이 n개보다 적으면 '시연 예시' 비움 기록을 채운다. */
    fun seedDemoRecords(now: Long, n: Int) {
        var have = monthCount(now)
        var i = 1
        while (have < n) {
            records += Record(now - i * 3_600_000L, "demo-$i", "예시 자리", Outcome.CLEARED, demo = true)
            have++; i++
        }
    }

    // ---- 내부 ----
    private fun onParkEntered(now: Long): List<Decision> {
        val out = mutableListOf<Decision>()
        val p = pending
        pending = null
        evaluatedThisPark = false
        if (!doorOpen && seatOccupied == false) seatLeftAt = now
        var exclude: String? = null
        var carryLight = false
        if (p != null) {
            val loc = location
            if (now - p.alertedAt <= CLEAR_WINDOW_MS && loc != null) {
                val d = Geo.haversine(loc.lat, loc.lon, p.target.lat, p.target.lon)
                if (Geo.outsideClear(d)) {
                    records += Record(now, p.target.id, p.target.detail, Outcome.CLEARED)
                    val n = monthCount(now)
                    out += Decision.Cleared(p.target, n, Phrases.cleared(n))
                    exclude = p.target.id
                } else {
                    carryLight = p.lightOn // 아직 반경 안: 다시 알림, 무드등은 이미 빨강
                }
            } else {
                records += Record(now, p.target.id, p.target.detail, Outcome.ALERT_ONLY)
                out += Decision.Ended("10분 안에 옮기지 않았어요", p.lightOn)
            }
        }
        val ev = evaluate(now, exclude, carryLight)
        if (ev != Decision.Silent || out.isEmpty()) out += ev
        return out
    }

    private fun currentMode() = if (doorOpen || seatOccupied == false) AlertMode.PARK else AlertMode.STOP

    private fun nearest(loc: LocationFix, now: Long, exclude: String?): Target? =
        (index.candidates(loc.lat, loc.lon).asSequence() + fireLanes.asSequence())
            .filter { it.id != exclude }
            .filter { it.usable.trim() != "N" }
            .filter { (mutes[it.id] ?: 0L) <= now }
            .map { it to Geo.haversine(loc.lat, loc.lon, it.lat, it.lon) }
            .filter { it.second < Geo.ALERT_R }
            .minByOrNull { it.second }?.first

    private fun evaluate(now: Long, exclude: String?, carryLight: Boolean): Decision {
        val loc = location ?: return Decision.Silent
        evaluatedThisPark = true
        val t = nearest(loc, now, exclude) ?: return Decision.Silent
        return decide(t, Geo.geometry(loc, t), currentMode(), now, carryLight)
    }

    private fun upgradeToPark(now: Long): List<Decision> {
        val a = alert ?: return emptyList()
        if (a.mode == AlertMode.PARK) return emptyList()
        val loc = location ?: return emptyList()
        return listOf(decide(a.target, Geo.geometry(loc, a.target), AlertMode.PARK, now, a.lightOn, a.alertedAt))
    }

    private fun epochDay(now: Long) = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toEpochDay()

    private fun decide(t: Target, g: Geometry, mode: AlertMode, now: Long, wasLit: Boolean, alertedAt: Long = now): Decision {
        val low = !t.isFireLane && isLowConfidence(location?.confidence ?: 1f)
        val infoOnly = g.suggestMeters == null && !t.isFireLane
        val wantLight = mode == AlertMode.PARK && !low && !infoOnly
        val a = ActiveAlert(t, g, mode, low, alertedAt, lightOn = wasLit || wantLight)
        alert = a
        dismissed = null
        val key = if (infoOnly || low) "${t.id}|ANY" else "${t.id}|$mode"
        val today = epochDay(now)
        val speech = if (spoken[key] == today) null else {
            spoken[key] = today
            Phrases.speech(a)
        }
        return when (mode) {
            AlertMode.STOP -> Decision.StopAlert(a, speech)
            AlertMode.PARK -> Decision.ParkAlert(a, speech, lightRed = wantLight && !wasLit)
        }
    }
}
