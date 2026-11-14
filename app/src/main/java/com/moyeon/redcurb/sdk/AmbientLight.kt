package com.moyeon.redcurb.sdk

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.control.LightSwitch

/**
 * 무드등: 원래 색·스위치를 저장 → ON → 빨강, 끝나면 원래대로.
 * 영역은 SeatArea(ROW_1_LEFT) 먼저, 실패하면 checkAmbientLightBrightnessColorCapabilities 가 준 첫 영역.
 * 색 정수 형식(RGB/ARGB/팔레트)은 공개 문서에 없다 [미확인] → 0xFF0000(RGB) 로 쓰고 결과를 Diag 에 남긴다.
 */
class AmbientLight(private val vehicleProvider: () -> Vehicle?, private val diag: Diag) {
    companion object { const val RED = 0xFF0000 }

    private var area = SeatArea(SeatValue.ROW_1_LEFT)
    private var savedColor: Int? = null
    private var savedSwitch: LightSwitch? = null
    var isRed = false; private set
    private var triedCapabilities = false

    fun red() {
        val light = vehicleProvider()?.getLight() ?: run { diag.report("Light", "Vehicle 미연결"); return }
        isRed = true
        try {
            light.getAmbientLightBrightnessColor(area, { c ->
                if (savedColor == null) savedColor = c
                diag.report("Light.getAmbientLightBrightnessColor", "$c")
                applyRed()
            }, { e ->
                diag.report("Light.getAmbientLightBrightnessColor", "실패: ${e.describe()}")
                if (!triedCapabilities) findAreaThen { red() } else applyRed()
            })
            light.getAmbientLightSwitch(area, { s ->
                if (savedSwitch == null) savedSwitch = s
                diag.report("Light.getAmbientLightSwitch", "$s")
            }, { e -> diag.report("Light.getAmbientLightSwitch", "실패: ${e.describe()}") })
        } catch (e: Throwable) {
            diag.report("Light.red", "실패: ${e.describe()}")
        }
    }

    private fun applyRed() {
        val light = vehicleProvider()?.getLight() ?: return
        try {
            light.setAmbientLightSwitch(area, LightSwitch.ON,
                { _, v -> diag.report("Light.setAmbientLightSwitch", "ON → $v") },
                { e -> diag.report("Light.setAmbientLightSwitch", "실패: ${e.describe()}") })
            light.setAmbientLightBrightnessColor(area, RED,
                { _, v -> diag.report("Light.setAmbientLightBrightnessColor", "0xFF0000 → $v") },
                { e -> diag.report("Light.setAmbientLightBrightnessColor", "실패: ${e.describe()}") })
        } catch (e: Throwable) {
            diag.report("Light.applyRed", "실패: ${e.describe()}")
        }
    }

    private fun findAreaThen(next: () -> Unit) {
        triedCapabilities = true
        val light = vehicleProvider()?.getLight() ?: return
        try {
            light.checkAmbientLightBrightnessColorCapabilities({ cfg, areas ->
                diag.report("Light.checkAmbientLightBrightnessColorCapabilities", "$cfg / ${areas.map { it.values.joinToString() }}")
                areas.firstOrNull()?.let { area = it; next() }
            }, { e -> diag.report("Light.checkAmbientLightBrightnessColorCapabilities", "실패: ${e.describe()}") })
        } catch (e: Throwable) {
            diag.report("Light.capabilities", "실패: ${e.describe()}")
        }
    }

    fun restore() {
        if (!isRed) return
        isRed = false
        val light = vehicleProvider()?.getLight() ?: return
        try {
            savedColor?.let { c ->
                light.setAmbientLightBrightnessColor(area, c,
                    { _, v -> diag.report("Light.restoreColor", "$v") },
                    { e -> diag.report("Light.restoreColor", "실패: ${e.describe()}") })
            }
            savedSwitch?.let { s ->
                if (s != LightSwitch.ON) light.setAmbientLightSwitch(area, s,
                    { _, v -> diag.report("Light.restoreSwitch", "$v") },
                    { e -> diag.report("Light.restoreSwitch", "실패: ${e.describe()}") })
            }
        } catch (e: Throwable) {
            diag.report("Light.restore", "실패: ${e.describe()}")
        }
        savedColor = null
        savedSwitch = null
    }
}
