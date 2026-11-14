package com.moyeon.redcurb.sdk

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import android.content.Context
import com.moyeon.redcurb.engine.LocationFix

/** Pleos FusedLocation 1.1.3: registerFusedLocationCallback → onChanged(LocationData) */
class LocationAdapter(context: Context, private val diag: Diag, private val sink: (LocationFix) -> Unit) {
    private val fused = FusedLocation(context.applicationContext)
    private var started = false
    var changedCount = 0; private set

    private val listener = object : FusedLocationListener {
        override fun onChanged(data: LocationData?) {
            changedCount++
            if (data == null) { diag.report("FusedLocation.onChanged", "#$changedCount null"); return }
            diag.report("FusedLocation.onChanged", "#$changedCount ${"%.6f".format(data.latitude)},${"%.6f".format(data.longitude)} heading=${data.heading} conf=${data.confidence}")
            sink(LocationFix(data.latitude, data.longitude, data.heading.toDouble(), data.confidence, data.timestampMillis))
        }
        override fun onError(e: Exception) = diag.report("FusedLocation.onError", e.describe())
    }

    fun start() {
        try {
            fused.initialize(); diag.report("FusedLocation.initialize", "호출 성공")
        } catch (e: Throwable) { diag.report("FusedLocation.initialize", "실패: ${e.describe()}"); return }
        try { diag.report("FusedLocation.isSupported", "${fused.isSupported()}") } catch (e: Throwable) { diag.report("FusedLocation.isSupported", "실패: ${e.describe()}") }
        try {
            fused.registerFusedLocationCallback(listener); started = true
            diag.report("FusedLocation.registerFusedLocationCallback", "호출 성공")
        } catch (e: Throwable) { diag.report("FusedLocation.registerFusedLocationCallback", "실패: ${e.describe()}") }
        try { fused.getLastKnownLocation(listener) } catch (e: Throwable) { diag.report("FusedLocation.getLastKnownLocation", "실패: ${e.describe()}") }
    }

    fun stop() {
        try { if (started) fused.unregisterFusedLocationCallback(listener); fused.release() } catch (_: Throwable) {}
        started = false
    }
}
