package personal.wuqing.anonymous

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.method.ArrowKeyMovementMethod
import android.text.method.BaseMovementMethod
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import android.util.Log
import android.util.Patterns
import android.view.MotionEvent
import android.widget.TextView
import androidx.core.graphics.toColorInt
import androidx.core.text.getSpans
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textview.MaterialTextView
import java.io.Serializable
import java.text.SimpleDateFormat
import java.util.*
import kotlin.time.*

enum class BottomStatus {
    REFRESHING, NO_MORE, NETWORK_ERROR, IDLE
}

object MagicClickableMovementMethod : BaseMovementMethod() {
    override fun onTouchEvent(
        widget: TextView?, buffer: Spannable?, event: MotionEvent?
    ): Boolean {
        if (event!!.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_DOWN) {
            val tx = event.x.toInt() - widget!!.totalPaddingLeft + widget.scrollX
            val ty = event.y.toInt() - widget.totalPaddingTop + widget.scrollY
            val line = widget.layout.getLineForVertical(ty)
            val offset = widget.layout.getOffsetForHorizontal(line, tx.toFloat())
            val spans = buffer!!.getSpans<ClickableSpan>(offset, offset)
            return spans.isNotEmpty().apply {
                if (this && event.action == MotionEvent.ACTION_UP)
                    spans.forEach { it.onClick(widget) }
            }
        }
        return super.onTouchEvent(widget, buffer, event)
    }
}

object MagicSelectableClickableMovementMethod : ArrowKeyMovementMethod() {
    override fun onTouchEvent(
        widget: TextView?, buffer: Spannable?, event: MotionEvent?
    ): Boolean {
        if (event!!.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_DOWN) {
            val tx = event.x.toInt() - widget!!.totalPaddingLeft + widget.scrollX
            val ty = event.y.toInt() - widget.totalPaddingTop + widget.scrollY
            val line = widget.layout.getLineForVertical(ty)
            val offset = widget.layout.getOffsetForHorizontal(line, tx.toFloat())
            val spans = buffer!!.getSpans<ClickableSpan>(offset, offset)
            return spans.isNotEmpty().apply {
                if (this && event.action == MotionEvent.ACTION_UP)
                    spans.forEach { it.onClick(widget) }
            }
        }
        return super.onTouchEvent(widget, buffer, event)
    }
}

@ExperimentalTime
fun copy(context: Context, s: String) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(
        ClipData.newPlainText("", s)
    )
    val view = when (context) {
        is MainActivity -> context.binding.swipeRefresh
        is PostDetailActivity -> context.binding.swipeRefresh
        else -> error("")
    }
    Snackbar.make(view, "已复制: $s", Snackbar.LENGTH_SHORT).apply {
        this.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
            .maxLines = 1
        if (context is PostDetailActivity) anchorView = context.binding.bottomBar
        show()
    }
}

fun showSelectDialog(context: Context, spannable: SpannableString) {
    MaterialAlertDialogBuilder(context).run {
        setMessage(spannable)
        setCancelable(true)
        create()
    }.apply {
        show()
        window?.apply {
            decorView.findViewById<MaterialTextView>(android.R.id.message)?.apply {
                setTextIsSelectable(true)
                movementMethod = MagicSelectableClickableMovementMethod
                textSize = 18F
            }
        }
    }
}

fun SpannableString.links(): List<Pair<String, Uri>> {
    val ret = mutableListOf<Pair<String, Uri>>()
    Regex(Patterns.WEB_URL.pattern()).findAll(this).forEach {
        Regex("[\u0000-\u007F].*[\u0000-\u007F]").find(it.value)?.apply {
            setSpan(
                URLSpan(value), it.range.first + range.first, it.range.first + range.last + 1,
                Spanned.SPAN_INCLUSIVE_INCLUSIVE
            )
            ret += value
                .replace(Regex("^https?://"), "")
                .replace(Regex("^www\\."), "")
                .run {
                    if (length >= 27) substring(0, 25) + '\u22EF' else this
                } to Uri.parse(value)
        }
    }
    return ret
}

@ExperimentalTime
fun Duration.display() = when {
    this < 1.hours -> "${inMinutes.toInt()}分钟前"
    this < 1.days -> "${inHours.toInt()}小时前"
    this < 30.days -> "${inDays.toInt()}天前"
    this < 365.days -> "${inDays.toInt() / 30}个月前"
    else -> "${inDays.toInt() / 365}年前"
}

@ExperimentalTime
fun String.untilNow() = Calendar.getInstance().let {
    it.time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(this)!!
    (System.currentTimeMillis() - it.timeInMillis).milliseconds
}

class RandomN(var seed: Long) {
    var a = seed
    var b = 19260817L
    fun next() = if (seed == 0L) {
        a += 1
        a
    } else {
        var t = a
        val s = b
        a = s
        t = t xor (t shl 23)
        t = t xor (t shr 17)
        t = t xor s xor (s shr 26)
        b = t
        (s + t) and Long.MAX_VALUE
    }
}

fun <T> MutableList<T>.shuffle(s: Long) {
    val random = RandomN(s)
    for (i in 1 until size) {
        val j = (random.next() % (i + 1)).toInt()
        val t = this[i]
        this[i] = this[j]
        this[j] = t
    }
}

enum class NameTheme(val id: String) {
    ALICE_AND_BOB("abc"),
    US_PRESIDENT("us_president"),
    TAROT("tarot"),
}

data class NameG(val theme: NameTheme, val seed: Long) : Serializable {
    private val list = (names[theme] ?: listOf("Unknown")).toMutableList().apply { shuffle(seed) }
    operator fun get(i: Int) =
        if (i >= list.size) "${list[i % list.size]}.${i / list.size + 1}"
        else list[i % list.size]

    constructor(theme: String, seed: Long) : this(
        when (theme) {
            NameTheme.ALICE_AND_BOB.id -> NameTheme.ALICE_AND_BOB
            NameTheme.US_PRESIDENT.id -> NameTheme.US_PRESIDENT
            NameTheme.TAROT.id -> NameTheme.TAROT
            else -> NameTheme.ALICE_AND_BOB
        }, seed
    )
}

data class ColorG(val seed: Long) : Serializable {
    private val list = colors.toMutableList().apply { shuffle(seed) }
    operator fun get(i: Int) = list[i % list.size]
}

val names = mapOf(
    NameTheme.ALICE_AND_BOB to listOf(
        "Alice",
        "Bob",
        "Carol",
        "Dave",
        "Eve",
        "Forest",
        "George",
        "Harry",
        "Issac",
        "Justin",
        "Kevin",
        "Laura",
        "Mallory",
        "Neal",
        "Oscar",
        "Pat",
        "Quentin",
        "Rose",
        "Steve",
        "Trent",
        "Utopia",
        "Victor",
        "Walter",
        "Xavier",
        "Young",
        "Zoe",
    ),
    NameTheme.US_PRESIDENT to listOf(
        "Washington",
        "J.Adams",
        "Jefferson",
        "Madison",
        "Monroe",
        "J.Q.Adams",
        "Jackson",
        "Buren",
        "W.H.Harrison",
        "J.Tyler",
        "Polk",
        "Z.Tylor",
        "Fillmore",
        "Pierce",
        "Buchanan",
        "Lincoln",
        "A.Johnson",
        "Grant",
        "Hayes",
        "Garfield",
        "Arthur",
        "Cleveland",
        "B.Harrison",
        "McKinley",
        "T.T.Roosevelt",
        "Taft",
        "Wilson",
        "Harding",
        "Coolidge",
        "Hoover",
        "F.D.Roosevelt",
        "Truman",
        "Eisenhower",
        "Kennedy",
        "L.B.Johnson",
        "Nixon",
        "Ford",
        "Carter",
        "Reagan",
        "G.H.W.Bush",
        "Clinton",
        "G.W.Bush",
        "Obama",
        "Trump",
    ),
    NameTheme.TAROT to listOf(
        "The Fool",
        "The Magician",
        "The High Priestess",
        "The Empress",
        "The Emperor",
        "The Hierophant",
        "The Lovers",
        "The Chariot",
        "Justice",
        "The Hermit",
        "Wheel of Fortune",
        "Strength",
        "The Hanged Man",
        "Death",
        "Temperance",
        "The Devil",
        "The Tower",
        "The Star",
        "The Moon",
        "The Sun",
        "Judgement",
        "The World",
    )
)

val colors = listOf("5ebd3e", "ffb900", "f78200", "e23838", "973999", "009cdf").map {
    "#aa$it".toColorInt()
}
