package com.moyeon.redcurb.sdk

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import ai.pleos.playground.vehicle.listener.SeatOccupancyListener
import android.car.VehicleGear
import android.content.Context
import com.moyeon.redcurb.engine.Gear

/**
 * Pleos Vehicle SDK 2.3.2 어댑터. 기어·운전석 문·운전석 착석 콜백을 엔진 입력으로 바꾼다.
 * 연결 방식은 공식 샘플 VehicleClient(Vehicle(context).initialize()) 를 따랐다.
 * 모든 호출은 try/catch — 에뮬레이터에서 실패해도 화면은 죽지 않고 Diag 에 남는다.
 * 기어: Pleos Gear 도메인은 SDK 2.3.2에서 deprecated·공개 문서 제외 → SDK 안내대로 AAOS 표준 GEAR_SELECTION(CarPropertyManager)으로 읽음([GearSelectionReader], 전용 스레드).
 */
class VehicleAdapter(context: Context, private val diag: Diag, private val sink: Sink) {

    interface Sink {
        fun onGear(gear: Gear)
        fun onDriverDoor(open: Boolean)
        fun onDriverSeat(occupied: Boolean)
    }

    val vehicle = Vehicle(context.applicationContext)
    var initialized = false; private set

    private val doorArea = DoorArea(DoorValue.ROW_1_LEFT)
    private val seatArea = SeatArea(SeatValue.ROW_1_LEFT)

    private val gearReader = GearSelectionReader(
        context,
        onGear = { raw -> sink.onGear(map(raw)) }, // Sink 쪽(fromSdk)이 메인으로 넘긴다
        onResult = { api, ok, detail -> diag.report(api, if (ok) detail else "실패: $detail") },
    )

    private val doorListener = object : DoorPositionListener {
        override fun onPositionUpdated(pos: Int?) {
            diag.report("Door.onPositionUpdated(ROW_1_LEFT)", "$pos")
            if (pos != null) sink.onDriverDoor(pos > 0) // 0=닫힘으로 가정 [미확인]
        }
        override fun onFailed(e: Exception) = diag.report("Door.onFailed", e.describe())
    }

    private val seatListener = object : SeatOccupancyListener {
        override fun onSeatOccupancyUpdated(occupied: Boolean?) {
            diag.report("Seat.onSeatOccupancyUpdated(ROW_1_LEFT)", "$occupied")
            if (occupied != null) sink.onDriverSeat(occupied)
        }
        override fun onFailed(e: Exception) = diag.report("Seat.onFailed", e.describe())
    }

    fun start() {
        gearReader.start()
        initialized = call("Vehicle.initialize") { vehicle.initialize() }
        if (!initialized) return
        val door = vehicle.getDoor()
        val seat = vehicle.getSeat()
        call("Door.registerDoorPosition(ROW_1_LEFT)") { door.registerDoorPosition(doorArea, doorListener) }
        call("Seat.registerSeatOccupancy(ROW_1_LEFT)") { seat.registerSeatOccupancy(seatArea, seatListener) }
        // 처음 값 한 번 읽기
        call("Door.getCurrentDoorPosition") {
            door.getCurrentDoorPosition(doorArea,
                { p -> diag.report("Door.getCurrent", "$p"); if (p != null) sink.onDriverDoor(p > 0) },
                { e -> diag.report("Door.getCurrent", "실패: ${e.describe()}") })
        }
        call("Seat.getCurrentSeatOccupancy") {
            seat.getCurrentSeatOccupancy(seatArea,
                { o -> diag.report("Seat.getCurrent", "$o"); if (o != null) sink.onDriverSeat(o) },
                { e -> diag.report("Seat.getCurrent", "실패: ${e.describe()}") })
        }
    }

    fun stop() {
        gearReader.stop()
        if (!initialized) return
        call("Door.unregisterDoorPosition") { vehicle.getDoor().unregisterDoorPosition(doorArea, doorListener) }
        call("Seat.unregisterSeatOccupancy") { vehicle.getSeat().unregisterSeatOccupancy(seatArea, seatListener) }
        call("Vehicle.release") { vehicle.release() }
        initialized = false
    }

    /** android.car.VehicleGear(Int) → 엔진 기어. */
    private fun map(g: Int): Gear = when (g) {
        VehicleGear.GEAR_PARK -> Gear.PARK
        VehicleGear.GEAR_REVERSE -> Gear.REVERSE
        VehicleGear.GEAR_NEUTRAL -> Gear.NEUTRAL
        VehicleGear.GEAR_UNKNOWN -> Gear.UNKNOWN
        else -> Gear.DRIVE // GEAR_DRIVE, GEAR_1..9
    }

    private inline fun call(name: String, block: () -> Unit): Boolean = try {
        block(); diag.report(name, "호출 성공"); true
    } catch (e: Throwable) {
        diag.report(name, "실패: ${e.describe()}"); false
    }
}

internal fun Throwable.describe(): String = "${this::class.java.simpleName}: ${message ?: ""}".take(160)
