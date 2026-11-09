package com.decmike.canonremote

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.decmike.canonremote.ui.cropRect
import com.decmike.canonremote.ui.fitRect
import com.decmike.canonremote.ui.parseAspect
import com.decmike.canonremote.ui.screens.parseSeconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CompositionTest {
    @Test fun aspectParsing() {
        assertEquals(1.5f, parseAspect("3:2")!!, 1e-4f)
        assertEquals(16f / 9, parseAspect("16_9")!!, 1e-4f)
        assertEquals(1f, parseAspect("1x1")!!, 1e-4f)
        assertNull(parseAspect("full"))
    }

    @Test fun fitLandscapeImageInTallBox() {
        val r = fitRect(Size(600f, 400f), Size(300f, 400f))
        assertEquals(300f, r.width, 0.01f)
        assertEquals(200f, r.height, 0.01f)
        assertEquals(100f, r.top, 0.01f)
    }

    @Test fun cropToSquareAndPortrait() {
        val img = Rect(Offset(0f, 100f), Size(300f, 200f))
        val sq = cropRect(img, 1f)
        assertEquals(200f, sq.width, 0.01f)
        assertEquals(50f, sq.left, 0.01f)
        val p = cropRect(img, 4f / 5)
        assertEquals(200f, p.height, 0.01f)
        assertEquals(160f, p.width, 0.01f)
        val wide = cropRect(img, 16f / 9)
        assertEquals(300f, wide.width, 0.01f)
        assertEquals(168.75f, wide.height, 0.01f)
    }

    @Test fun secondsParsing() {
        assertEquals(1 / 250.0, parseSeconds("1/250")!!, 1e-9)
        assertEquals(150.0, parseSeconds("2:30")!!, 1e-9)
        assertEquals(1.5, parseSeconds("1,5")!!, 1e-9)
    }
}
