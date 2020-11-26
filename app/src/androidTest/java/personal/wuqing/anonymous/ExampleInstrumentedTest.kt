package personal.wuqing.anonymous

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.*
import kotlin.system.measureTimeMillis
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
        println("shabi")
        Log.d("tryshabi", "tried")
    }

    @Test
    fun sendEmail() {
        var cnt = 0
        fun test(des: String, type: Network.PostType) = Thread {
            GlobalScope.launch {
                for (j in 1..10000) try {
                    Log.d(
                        "ServerDelayTest_$des",
                        String.format("%d: %.3f ms", ++cnt, measureTimeMillis {
                            Network.post(
                                UUID.randomUUID().toString(),
                                Post.Category.SOCIAL,
                                UUID.randomUUID().toString(),
                                NameTheme.US_PRESIDENT,
                                false
                            )
                            val posts = Network.fetchPost(type, Post.Category.ALL, "NULL")
                            Network.favorPost(posts.first().id)
                        }.toDouble() / 1000)
                    )
                } catch (e: Exception) {
                    Log.d("ServerDelayTest", e.toString())
                }
            }
        }.start()
        repeat(10) { test("Fetch", Network.PostType.TIME) }
        repeat(2) { test("My", Network.PostType.MY) }
        repeat(2) { test("Trending", Network.PostType.TRENDING) }
        repeat(2) { test("Favoured", Network.PostType.FAVOURED) }
    }
}