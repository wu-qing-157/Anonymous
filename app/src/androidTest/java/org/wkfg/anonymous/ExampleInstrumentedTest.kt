package org.wkfg.anonymous

import android.util.Log
import android.util.Patterns
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.ExperimentalTime

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
@ExperimentalTime
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("personal.wuqing.anonymous", appContext.packageName)
    }

    @Test
    fun m() {
        Log.d("tryshabi", "tried")
    }

    @Test
    fun sendEmail() {
        println(Patterns.WEB_URL.pattern())
        Log.d("ppppp", Patterns.WEB_URL.pattern())
    }
}