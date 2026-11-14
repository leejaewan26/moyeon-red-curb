package com.moyeon.redcurb.engine

import kotlin.math.roundToInt

/** 명세 4-4 문구. 음성은 "미터", 화면은 "m". */
object Phrases {
    const val SUB_LINE = "주민신고 대상 · 과태료 8만 원 이상"
    const val FIRE_LANE = "여기는 소방차 전용구역이에요. 불이 나면 사다리차가 서는 자리예요."

    fun num(m: Double): String = if (m % 1.0 == 0.0) m.toInt().toString() else String.format(java.util.Locale.ROOT, "%.1f", m)

    /** 받침 있으면 "이", 없으면 "가" */
    fun iGa(word: String): String {
        val c = word.lastOrNull() ?: return "이"
        if (c !in '가'..'힣') return "이"
        return if ((c - '가') % 28 != 0) "이" else "가"
    }

    private fun dist(g: Geometry) = g.distance.roundToInt()
    private fun dir(g: Geometry) = if (g.suggestBack) "뒤로" else "앞으로"

    fun speech(a: ActiveAlert): String {
        val n = a.target.type.noun
        val g = a.geometry
        return when {
            a.target.isFireLane -> FIRE_LANE
            a.lowConfidence -> "근처에 $n${iGa(n)} 있어요. 위치가 흔들려서, 주변을 한 번 봐 주세요."
            a.infoOnly -> "${g.side.word} 약 ${dist(g)}미터에 $n${iGa(n)} 있어요. 5미터 밖이면 괜찮아요, 한 번 확인해 주세요."
            a.mode == AlertMode.STOP -> "여기는 잠깐 서는 것도 안 되는 자리예요. $n${iGa(n)} ${g.side.word} ${dist(g)}미터쯤 있어요."
            else -> "잠깐만요. ${g.side.word} ${dist(g)}미터쯤 $n${iGa(n)} 있어요. 불이 나면 소방차가 물을 대는 자리예요. " +
                "${dir(g)} ${num(g.suggestMeters!!)}미터만 옮기면 괜찮아요."
        }
    }

    /** 카드 큰 숫자 줄 */
    fun headline(a: ActiveAlert): String = when {
        a.target.isFireLane -> "소방차 전용구역"
        a.lowConfidence -> "근처 · ${a.target.type.noun}"
        else -> "${a.geometry.side.word} ${dist(a.geometry)}m · ${a.target.type.noun}"
    }

    /** 카드 둘째 줄 */
    fun moveLine(a: ActiveAlert): String = when {
        a.lowConfidence -> "위치가 흔들려요. 주변을 한 번 봐 주세요"
        a.target.isFireLane && a.geometry.suggestMeters == null -> "불이 나면 사다리차가 서는 자리예요"
        a.geometry.suggestMeters == null -> "5m 밖이면 괜찮아요, 한 번 확인해 주세요"
        else -> "${dir(a.geometry)} 약 ${num(a.geometry.suggestMeters)}m 옮기면 벗어나요"
    }

    private val ordinals = listOf("첫", "두", "세", "네", "다섯", "여섯", "일곱", "여덟", "아홉", "열")
    fun ordinal(n: Int): String = if (n in 1..10) "${ordinals[n - 1]} 번째" else "${n}번째"

    fun cleared(monthCount: Int) = "비웠어요. 소방차 물 대는 자리를 지켰어요. 이번 달 ${ordinal(monthCount)}예요."
}
