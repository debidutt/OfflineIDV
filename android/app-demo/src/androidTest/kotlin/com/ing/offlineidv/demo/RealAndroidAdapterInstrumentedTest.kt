package com.ing.offlineidv.demo

import android.Manifest
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ing.offlineidv.camera.real.InMemoryCapturedImageStore
import com.ing.offlineidv.core.result.IdvResult
import com.ing.offlineidv.core.session.IdvSessionId
import com.ing.offlineidv.nfc.NfcCapability
import com.ing.offlineidv.nfc.real.AndroidNfcCapabilityDetector
import com.ing.offlineidv.ocr.OcrDocumentInput
import com.ing.offlineidv.ocr.OcrEngineResult
import com.ing.offlineidv.ocr.OcrImage
import com.ing.offlineidv.ocr.OcrImageSource
import com.ing.offlineidv.ocr.real.MlKitOcrEngine
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
public class RealAndroidAdapterInstrumentedTest {
    @Test
    public fun `packaged app requests camera NFC and vibration but not network access`() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val permissions =
            context.packageManager
                .getPackageInfo(context.packageName, 0x00001000)
                .requestedPermissions
                .orEmpty()
                .toSet()

        assertTrue(Manifest.permission.CAMERA in permissions)
        assertTrue(Manifest.permission.NFC in permissions)
        assertTrue(Manifest.permission.VIBRATE in permissions)
        assertFalse(Manifest.permission.INTERNET in permissions)
        assertFalse(Manifest.permission.ACCESS_NETWORK_STATE in permissions)
    }

    @Test
    public fun `Android NFC capability detector reports a finite safe state`() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        val status = AndroidNfcCapabilityDetector(context).detect()

        assertTrue(status in NfcCapability.entries)
    }

    @Test
    public fun `real capture store returns opaque artifact`() {
        val session = (IdvSessionId.parse("atlas_device_capture") as IdvResult.Success).value
        val store = InMemoryCapturedImageStore(session)

        val stored = store.store(session, byteArrayOf(1, 2, 3), 1_200, 800, 90)

        assertTrue(stored is IdvResult.Success)
        assertTrue((stored as IdvResult.Success).value.toString().contains("[REDACTED]"))
    }

    @Test
    public fun `bundled ML Kit recognizer executes without a downloaded model gate`() {
        val image = staticTextImage()
        val engine = MlKitOcrEngine(OcrImageSource { IdvResult.Success(OcrImage.copyOf(image, 0)) })
        val latch = CountDownLatch(1)
        var observation: OcrEngineResult? = null

        engine.recognize(OcrDocumentInput(1)) {
            observation = it
            latch.countDown()
        }

        assertTrue(latch.await(30, TimeUnit.SECONDS))
        assertTrue(observation is OcrEngineResult.Recognized)
        engine.close()
    }

    private fun staticTextImage(): ByteArray {
        val bitmap = Bitmap.createBitmap(800, 300, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint =
            Paint().apply {
                color = Color.BLACK
                textSize = 54f
                isAntiAlias = true
            }
        canvas.drawText("P<UTO TESTER", 30f, 120f, paint)
        canvas.drawText("A12B34567 UTO 900101", 30f, 220f, paint)
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        bitmap.recycle()
        return output.toByteArray()
    }
}
