package personal.wuqing.anonymous

import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun send_email() = runBlocking {
        println(Network.requestLoginCode("wuqing157@sjtu.edu.cn"))
    }
}
