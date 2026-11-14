package com.moyeon.redcurb

import android.content.Context
import com.moyeon.redcurb.engine.FacilityType
import com.moyeon.redcurb.engine.Outcome
import com.moyeon.redcurb.engine.Record
import com.moyeon.redcurb.engine.Target
import org.json.JSONArray
import org.json.JSONObject

/** 기록·전용구역·음소거·말한 날짜를 차 안(SharedPreferences)에만 저장한다. 서버·내보내기 없음. */
class Storage(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("red_curb", Context.MODE_PRIVATE)

    fun save(records: List<Record>, lanes: List<Target>, mutes: Map<String, Long>, spoken: Map<String, Long>) {
        sp.edit()
            .putString("records", JSONArray(records.map {
                JSONObject().put("t", it.timeMillis).put("id", it.targetId).put("detail", it.detail).put("outcome", it.outcome.name).put("demo", it.demo)
            }).toString())
            .putString("lanes", JSONArray(lanes.map {
                JSONObject().put("id", it.id).put("lat", it.lat).put("lon", it.lon).put("label", it.detail).put("createdAt", it.createdAt)
            }).toString())
            .putString("mutes", JSONObject(mutes as Map<*, *>).toString())
            .putString("spoken", JSONObject(spoken as Map<*, *>).toString())
            .apply()
    }

    fun records(): List<Record> = arr("records").map {
        Record(it.getLong("t"), it.getString("id"), it.getString("detail"), Outcome.valueOf(it.getString("outcome")), it.optBoolean("demo"))
    }

    fun lanes(): List<Target> = arr("lanes").map {
        Target(it.getString("id"), it.getDouble("lat"), it.getDouble("lon"), FacilityType.FIRE_LANE, it.getString("label"), createdAt = it.getLong("createdAt"))
    }

    fun mutes(): Map<String, Long> = obj("mutes")
    fun spoken(): Map<String, Long> = obj("spoken")

    private fun arr(key: String): List<JSONObject> = try {
        val a = JSONArray(sp.getString(key, "[]"))
        (0 until a.length()).map { a.getJSONObject(it) }
    } catch (_: Exception) { emptyList() }

    private fun obj(key: String): Map<String, Long> = try {
        val o = JSONObject(sp.getString(key, "{}")!!)
        o.keys().asSequence().associateWith { o.getLong(it) }
    } catch (_: Exception) { emptyMap() }
}
