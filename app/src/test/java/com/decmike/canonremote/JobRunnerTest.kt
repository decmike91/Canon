package com.decmike.canonremote

import com.decmike.canonremote.ccapi.CameraControl
import com.decmike.canonremote.ccapi.CameraSetting
import com.decmike.canonremote.jobs.HdrMethod
import com.decmike.canonremote.jobs.JobProgress
import com.decmike.canonremote.jobs.JobRunner
import com.decmike.canonremote.jobs.ShootJob
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeCamera(mode: String = "m", tv: String = "1/125", comp: String = "0") : CameraControl {
    val log = mutableListOf<String>()
    val settings = mutableMapOf(
        "shootingmodedial" to CameraSetting("shootingmodedial", mode),
        "tv" to CameraSetting("tv", tv, listOf("30\"", "15\"", "1\"", "1/4", "1/8", "1/15", "1/30", "1/60", "1/125", "1/250", "1/500", "1/1000", "1/2000", "bulb")),
        "exposure" to CameraSetting("exposure", comp, listOf("-3", "-2", "-1", "0", "+1", "+2", "+3")),
    )
    override suspend fun getSetting(key: String) = settings[key]
    override suspend fun setSetting(key: String, value: String) {
        log += "set $key=$value"; settings[key] = settings[key]!!.copy(value = value)
    }
    override suspend fun shoot(af: Boolean) { log += "shoot" }
    override suspend fun shutterManual(action: String, af: Boolean) { log += "manual $action" }
    override suspend fun driveFocus(step: String) { log += "focus $step" }
}

class JobRunnerTest {
    private fun runner(cam: FakeCamera, sink: MutableList<JobProgress>) = JobRunner(cam) { sink += it }

    @Test fun hdrShutterBracket() = runTest {
        val cam = FakeCamera()
        val p = mutableListOf<JobProgress>()
        runner(cam, p).run(ShootJob.Hdr(shots = 5, stepEv = 1.0, method = HdrMethod.AUTO))
        assertEquals(
            listOf("shoot", "set tv=1/250", "shoot", "set tv=1/60", "shoot",
                "set tv=1/500", "shoot", "set tv=1/30", "shoot", "set tv=1/125"),
            cam.log
        )
        assertNull(p.last().error)
        assertEquals("1/125", cam.settings["tv"]!!.value)
    }

    @Test fun hdrCompensationInAv() = runTest {
        val cam = FakeCamera(mode = "av")
        runner(cam, mutableListOf()).run(ShootJob.Hdr(shots = 3, stepEv = 2.0))
        assertTrue(cam.log.containsAll(listOf("set exposure=-2", "set exposure=+2")))
        assertEquals("0", cam.settings["exposure"]!!.value)
        assertEquals(3, cam.log.count { it == "shoot" })
    }

    @Test fun intervalBulb() = runTest {
        val cam = FakeCamera()
        val p = mutableListOf<JobProgress>()
        runner(cam, p).run(ShootJob.Interval(count = 3, exposureSec = 120.0, intervalSec = 5.0))
        assertEquals("set tv=bulb", cam.log.first())
        assertEquals(3, cam.log.count { it == "manual full_press" })
        assertEquals(3, cam.log.count { it == "manual release" })
        assertTrue(p.last().finished)
        assertNull(p.last().error)
    }

    @Test fun intervalNormalShutter() = runTest {
        val cam = FakeCamera()
        runner(cam, mutableListOf()).run(ShootJob.Interval(count = 4, exposureSec = 1.0 / 30, intervalSec = 2.0))
        assertEquals("set tv=1/30", cam.log.first())
        assertEquals(4, cam.log.count { it == "shoot" })
    }

    @Test fun bulbUnavailableReportsError() = runTest {
        val cam = FakeCamera()
        cam.settings["tv"] = cam.settings["tv"]!!.copy(options = listOf("1/125", "30\""))
        val p = mutableListOf<JobProgress>()
        runner(cam, p).run(ShootJob.Interval(count = 2, exposureSec = 300.0, intervalSec = 1.0))
        assertTrue(p.last().error != null)
    }

    @Test fun focusStack() = runTest {
        val cam = FakeCamera()
        runner(cam, mutableListOf()).run(ShootJob.FocusStack(shots = 3, stepSize = 2, stepsPerShot = 2))
        assertEquals(listOf("shoot", "focus far2", "focus far2", "shoot", "focus far2", "focus far2", "shoot"), cam.log)
    }
}

/** Télécommande sans accès aux réglages (cas du Bluetooth). */
class FakeBleCamera : CameraControl {
    val log = mutableListOf<String>()
    override val supportsSettings get() = false
    override suspend fun getSetting(key: String): CameraSetting? = null
    override suspend fun setSetting(key: String, value: String) = throw UnsupportedOperationException()
    override suspend fun shoot(af: Boolean) { log += "shoot" }
    override suspend fun shutterManual(action: String, af: Boolean) { log += "manual $action" }
    override suspend fun driveFocus(step: String) = throw UnsupportedOperationException()
}

class BleJobTest {
    @Test fun bulbIntervalOverBluetooth() = runTest {
        val cam = FakeBleCamera()
        val p = mutableListOf<JobProgress>()
        JobRunner(cam) { p += it }.run(ShootJob.Interval(count = 2, exposureSec = 60.0, intervalSec = 3.0))
        assertEquals(listOf("manual full_press", "manual release", "manual full_press", "manual release"), cam.log)
        assertNull(p.last().error)
    }

    @Test fun shortExposureOverBluetooth() = runTest {
        val cam = FakeBleCamera()
        JobRunner(cam) {}.run(ShootJob.Interval(count = 3, exposureSec = null, intervalSec = 1.0))
        assertEquals(3, cam.log.count { it == "shoot" })
    }

    @Test fun hdrRefusedOverBluetooth() = runTest {
        val p = mutableListOf<JobProgress>()
        JobRunner(FakeBleCamera()) { p += it }.run(ShootJob.Hdr(shots = 3, stepEv = 1.0))
        assertTrue(p.last().error!!.contains("Wi-Fi"))
    }
}
