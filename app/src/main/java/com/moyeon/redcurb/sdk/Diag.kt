package com.moyeon.redcurb.sdk

/** SDK 호출 결과를 화면(시나리오 화면의 'SDK 상태')과 logcat 에 남긴다. */
fun interface Diag {
    fun report(key: String, value: String)
}
