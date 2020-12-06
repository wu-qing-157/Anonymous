package personal.wuqing.anonymous

import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.random.Random
import kotlin.time.ExperimentalTime

object Network {
    private const val IP = "172.81.215.104"
    private const val PORT = 8080
    var token = ""
    private fun connect(data: JSONObject) = Socket().use {
        it.soTimeout = 20 * 1000
        it.connect(InetSocketAddress(IP, PORT))
        it.getOutputStream().write(data.toString().toByteArray())
        JSONObject(it.getInputStream().bufferedReader().readLine().run {
            if (endsWith('}')) this else "$this}"
        })
    }

    object NotLoggedInException : Exception()
    class BannedException(
        val release: String,
        val thread: String,
        val content: String,
        val reason: String,
        val isReply: Boolean,
    ) : Exception() {
        fun showLogout(context: Context) {
            MaterialAlertDialogBuilder(context).apply {
                setTitle("很抱歉，你已被封禁")
                setMessage(
                    """
                    |由于${if (isReply) "你在 #$thread 下的回复" else "你的发帖 #$thread"} $reason ，已被我们屏蔽。结合你之前在无可奉告的封禁记录，你的账号将被暂时封禁至 $release。
                    |
                    |违规${if (isReply) "回复" else "发帖"}内容为：
                    |
                    |$content
                    |
                    |请与我们一起维护无可奉告社区环境。
                    |谢谢！
                """.trimMargin()
                )
                setCancelable(false)
                setPositiveButton("知道了") { _, _ ->
                    context.clearToken()
                    context.needLogin()
                }
                show()
            }
        }
    }

    private fun <T> getData(
        op: String, checkLogin: Boolean = true,
        p1: String = "0", p2: String = "0", p3: String = "0",
        p4: String = "0", p5: String = "0", p6: String = "0",
        done: JSONObject.() -> T
    ): T {
        val json = JSONObject(
            mapOf(
                "op_code" to op,
                "pa_1" to p1,
                "pa_2" to p2,
                "pa_3" to p3,
                "pa_4" to p4,
                "pa_5" to p5,
                "pa_6" to p6,
                "Token" to token
            )
        )
        val result = connect(json)
        return when {
            checkLogin && result.optString("login_flag", "") == "-1" ->
                throw BannedException(
                    release = result.getString("ReleaseTime"),
                    thread = result.getString("Ban_ThreadID"),
                    content = result.getString("Ban_Content"),
                    reason = result.getString("Ban_Reason"),
                    isReply = result.getString("Ban_Style") == "1"
                )
            checkLogin && result.optString("login_flag", "1") != "1" ->
                throw NotLoggedInException
            else -> done(result)
        }
    }

    suspend fun requestLoginCode(email: String) = withContext(Dispatchers.IO) {
        getData(op = "0", checkLogin = false, p1 = email) {
            getInt("VarifiedEmailAddress") == 1
        }
    }

    suspend fun login(email: String, code: String, device: String) = withContext(Dispatchers.IO) {
        getData(op = "f", checkLogin = false, p1 = email, p2 = code, p3 = device) {
            (getInt("login_flag") == 0).also {
                if (it) token = getString("Token")
            }
        }
    }

    suspend fun verifyToken() = withContext(Dispatchers.IO) {
        getData(op = "-1", checkLogin = false) {
            getString("login_flag") == "1"
        }
    }

    suspend fun likePost(id: String) = withContext(Dispatchers.IO) {
        getData(op = "8_3", p1 = id) { true }
    }

    suspend fun cancelLikePost(id: String) = withContext(Dispatchers.IO) {
        getData(op = "8_4", p1 = id) { true }
    }

    suspend fun dislikePost(id: String) = withContext(Dispatchers.IO) {
        getData(op = "9", p1 = id) { true }
    }

    suspend fun cancelDislikePost(id: String) = withContext(Dispatchers.IO) {
        getData(op = "9_2", p1 = id) { true }
    }

    suspend fun favorPost(id: String) = withContext(Dispatchers.IO) {
        getData(op = "5", p1 = id) { true }
    }

    suspend fun deFavorPost(id: String) = withContext(Dispatchers.IO) {
        getData(op = "5_2", p1 = id) { true }
    }

    enum class PostType(val op: String) {
        TIME("1"), FAVOURED("6"), MY("7"), TRENDING("d"), MESSAGE("a"),
    }

    @ExperimentalTime
    suspend fun fetchPost(type: PostType, category: Post.Category, last: String = "NULL") =
        withContext(Dispatchers.IO) {
            getData(op = type.op, p1 = last, p2 = category.id.toString()) {
                var lastSeen = "NULL"
                for (key in keys()) if (key.startsWith("LastSeen")) lastSeen = getString(key)
                lastSeen to (optJSONArray("thread_list") ?: getJSONArray("message_list")).let {
                    (0 until it.length()).map { i -> Post(it.getJSONObject(i), false) }
                }
            }
        }

    @ExperimentalTime
    suspend fun search(keyword: String, last: String = "NULL") = withContext(Dispatchers.IO) {
        getData(op = "b", p1 = keyword, p2 = last) {
            var lastSeen = "NULL"
            for (key in keys()) if (key.startsWith("LastSeen")) lastSeen = getString(key)
            lastSeen to getJSONArray("thread_list").let {
                (0 until it.length()).map { i -> Post(it.getJSONObject(i), false) }
            }
        }
    }

    enum class ReplySort(val op: String) {
        EARLIEST("0"), NEWEST("1"), HOST("2"), HOT("3"),
    }

    @ExperimentalTime
    suspend fun fetchReply(postId: String, order: ReplySort, last: String = "NULL") =
        withContext(Dispatchers.IO) {
            getData(op = "2", p1 = postId, p2 = last, p3 = order.op) {
                val post = Post(getJSONObject("this_thread"), true)
                var newLast = "NULL"
                for (key in keys()) if (key.startsWith("LastSeen")) newLast = getString(key)
                newLast to post to getJSONArray("floor_list").let {
                    (0 until it.length()).map { i ->
                        Reply(it.getJSONObject(i), post.nameG, post.colorG)
                    }
                }
            }
        }

    suspend fun likeReply(post: String, reply: String) = withContext(Dispatchers.IO) {
        getData(op = "8", p1 = post, p4 = reply) { true }
    }

    suspend fun cancelLikeReply(post: String, reply: String) = withContext(Dispatchers.IO) {
        getData(op = "8_2", p1 = post, p4 = reply) { true }
    }

    suspend fun dislikeReply(post: String, reply: String) = withContext(Dispatchers.IO) {
        getData(op = "8_5", p1 = post, p4 = reply) { true }
    }

    suspend fun cancelDislikeReply(post: String, reply: String) = withContext(Dispatchers.IO) {
        getData(op = "8_6", p1 = post, p4 = reply) { true }
    }

    suspend fun reply(post: String, reply: String, content: String) = withContext(Dispatchers.IO) {
        getData(op = if (reply == "0") "4" else "4_2", p1 = post, p3 = content, p4 = reply) { true }
    }

    suspend fun post(
        title: String,
        category: Post.Category,
        tag: Post.Tag?,
        content: String,
        anonymousType: NameTheme,
        random: Boolean
    ) = withContext(Dispatchers.IO) {
        getData(
            op = "3",
            p1 = title,
            p2 = category.id.toString(),
            p3 = content,
            p4 = anonymousType.id,
            p5 = (if (random) Random.nextInt(1000000) else 0).toString(),
            p6 = tag?.backend ?: "NULL",
        ) { true }
    }

    suspend fun report(id: String) = withContext(Dispatchers.IO) {
        getData(op = "e", p1 = id) { true }
    }

    suspend fun reportReply(post: String, reply: String) = withContext(Dispatchers.IO) {
        getData(op = "h", p1 = post, p2 = reply) { true }
    }

    suspend fun tag(id: String, tag: Post.Tag) = withContext(Dispatchers.IO) {
        getData(op = "i", p1 = id, p4 = tag.backend) { true }
    }

    suspend fun cancelTag(id: String) = withContext(Dispatchers.IO) {
        getData(op = "i_2") { true }
    }
}
