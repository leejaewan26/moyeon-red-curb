package com.moyeon.redcurb

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.moyeon.redcurb.engine.ActiveAlert
import com.moyeon.redcurb.engine.Decision
import com.moyeon.redcurb.engine.Gear
import com.moyeon.redcurb.engine.LocationFix
import com.moyeon.redcurb.engine.ParkingEngine
import com.moyeon.redcurb.engine.Record
import com.moyeon.redcurb.engine.Scenario
import com.moyeon.redcurb.engine.ScenarioAction
import com.moyeon.redcurb.engine.Target
import com.moyeon.redcurb.engine.TargetIndex
import com.moyeon.redcurb.sdk.AmbientLight
import com.moyeon.redcurb.sdk.Diag
import com.moyeon.redcurb.sdk.LocationAdapter
import com.moyeon.redcurb.sdk.Speaker
import com.moyeon.redcurb.sdk.VehicleAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen { HOME, RECORDS, LANES, SCENARIO }
enum class InputSource { SDK, SCENARIO }

data class Cleared(val count: Int, val detail: String, val until: Long)

data class UiState(
    val screen: Screen = Screen.HOME,
    val gear: Gear = Gear.UNKNOWN,
    val doorOpen: Boolean = false,
    val seat: Boolean? = null,
    val location: LocationFix? = null,
    val alert: ActiveAlert? = null,
    val cleared: Cleared? = null,
    val redBorder: Boolean = false,
    val records: List<Record> = emptyList(),
    val monthCount: Int = 0,
    val lanes: List<Target> = emptyList(),
    val dataCount: Int = 0,
    val dataLoaded: Boolean = false,
    val scenarioRunning: Boolean = false,
    val scenarioSecond: Int = 0,
    val caption: String? = null,
    val lastSpeech: String? = null,
    val speechEngine: String = "준비 중",
    val diag: Map<String, String> = linkedMapOf(),
    val lastInput: InputSource? = null,
)

/**
 * 어댑터(실제 SDK) → 엔진 → 출력(TTS·무드등·화면)을 잇는다. 엔진 호출은 모두 메인 스레드에서.
 * 시나리오 재생 중에는 SDK 의 기어·문·착석·위치 입력을 엔진에 넣지 않는다(화면에 '시나리오 재생 중').
 */
class RedCurbViewModel(app: Application) : AndroidViewModel(app) {
    private val main = Handler(Looper.getMainLooper())
    private val index = TargetIndex()
    private val engine = ParkingEngine(index)
    private val storage = Storage(app)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    private val diag = Diag { k, v ->
        Log.i("RedCurb", "$k: $v")
        main.post { _state.update { s -> s.copy(diag = LinkedHashMap(s.diag).apply { put(k, v) }) } }
    }

    private val vehicle = VehicleAdapter(app, diag, object : VehicleAdapter.Sink {
        override fun onGear(gear: Gear) = fromSdk { engine.onGear(gear, now()) }
        override fun onDriverDoor(open: Boolean) = fromSdk { engine.onDoor(open, now()) }
        override fun onDriverSeat(occupied: Boolean) = fromSdk { engine.onSeat(occupied, now()) }
    })
    private val light = AmbientLight({ if (vehicle.initialized) vehicle.vehicle else null }, diag)
    private val speaker = Speaker(app, diag) { name -> main.post { _state.update { it.copy(speechEngine = name) } } }
    private val location = LocationAdapter(app, diag) { fix -> fromSdk { engine.onLocation(fix, now()) } }

    private var scenarioJob: Job? = null

    init {
        engine.restore(storage.records(), storage.lanes(), storage.mutes(), storage.spoken())
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) {
                app.assets.open("hydrants.csv").bufferedReader().useLines { TargetIndex.parseCsv(it) }
            }
            index.addAll(list)
            diag.report("데이터", "서울 소방용수시설 ${list.size}곳 적재")
            _state.update { it.copy(dataCount = list.size, dataLoaded = true) }
        }
        vehicle.start()
        location.start()
        speaker.start()
        viewModelScope.launch {
            while (isActive) {
                delay(1000)
                apply(engine.tick(now()))
                _state.update { s -> if (s.cleared != null && now() > s.cleared.until) s.copy(cleared = null) else s }
            }
        }
        refresh(null)
    }

    private fun now() = System.currentTimeMillis()

    private fun fromSdk(block: () -> List<Decision>) {
        main.post {
            if (_state.value.scenarioRunning) return@post // 시나리오 재생 중엔 SDK 입력을 막는다
            apply(block(), InputSource.SDK)
        }
    }

    private fun fromScenario(block: () -> List<Decision>) = apply(block(), InputSource.SCENARIO)

    private fun apply(decisions: List<Decision>, source: InputSource? = null) {
        for (d in decisions) {
            Log.i("RedCurb", "decision: $d")
            when (d) {
                Decision.Silent -> Unit
                is Decision.StopAlert -> d.speech?.let(::say)
                is Decision.ParkAlert -> {
                    if (d.lightRed) light.red()
                    d.speech?.let(::say)
                }
                is Decision.Cleared -> {
                    light.restore()
                    say(d.speech)
                    _state.update { it.copy(cleared = Cleared(d.monthCount, d.target.detail, now() + 3000)) }
                }
                is Decision.Ended -> if (d.restoreLight) light.restore()
            }
        }
        if (decisions.any { it != Decision.Silent }) persist()
        refresh(source)
    }

    private fun say(text: String) {
        speaker.speak(text)
        _state.update { it.copy(lastSpeech = text) }
    }

    private fun persist() = storage.save(engine.records(), engine.fireLanes(), engine.mutes(), engine.spoken())

    private fun refresh(source: InputSource?) {
        val t = now()
        _state.update {
            it.copy(
                gear = engine.gear, doorOpen = engine.doorOpen, seat = engine.seatOccupied, location = engine.location,
                alert = engine.alert, redBorder = engine.alert?.lightOn == true || light.isRed,
                records = engine.records(), monthCount = engine.monthCount(t), lanes = engine.fireLanes(),
                lastInput = source ?: it.lastInput,
            )
        }
    }

    // ---- 화면 동작 ----
    fun restartLocation() { location.stop(); location.start() }

    fun go(screen: Screen) = _state.update { it.copy(screen = screen) }
    fun dismiss() = apply(engine.dismiss())
    fun notHere() = apply(engine.muteCurrent(now()))

    fun registerLane(): Boolean {
        val n = engine.fireLanes().size + 1
        val t = engine.registerFireLane("소방차 전용구역 $n", now()) ?: return false
        diag.report("전용구역 등록", "${t.lat},${t.lon}")
        persist(); refresh(null); return true
    }

    fun removeLane(id: String) { engine.removeFireLane(id); persist(); refresh(null) }

    // ---- 시나리오(수동 버튼도 같은 엔진 입력 함수) ----
    private fun demoTarget(): Target = index.get(Scenario.DEMO_TARGET_ID) ?: Scenario.DEMO_FALLBACK

    fun manual(action: ScenarioAction) {
        val t = now()
        when (action) {
            is ScenarioAction.SetGear -> fromScenario { engine.onGear(action.gear, t) }
            is ScenarioAction.SetDoor -> fromScenario { engine.onDoor(action.open, t) }
            is ScenarioAction.SetSeat -> fromScenario { engine.onSeat(action.occupied, t) }
            is ScenarioAction.SetLocation -> fromScenario { engine.onLocation(LocationFix(action.lat, action.lon, action.heading, action.confidence, t), t) }
            is ScenarioAction.Caption -> _state.update { it.copy(caption = action.text) }
            ScenarioAction.OpenRecords -> go(Screen.RECORDS)
            ScenarioAction.End -> Unit
        }
    }

    fun arrivalAction(): ScenarioAction.SetLocation = demoTarget().let { val (a, b) = Scenario.arrival(it); ScenarioAction.SetLocation(a, b, Scenario.HEADING) }
    fun movedAction(): ScenarioAction.SetLocation = demoTarget().let { val (a, b) = Scenario.movedBack(it); ScenarioAction.SetLocation(a, b, Scenario.HEADING) }

    fun playScenario() {
        if (scenarioJob?.isActive == true) return
        light.restore()
        engine.resetFor(demoTarget().id)
        engine.seedDemoRecords(now(), 2) // "이번 달 세 번째예요" 시연용 예시 기록 2건(기록 화면에 '시연 예시'로 표시)
        persist()
        _state.update { it.copy(scenarioRunning = true, screen = Screen.HOME, caption = null, scenarioSecond = 0) }
        scenarioJob = viewModelScope.launch {
            val start = now()
            val ticker = launch { while (isActive) { _state.update { it.copy(scenarioSecond = ((now() - start) / 1000).toInt()) }; delay(250) } }
            for (step in Scenario.steps(demoTarget())) {
                val wait = start + step.atMs - now()
                if (wait > 0) delay(wait)
                manual(step.action)
            }
            ticker.cancel()
            _state.update { it.copy(scenarioRunning = false, caption = null) }
        }
    }

    fun stopScenario() {
        scenarioJob?.cancel()
        _state.update { it.copy(scenarioRunning = false, caption = null) }
    }

    override fun onCleared() {
        light.restore()
        vehicle.stop(); location.stop(); speaker.stop()
    }
}
