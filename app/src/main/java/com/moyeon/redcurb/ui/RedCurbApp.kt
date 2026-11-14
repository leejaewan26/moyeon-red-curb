package com.moyeon.redcurb.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moyeon.redcurb.BuildConfig
import com.moyeon.redcurb.RedCurbViewModel
import com.moyeon.redcurb.Screen
import com.moyeon.redcurb.UiState
import com.moyeon.redcurb.engine.ActiveAlert
import com.moyeon.redcurb.engine.AlertMode
import com.moyeon.redcurb.engine.FacilityType
import com.moyeon.redcurb.engine.Gear
import com.moyeon.redcurb.engine.Outcome
import com.moyeon.redcurb.engine.Phrases
import com.moyeon.redcurb.engine.ScenarioAction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Bg = Color(0xFF15171A)
private val Panel = Color(0xFF22262B)
private val Red = Color(0xFFD7262E)
private val Green = Color(0xFF2E9E5B)
private val Muted = Color(0xFFA9B0B8)
private val Amber = Color(0xFFF2B33D)

const val DATA_CREDIT = "데이터: 서울특별시 소방재난본부 「서울시 소방용수시설 현황」(서울 열린데이터광장 OA-21306, 2025-12-31 기준) · 공공누리 제1유형"

@Composable
fun RedCurbApp(vm: RedCurbViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    MaterialTheme(colorScheme = darkColorScheme(primary = Red, background = Bg, surface = Panel)) {
        val driving = s.gear == Gear.DRIVE || s.gear == Gear.REVERSE || s.gear == Gear.NEUTRAL
        Box(
            Modifier.fillMaxSize().background(Bg)
                .then(if (s.redBorder) Modifier.border(28.dp, Red) else Modifier)
        ) {
            Column(Modifier.fillMaxSize()) {
                if (s.scenarioRunning) ScenarioBanner(s, vm)
                if (driving) DrivingScreen(s) else Row(Modifier.fillMaxSize()) {
                    NavRail(s.screen, vm)
                    Box(Modifier.fillMaxSize().padding(48.dp)) {
                        when (s.screen) {
                            Screen.HOME -> HomeScreen(s)
                            Screen.RECORDS -> RecordsScreen(s)
                            Screen.LANES -> LanesScreen(s, vm)
                            Screen.SCENARIO -> ScenarioScreen(s, vm)
                        }
                    }
                }
            }
            if (!driving) s.alert?.let { a ->
                if (a.mode == AlertMode.PARK) BigCard(a, vm) else SmallCard(a, vm)
            }
            s.cleared?.let { ClearedCard(it.count) }
        }
    }
}

@Composable
private fun T(text: String, size: TextUnit, color: Color = Color.White, weight: FontWeight = FontWeight.Normal, modifier: Modifier = Modifier, align: TextAlign? = null) =
    Text(text, color = color, fontSize = size, fontWeight = weight, modifier = modifier, textAlign = align, lineHeight = size * 1.25f)

@Composable
private fun BigButton(label: String, enabled: Boolean = true, color: Color = Red, modifier: Modifier = Modifier, onClick: () -> Unit) =
    Button(onClick, enabled = enabled, modifier = modifier.height(120.dp), shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White)) {
        T(label, 40.sp, weight = FontWeight.Bold)
    }

@Composable
private fun ScenarioBanner(s: UiState, vm: RedCurbViewModel) {
    Row(Modifier.fillMaxWidth().background(Amber).padding(horizontal = 48.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            T("▶ 시나리오 재생 중 · ${s.scenarioSecond}초 / 30초 · 위치는 시나리오가 넣습니다", 36.sp, Color.Black, FontWeight.Bold)
            s.caption?.let { T(it, 32.sp, Color.Black) }
        }
        OutlinedButton(onClick = vm::stopScenario, modifier = Modifier.height(96.dp)) { T("멈춤", 36.sp, Color.Black, FontWeight.Bold) }
    }
}

@Composable
private fun DrivingScreen(s: UiState) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).background(Red, CircleShape))
                Spacer(Modifier.width(28.dp))
                T("빨간 연석 켜짐", 72.sp, weight = FontWeight.Bold)
            }
            Spacer(Modifier.height(24.dp))
            T("주행 중 · ${gearLabel(s.gear)}", 40.sp, Muted)
        }
    }
}

@Composable
private fun NavRail(current: Screen, vm: RedCurbViewModel) {
    Column(Modifier.fillMaxHeight().width(400.dp).background(Panel).padding(32.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CurbMark()
            Spacer(Modifier.width(20.dp))
            T("빨간 연석", 48.sp, weight = FontWeight.Bold)
        }
        Spacer(Modifier.height(24.dp))
        listOf(Screen.HOME to "홈", Screen.RECORDS to "기록", Screen.LANES to "전용구역", Screen.SCENARIO to "시나리오 재생").forEach { (sc, label) ->
            val on = sc == current
            Button(onClick = { vm.go(sc) }, modifier = Modifier.fillMaxWidth().height(120.dp), shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (on) Red else Color(0xFF30353B), contentColor = Color.White)) {
                T(label, 40.sp, weight = if (on) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun CurbMark() = Canvas(Modifier.size(width = 80.dp, height = 40.dp)) {
    val w = size.width / 4
    for (i in 0 until 4) drawRect(if (i % 2 == 0) Red else Color.White, Offset(i * w, size.height * 0.4f), Size(w, size.height * 0.6f))
}

private fun gearLabel(g: Gear) = when (g) {
    Gear.PARK -> "P"; Gear.DRIVE -> "D"; Gear.REVERSE -> "R"; Gear.NEUTRAL -> "N"; Gear.UNKNOWN -> "?"
}

@Composable
private fun HomeScreen(s: UiState) {
    Column(verticalArrangement = Arrangement.spacedBy(32.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).background(Red, CircleShape))
            Spacer(Modifier.width(28.dp))
            T("빨간 연석 켜짐", 80.sp, weight = FontWeight.Bold)
        }
        T("P에 넣는 순간, 근처 소화전을 말로 알려 드려요. 주행 중엔 말하지 않아요.", 40.sp, Muted)
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            Stat("기어", gearLabel(s.gear), Modifier.weight(1f))
            Stat("운전석", when (s.seat) { true -> "앉아 있음"; false -> "비어 있음"; null -> "모름" }, Modifier.weight(1f))
            Stat("운전석 문", if (s.doorOpen) "열림" else "닫힘", Modifier.weight(1f))
            Stat("이번 달 비운 자리", "${s.monthCount}곳", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            Stat("위치", s.location?.let { "%.5f, %.5f".format(it.lat, it.lon) } ?: "아직 없음", Modifier.weight(2f))
            Stat("소화전 데이터", if (s.dataLoaded) "%,d곳".format(s.dataCount) else "읽는 중", Modifier.weight(1f))
            Stat("음성", s.speechEngine, Modifier.weight(1f), 32.sp)
        }
        s.lastSpeech?.let { T("마지막 음성: “$it”", 32.sp, Muted) }
        Spacer(Modifier.weight(1f))
        T(DATA_CREDIT, 24.sp, Muted)
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier, valueSize: TextUnit = 52.sp) {
    Column(modifier.background(Panel, RoundedCornerShape(24.dp)).padding(32.dp)) {
        T(label, 30.sp, Muted)
        Spacer(Modifier.height(8.dp))
        T(value, valueSize, weight = FontWeight.Bold)
    }
}

@Composable
private fun RecordsScreen(s: UiState) {
    val fmt = SimpleDateFormat("M월 d일 HH:mm", Locale.KOREAN)
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        T("이번 달 비운 자리 ${s.monthCount}곳", 88.sp, weight = FontWeight.Bold)
        T("기록은 이 차 안에만 저장돼요. 내보내기 없음.", 34.sp, Muted)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(s.records) { r ->
                Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(20.dp)).padding(28.dp), verticalAlignment = Alignment.CenterVertically) {
                    T(fmt.format(Date(r.timeMillis)), 36.sp, Muted, modifier = Modifier.width(380.dp))
                    T(r.detail + if (r.demo) " (시연 예시)" else "", 38.sp, modifier = Modifier.weight(1f))
                    T(r.outcome.label, 38.sp, if (r.outcome == Outcome.CLEARED) Green else Muted, FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun LanesScreen(s: UiState, vm: RedCurbViewModel) {
    val canRegister = s.gear == Gear.PARK && s.location != null
    Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
        T("아파트 소방차 전용구역", 72.sp, weight = FontWeight.Bold)
        T("P 상태에서 한 번 누르면 지금 자리를 등록해요. 다음에 여기 서면 알려 드려요. 이 차에만 저장돼요.", 36.sp, Muted)
        BigButton("지금 자리를 전용구역으로 등록", enabled = canRegister, modifier = Modifier.fillMaxWidth()) { vm.registerLane() }
        if (!canRegister) T(if (s.gear != Gear.PARK) "P일 때만 등록할 수 있어요" else "위치를 아직 받지 못했어요", 32.sp, Amber)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(s.lanes) { l ->
                Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(20.dp)).padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    T("${l.detail} · %.5f, %.5f".format(l.lat, l.lon), 36.sp, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { vm.removeLane(l.id) }, modifier = Modifier.height(96.dp)) { T("삭제", 34.sp) }
                }
            }
        }
    }
}

@Composable
private fun ScenarioScreen(s: UiState, vm: RedCurbViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(48.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            T("시나리오 재생 (시연·테스트용)", 56.sp, weight = FontWeight.Bold)
            T("명세 30초 시연을 같은 엔진에 흘립니다. 재생 중엔 실제 SDK 입력을 막고 화면 위에 표시합니다.", 30.sp, Muted)
            BigButton("▶ 30초 시연 재생", modifier = Modifier.fillMaxWidth()) { vm.playScenario() }
            T("수동 입력 (SDK 콜백이 안 올 때 대체 경로)", 34.sp, Amber, FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SmallBtn("위치: 골목 도착", Modifier.weight(1f)) { vm.manual(vm.arrivalAction()) }
                SmallBtn("위치: 4m 뒤", Modifier.weight(1f)) { vm.manual(vm.movedAction()) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                listOf(Gear.PARK to "P", Gear.DRIVE to "D", Gear.REVERSE to "R").forEach { (g, l) ->
                    SmallBtn("기어 $l", Modifier.weight(1f)) { vm.manual(ScenarioAction.SetGear(g)) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SmallBtn("문 열기", Modifier.weight(1f)) { vm.manual(ScenarioAction.SetDoor(true)) }
                SmallBtn("문 닫기", Modifier.weight(1f)) { vm.manual(ScenarioAction.SetDoor(false)) }
                SmallBtn("착석 ON", Modifier.weight(1f)) { vm.manual(ScenarioAction.SetSeat(true)) }
                SmallBtn("착석 OFF", Modifier.weight(1f)) { vm.manual(ScenarioAction.SetSeat(false)) }
            }
            T("마지막 입력: ${s.lastInput?.let { if (it.name == "SDK") "실제 SDK" else "시나리오" } ?: "-"}", 28.sp, Muted)
        }
        Column(Modifier.weight(1f).background(Panel, RoundedCornerShape(24.dp)).padding(28.dp)) {
            T("SDK 상태  (Vehicle ${BuildConfig.VEHICLE_SDK_VERSION} · FusedLocation ${BuildConfig.FUSED_LOCATION_SDK_VERSION} · TTS ${BuildConfig.TTS_SDK_VERSION})", 28.sp, weight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(s.diag.entries.toList()) { (k, v) ->
                    Row {
                        T(k, 22.sp, Muted, modifier = Modifier.width(420.dp))
                        T(v, 22.sp, if (v.startsWith("실패")) Red else Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallBtn(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) =
    Button(onClick, modifier = modifier.height(104.dp), shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3A4048), contentColor = Color.White)) {
        T(label, 32.sp, weight = FontWeight.Bold)
    }

@Composable
private fun FacilityIcon(type: FacilityType, size: Dp) = Canvas(Modifier.size(size)) {
    val w = this.size.width
    val h = this.size.height
    when (type) {
        FacilityType.UNDERGROUND -> { // 도로 위 맨홀
            drawCircle(Color(0xFF555B63), radius = w * 0.45f)
            drawCircle(Red, radius = w * 0.45f, style = Stroke(w * 0.05f))
            for (i in -2..2) drawLine(Color(0xFF8A9099), Offset(w * 0.2f, h / 2 + i * h * 0.1f), Offset(w * 0.8f, h / 2 + i * h * 0.1f), w * 0.03f)
        }
        FacilityType.FIRE_LANE -> {
            drawRect(Amber, Offset(w * 0.05f, h * 0.2f), Size(w * 0.9f, h * 0.6f), style = Stroke(w * 0.06f))
            for (i in 0..3) drawLine(Amber, Offset(w * (0.1f + i * 0.22f), h * 0.75f), Offset(w * (0.3f + i * 0.22f), h * 0.25f), w * 0.04f)
        }
        else -> { // 지상식 기둥 · 급수탑 · 저수조
            val p = Path().apply {
                moveTo(w * 0.35f, h * 0.9f); lineTo(w * 0.35f, h * 0.3f); lineTo(w * 0.42f, h * 0.15f); lineTo(w * 0.58f, h * 0.15f)
                lineTo(w * 0.65f, h * 0.3f); lineTo(w * 0.65f, h * 0.9f); close()
            }
            drawPath(p, Red)
            drawRect(Red, Offset(w * 0.2f, h * 0.42f), Size(w * 0.6f, h * 0.12f))
            drawRect(Color.White, Offset(w * 0.25f, h * 0.88f), Size(w * 0.5f, h * 0.06f))
        }
    }
}

@Composable
private fun Arrow(back: Boolean, size: Dp) = Canvas(Modifier.size(size)) {
    val w = this.size.width; val h = this.size.height
    val p = Path().apply {
        if (back) { moveTo(w * 0.5f, h * 0.95f); lineTo(w * 0.1f, h * 0.5f); lineTo(w * 0.35f, h * 0.5f); lineTo(w * 0.35f, h * 0.05f); lineTo(w * 0.65f, h * 0.05f); lineTo(w * 0.65f, h * 0.5f); lineTo(w * 0.9f, h * 0.5f) }
        else { moveTo(w * 0.5f, h * 0.05f); lineTo(w * 0.1f, h * 0.5f); lineTo(w * 0.35f, h * 0.5f); lineTo(w * 0.35f, h * 0.95f); lineTo(w * 0.65f, h * 0.95f); lineTo(w * 0.65f, h * 0.5f); lineTo(w * 0.9f, h * 0.5f) }
        close()
    }
    drawPath(p, Color.White)
}

@Composable
private fun SmallCard(a: ActiveAlert, vm: RedCurbViewModel) {
    Box(Modifier.fillMaxSize().padding(start = 460.dp, end = 60.dp, bottom = 60.dp), contentAlignment = Alignment.BottomCenter) {
        Row(Modifier.fillMaxWidth().background(Color(0xFF3A1416), RoundedCornerShape(32.dp)).border(4.dp, Red, RoundedCornerShape(32.dp)).padding(40.dp),
            verticalAlignment = Alignment.CenterVertically) {
            FacilityIcon(a.target.type, 140.dp)
            Spacer(Modifier.width(40.dp))
            Column(Modifier.weight(1f)) {
                T("정차 · " + Phrases.headline(a), 64.sp, weight = FontWeight.Bold)
                T(if (a.target.isFireLane) "여기는 잠깐 서는 것도 조심해 주세요" else "여기는 잠깐 서는 것도 안 되는 자리예요", 38.sp)
                T(a.target.detail, 32.sp, Muted)
            }
            OutlinedButton(onClick = vm::dismiss, modifier = Modifier.height(110.dp)) { T("닫기", 38.sp) }
        }
    }
}

@Composable
private fun BigCard(a: ActiveAlert, vm: RedCurbViewModel) {
    Box(Modifier.fillMaxSize().background(Color(0xCC000000)).padding(80.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxWidth().background(Color(0xFF2A1012), RoundedCornerShape(40.dp)).border(6.dp, Red, RoundedCornerShape(40.dp)).padding(64.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp)) {
            T(Phrases.headline(a), 120.sp, weight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (a.geometry.suggestMeters != null && !a.lowConfidence) {
                    Arrow(a.geometry.suggestBack, 110.dp); Spacer(Modifier.width(28.dp))
                }
                T(Phrases.moveLine(a), 64.sp, weight = FontWeight.Bold, color = if (a.lowConfidence) Amber else Color.White)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                FacilityIcon(a.target.type, 150.dp)
                Spacer(Modifier.width(36.dp))
                Column {
                    T(a.target.detail.ifBlank { "상세위치 정보 없음" }, 46.sp)
                    T(a.target.type.label, 40.sp, Muted)
                }
            }
            T(if (a.target.isFireLane) "공동주택 소방차 전용구역 · 주차·정차 금지" else Phrases.SUB_LINE, 32.sp, Muted)
            Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                if (!a.target.isFireLane) BigButton("여기는 아니에요", color = Color(0xFF4A5058), modifier = Modifier.weight(1f)) { vm.notHere() }
                BigButton("닫기", color = Red, modifier = Modifier.weight(1f)) { vm.dismiss() }
            }
        }
    }
}

@Composable
private fun ClearedCard(count: Int) {
    Box(Modifier.fillMaxSize().background(Color(0xAA000000)), contentAlignment = Alignment.Center) {
        Column(Modifier.background(Color(0xFF123222), RoundedCornerShape(40.dp)).border(6.dp, Green, RoundedCornerShape(40.dp)).padding(80.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(Modifier.size(200.dp)) {
                drawCircle(Green)
                val p = Path().apply { moveTo(size.width * 0.27f, size.height * 0.52f); lineTo(size.width * 0.44f, size.height * 0.68f); lineTo(size.width * 0.74f, size.height * 0.36f) }
                drawPath(p, Color.White, style = Stroke(size.width * 0.09f))
            }
            Spacer(Modifier.height(32.dp))
            T("비웠어요", 110.sp, weight = FontWeight.Bold)
            T("소방차 물 대는 자리를 지켰어요 · 이번 달 ${Phrases.ordinal(count)}", 48.sp, align = TextAlign.Center)
        }
    }
}
