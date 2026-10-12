package com.example.phonecheck

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.view.Choreographer
import android.view.WindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.coroutines.resume
import kotlin.math.max

/**
 * All the thresholds live here so you can tune them. The CPU numbers are rough
 * guesses: run the app on a phone you know is fast and one you know is slow,
 * then adjust until the scores feel right.
 */
object Thresholds {
    // CPU benchmark time in ms (lower is better): <fast -> full marks ... else 1 point
    const val CPU_EXCELLENT = 600L
    const val CPU_GOOD = 1000L
    const val CPU_OK = 1600L
    const val CPU_SLOW = 2600L
}

object Scanner {

    @Volatile
    private var sink: Double = 0.0

    suspend fun run(ctx: Context, onProgress: (String, Float) -> Unit): ScanResult {
        val app = ctx.applicationContext

        onProgress("Reading device details…", 0.05f)
        val info = readInfo(app)
        delay(500)

        onProgress("Testing processor speed…", 0.2f)
        val cpuMs = withContext(Dispatchers.Default) { cpuBenchmark() }

        onProgress("Testing storage speed…", 0.5f)
        val writeMbps = withContext(Dispatchers.IO) { storageWriteSpeed(app) }

        onProgress("Testing smoothness under load…", 0.7f)
        val jankPct = frameTest(info.refreshHz, 3000L)

        onProgress("Calculating your score…", 0.95f)
        delay(400)

        val checks = listOf(
            osCheck(info),
            patchCheck(info),
            ramCheck(info),
            storageCheck(info),
            cpuCheck(cpuMs),
            diskCheck(writeMbps),
            batteryCheck(info),
            smoothCheck(jankPct)
        )
        return ScanResult(info, checks, checks.sumOf { it.score })
    }

    // ---------- Reading device details ----------

    private fun readInfo(ctx: Context): DeviceInfo {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mem = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val ramGib = mem.totalMem / GIB
        val ramGb = listOf(2, 3, 4, 6, 8, 12, 16, 24).firstOrNull { it >= ramGib } ?: 24

        val stat = StatFs(Environment.getDataDirectory().path)
        val totalGib = stat.totalBytes / GIB
        val storageTotal = listOf(16, 32, 64, 128, 256, 512, 1024).firstOrNull { it >= totalGib } ?: 1024
        val freeGb = stat.availableBytes / GIB

        @Suppress("DEPRECATION")
        val hz = (ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
            .defaultDisplay.refreshRate.toInt().coerceAtLeast(30)

        val battery: Intent? = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val health = when (battery?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheating"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
            else -> "Unknown"
        }
        // EXTRA_CYCLE_COUNT exists from Android 14; most phones don't report it.
        val cycles = battery?.getIntExtra("android.os.extra.CYCLE_COUNT", -1) ?: -1

        return DeviceInfo(
            model = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}",
            androidVersion = Build.VERSION.RELEASE,
            apiLevel = Build.VERSION.SDK_INT,
            securityPatch = Build.VERSION.SECURITY_PATCH ?: "Unknown",
            ramGb = ramGb,
            storageTotalGb = storageTotal,
            storageFreeGb = freeGb,
            cores = Runtime.getRuntime().availableProcessors(),
            abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "?",
            refreshHz = hz,
            batteryHealth = health,
            batteryCycles = cycles
        )
    }

    private const val GIB = 1024.0 * 1024.0 * 1024.0

    // ---------- Benchmarks ----------

    private fun cpuWork(): Double {
        val n = 3_000_000
        val composite = BooleanArray(n + 1)
        var count = 0
        for (i in 2..n) {
            if (!composite[i]) {
                count++
                var j = i.toLong() * i
                while (j <= n) {
                    composite[j.toInt()] = true
                    j += i
                }
            }
        }
        var x = 0.0
        for (i in 1..4_000_000) {
            x += Math.sqrt(i.toDouble()) * Math.sin(i * 0.001)
        }
        return x + count
    }

    /** Best of two runs, in milliseconds. */
    private fun cpuBenchmark(): Long {
        var best = Long.MAX_VALUE
        repeat(2) {
            val t = System.nanoTime()
            sink = cpuWork()
            best = minOf(best, (System.nanoTime() - t) / 1_000_000)
        }
        return best
    }

    /** Sequential write speed in MB/s, or -1 if there isn't enough free space to test. */
    private fun storageWriteSpeed(ctx: Context): Double {
        val file = File(ctx.cacheDir, "bench.tmp")
        val mb = 48
        if (ctx.cacheDir.usableSpace < 200L * 1024 * 1024) return -1.0
        val buf = ByteArray(1024 * 1024) { (it * 31).toByte() }
        return try {
            val t = System.nanoTime()
            FileOutputStream(file).use { out ->
                repeat(mb) { out.write(buf) }
                out.flush()
                out.fd.sync()
            }
            val sec = (System.nanoTime() - t) / 1e9
            mb / sec
        } catch (e: Exception) {
            -1.0
        } finally {
            file.delete()
        }
    }

    /**
     * Counts frames for [durationMs] on the main thread while background threads
     * keep half the CPU cores busy (like other apps running). Returns the percentage
     * of frames that took clearly longer than one refresh period.
     */
    private suspend fun frameTest(refreshHz: Int, durationMs: Long): Double =
        withContext(Dispatchers.Main) {
            val running = AtomicBoolean(true)
            val workers = (1..max(1, Runtime.getRuntime().availableProcessors() / 2)).map {
                thread(isDaemon = true) {
                    var x = 1.0
                    while (running.get()) x += Math.sqrt(x) + Math.sin(x)
                    sink = x
                }
            }
            val frames = ArrayList<Long>()
            try {
                suspendCancellableCoroutine<Unit> { cont ->
                    val start = System.nanoTime()
                    val choreographer = Choreographer.getInstance()
                    val cb = object : Choreographer.FrameCallback {
                        override fun doFrame(frameTimeNanos: Long) {
                            frames.add(frameTimeNanos)
                            if ((frameTimeNanos - start) / 1_000_000 >= durationMs) {
                                if (cont.isActive) cont.resume(Unit)
                            } else {
                                choreographer.postFrameCallback(this)
                            }
                        }
                    }
                    choreographer.postFrameCallback(cb)
                    cont.invokeOnCancellation { choreographer.removeFrameCallback(cb) }
                }
            } finally {
                running.set(false)
                workers.forEach { it.join(200) }
            }
            val periodMs = 1000.0 / refreshHz
            val intervals = frames.zipWithNext { a, b -> (b - a) / 1e6 }
            if (intervals.isEmpty()) return@withContext 100.0
            val dropped = intervals.count { it > periodMs * 1.5 }
            100.0 * dropped / intervals.size
        }

    // ---------- Scoring (total = 100) ----------

    private fun osCheck(i: DeviceInfo): Check {
        val s = when {
            i.apiLevel >= 35 -> 20
            i.apiLevel == 34 -> 17
            i.apiLevel == 33 -> 14
            i.apiLevel >= 31 -> 10
            i.apiLevel == 30 -> 6
            i.apiLevel == 29 -> 3
            else -> 1
        }
        val advice = if (s >= 14) "Your Android version is still recent."
        else "Android ${i.androidVersion} is old. New apps and security fixes are moving on without it."
        return Check("os", "Android version", "Android ${i.androidVersion} (API ${i.apiLevel})", s, 20, advice)
    }

    private fun patchCheck(i: DeviceInfo): Check {
        val months = try {
            ChronoUnit.MONTHS.between(LocalDate.parse(i.securityPatch), LocalDate.now()).toInt()
        } catch (e: Exception) {
            -1
        }
        val s = when {
            months < 0 -> 3
            months <= 3 -> 10
            months <= 6 -> 8
            months <= 12 -> 5
            months <= 24 -> 2
            else -> 0
        }
        val value = if (months < 0) "Unknown" else "${i.securityPatch} ($months months old)"
        val advice = if (s >= 8) "Security patches are current."
        else "No recent security patches. Banking and UPI apps on this phone carry more risk."
        return Check("patch", "Security patch", value, s, 10, advice)
    }

    private fun ramCheck(i: DeviceInfo): Check {
        val s = when {
            i.ramGb >= 8 -> 15
            i.ramGb >= 6 -> 12
            i.ramGb >= 4 -> 8
            i.ramGb >= 3 -> 5
            else -> 2
        }
        val advice = if (s >= 12) "Plenty of RAM for multitasking."
        else "${i.ramGb} GB RAM means apps get closed in the background and reload often."
        return Check("ram", "RAM", "${i.ramGb} GB", s, 15, advice)
    }

    private fun storageCheck(i: DeviceInfo): Check {
        val s = when {
            i.storageFreeGb >= 32 -> 15
            i.storageFreeGb >= 16 -> 11
            i.storageFreeGb >= 8 -> 7
            i.storageFreeGb >= 4 -> 3
            else -> 1
        }
        val value = "%.1f GB free of %d GB".format(i.storageFreeGb, i.storageTotalGb)
        val advice = if (s >= 11) "You have comfortable free space."
        else "Low free space slows the phone and blocks app and system updates."
        return Check("storage", "Free storage", value, s, 15, advice)
    }

    private fun cpuCheck(ms: Long): Check {
        val s = when {
            ms < Thresholds.CPU_EXCELLENT -> 15
            ms < Thresholds.CPU_GOOD -> 12
            ms < Thresholds.CPU_OK -> 8
            ms < Thresholds.CPU_SLOW -> 4
            else -> 1
        }
        val advice = if (s >= 12) "The processor is still quick."
        else "The processor is slow at heavy work like games, camera and big apps."
        return Check("cpu", "Processor speed", "$ms ms (lower is better)", s, 15, advice)
    }

    private fun diskCheck(mbps: Double): Check {
        if (mbps < 0) return Check("disk", "Storage speed", "Couldn't test (not enough space)", 1, 5,
            "Free up some space to test storage speed.")
        val s = when {
            mbps >= 120 -> 5
            mbps >= 60 -> 4
            mbps >= 30 -> 3
            mbps >= 10 -> 2
            else -> 1
        }
        val advice = if (s >= 4) "Storage is fast, so apps open quickly."
        else "Slow storage makes app launches and installs feel laggy."
        return Check("disk", "Storage speed", "%.0f MB/s write".format(mbps), s, 5, advice)
    }

    private fun batteryCheck(i: DeviceInfo): Check {
        var s = if (i.batteryHealth == "Good") 10 else if (i.batteryHealth == "Unknown") 6 else 3
        if (i.batteryCycles > 1000) s = minOf(s, 4) else if (i.batteryCycles > 700) s = minOf(s, 7)
        val cycles = if (i.batteryCycles >= 0) ", ${i.batteryCycles} cycles" else ""
        val advice = when {
            s >= 9 -> "Battery reports healthy. Note that Android can't show real capacity loss on most phones."
            s >= 6 -> "The phone doesn't report battery wear. If it dies early, a battery swap may be cheaper than a new phone."
            else -> "Battery looks worn. A replacement might extend the phone's life."
        }
        return Check("battery", "Battery", "${i.batteryHealth}$cycles", s, 10, advice)
    }

    private fun smoothCheck(jankPct: Double): Check {
        val s = when {
            jankPct < 5 -> 10
            jankPct < 12 -> 8
            jankPct < 25 -> 5
            jankPct < 40 -> 3
            else -> 1
        }
        val advice = if (s >= 8) "Animations stay smooth even with other work running."
        else "The screen stutters when the phone is busy. You'll notice this as lag."
        return Check("smooth", "Smoothness under load", "%.0f%% dropped frames".format(jankPct), s, 10, advice)
    }
}
