package com.example.phonecheck

data class DeviceInfo(
    val model: String,
    val androidVersion: String,
    val apiLevel: Int,
    val securityPatch: String,
    val ramGb: Int,
    val storageTotalGb: Int,
    val storageFreeGb: Double,
    val cores: Int,
    val abi: String,
    val refreshHz: Int,
    val batteryHealth: String,
    val batteryCycles: Int
)

/** One scored test. [id] is used to pick buying advice; [max] is the points available. */
data class Check(
    val id: String,
    val title: String,
    val value: String,
    val score: Int,
    val max: Int,
    val advice: String
)

data class ScanResult(
    val info: DeviceInfo,
    val checks: List<Check>,
    val total: Int
)

sealed interface UiState {
    data object Idle : UiState
    data class Scanning(val stage: String, val progress: Float) : UiState
    data class Done(val result: ScanResult) : UiState
}
