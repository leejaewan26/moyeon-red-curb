package com.moyeon.redcurb.sdk

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import java.util.Locale
import android.speech.tts.TextToSpeech as AndroidTts

/**
 * 음성: Pleos TextToSpeech 3.3.1 ON_DEVICE 가 1순위.
 * 초기화가 실패하거나 말하기 오류가 나면 Android 기본 android.speech.tts.TextToSpeech 로 대체(명세 8 '첫 30분 확인').
 */
class Speaker(context: Context, private val diag: Diag, private val onEngine: (String) -> Unit) {
    private val app = context.applicationContext
    private var pleos: TextToSpeech? = null
    private var pleosReady = false
    private var fallback: AndroidTts? = null
    private var androidReady = false
    private var lastText: String? = null

    fun start() {
        try {
            val tts = TextToSpeech(app, Mode.ON_DEVICE)
            pleos = tts
            tts.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    pleosReady = true
                    diag.report("TTS.initialize(ON_DEVICE)", "성공")
                    try { tts.addEventListener(object : EventListener {
                    override fun onStart() = diag.report("TTS.onStart", "")
                    override fun onReady() = diag.report("TTS.onReady", "")
                    override fun onDone() = diag.report("TTS.onDone", "")
                    override fun onStop() = diag.report("TTS.onStop", "")
                    override fun onUpdatedRms(rms: Double) {}
                    override fun onError(error: ErrorType) {
                        diag.report("TTS.onError", "$error → 이번 문장은 Android TTS 로 대체")
                        lastText?.let { speakAndroid(it) }
                    }
                    }) } catch (e: Throwable) { diag.report("TTS.addEventListener", "실패: ${e.describe()}") }
                    // "ko"(Locale.KOREAN)만 주면 엔진이 "unsupported locale: ko-" 로 실패한다(에뮬레이터 로그로 확인) → ko_KR
                    try { tts.setLocale(Locale.KOREA) } catch (e: Throwable) { diag.report("TTS.setLocale", "실패: ${e.describe()}") }
                    onEngine("Pleos TTS (ON_DEVICE)")
                }
                override fun onFailure(error: ErrorType) {
                    diag.report("TTS.initialize(ON_DEVICE)", "실패: $error → Android TTS 로 대체")
                    onEngine("Android 기본 TTS(대체)")
                }
            })
        } catch (e: Throwable) {
            diag.report("TTS.initialize(ON_DEVICE)", "실패: ${e.describe()} → Android TTS 로 대체")
            onEngine("Android 기본 TTS(대체)")
        }
        fallback = AndroidTts(app) { status ->
            androidReady = status == AndroidTts.SUCCESS
            val lang = if (androidReady) fallback?.setLanguage(Locale.KOREA) else null
            diag.report("AndroidTTS.init", "status=$status lang=$lang")
        }
    }

    fun speak(text: String) {
        lastText = text
        val p = pleos
        if (p != null && pleosReady) {
            try {
                p.speak(text, true) // 두 번째 인자 의미(큐 비우기 추정) [미확인]
                diag.report("TTS.speak", text.take(40))
                return
            } catch (e: Throwable) {
                diag.report("TTS.speak", "실패: ${e.describe()} → Android TTS")
            }
        }
        speakAndroid(text)
    }

    private fun speakAndroid(text: String) {
        val a = fallback
        if (a != null && androidReady) {
            val r = a.speak(text, AndroidTts.QUEUE_FLUSH, null, "redcurb")
            diag.report("AndroidTTS.speak", "result=$r ${text.take(30)}")
        } else diag.report("AndroidTTS.speak", "준비 안 됨: ${text.take(30)}")
    }

    fun stop() {
        try { pleos?.release() } catch (_: Throwable) {}
        try { fallback?.shutdown() } catch (_: Throwable) {}
    }
}
