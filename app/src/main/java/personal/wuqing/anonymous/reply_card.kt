package personal.wuqing.anonymous

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.res.ColorStateList
import android.text.SpannableString
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.databinding.BindingAdapter
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import personal.wuqing.anonymous.databinding.PostCardBinding
import personal.wuqing.anonymous.databinding.RecycleBottomBinding
import personal.wuqing.anonymous.databinding.ReplyCardBinding
import personal.wuqing.anonymous.databinding.ReplySortBinding
import java.io.Serializable
import kotlin.time.ExperimentalTime

sealed class ReplyListElem

object ReplyListPost : ReplyListElem()
object ReplyListOrder : ReplyListElem()
object ReplyListBottom : ReplyListElem()

data class Reply constructor(
    val id: String,
    val update: String,
    val name: String,
    val avatar: Int,
    val content: String,
    var like: Like,
    var likeCount: Int,
    val toName: String,
    val toFloor: Int
) : ReplyListElem(), Serializable {
    @ExperimentalTime
    constructor(json: JSONObject, nameG: NameG, colorG: ColorG) : this(
        id = json.getString("FloorID"),
        update = json.getString("RTime").display(),
        name = nameG[json.getString("Speakername").toInt()],
        avatar = colorG[json.getString("Speakername").toInt()],
        content = json.getString("Context"),
        like = when (json.getInt("WhetherLike")) {
            -1 -> Like.DISLIKE
            0 -> Like.NORMAL
            1 -> Like.LIKE
            else -> error("")
        },
        likeCount = json.getInt("Like"),
        toName = nameG[json.getString("Replytoname").toInt()],
        toFloor = json.getInt("Replytofloor"),
    )

    fun avatarC() = name.split(" ").last()[0].toString()
    fun id() = "#$id"
    fun likeCount() = likeCount.toString()
    fun showTo() = toFloor != 0
    fun showReply() = if (showTo()) "回复" else ""
    fun toName() = if (showTo()) toName else ""
    fun toFloor() = if (showTo()) "#$toFloor" else ""
    fun likeIcon(context: Context) = ContextCompat.getDrawable(
        context,
        when (like) {
            Like.LIKE, Like.LIKE_WAIT -> R.drawable.ic_thumb_up_alt
            Like.NORMAL -> R.drawable.ic_thumb_up_alt_outlined
            Like.DISLIKE, Like.DISLIKE_WAIT -> R.drawable.ic_thumb_down_alt
        }
    )

    fun contentWithLink(context: Context) =
        SpannableString(content).apply { links(context as Activity) }

    @ExperimentalUnsignedTypes
    private fun iconTint(context: Context, boolean: Boolean) = ColorStateList.valueOf(
        if (boolean) TypedValue().run {
            context.theme.resolveAttribute(R.attr.colorPrimary, this, true)
            data
        } else TypedValue().run {
            context.theme.resolveAttribute(android.R.attr.textColorSecondary, this, true)
            data.toString(16).padStart(3, '0').toCharArray()
                .joinToString("", prefix = "ff") { "$it$it" }
                .toUInt(16).toInt()
        }
    )

    @ExperimentalUnsignedTypes
    fun likeIconTint(context: Context) = iconTint(
        context, when (like) {
            Like.LIKE, Like.DISLIKE -> true
            else -> false
        }
    )
}

class ReplyAdapter(
    private val replyInit: ReplyCardBinding.() -> Unit,
    private val sortInit: ReplySortBinding.() -> Unit,
    private val postInit: PostCardBinding.() -> Unit,
    private val bottomInit: RecycleBottomBinding.() -> Unit,
) : ListAdapter<ReplyListElem, ReplyAdapter.ViewHolder>(ReplyDiffCallback) {
    companion object {
        const val POST = 1
        const val REPLY = 0
        const val BOTTOM = 2
        const val SORT = 3
    }

    sealed class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        class ReplyCard(
            private val binding: ReplyCardBinding,
            private val init: ReplyCardBinding.() -> Unit
        ) : ViewHolder(binding.root) {
            fun bind(item: Reply) = binding.apply {
                reply = item
                init()
                executePendingBindings()
            }
        }

        class Sort(
            binding: ReplySortBinding,
            init: ReplySortBinding.() -> Unit
        ) : ViewHolder(binding.root) {
            init {
                binding.init()
            }
        }

        class PostCard(
            private val binding: PostCardBinding,
            private val init: PostCardBinding.() -> Unit
        ) : ViewHolder(binding.root) {
            fun bind() = binding.init()
        }

        class Bottom(
            binding: RecycleBottomBinding,
            init: RecycleBottomBinding.() -> Unit
        ) : ViewHolder(binding.root) {
            init {
                binding.init()
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            REPLY -> {
                val binding = ReplyCardBinding.inflate(inflater, parent, false)
                ViewHolder.ReplyCard(binding, replyInit)
            }
            POST -> {
                val binding = PostCardBinding.inflate(inflater, parent, false)
                ViewHolder.PostCard(binding, postInit)
            }
            BOTTOM -> {
                val binding = RecycleBottomBinding.inflate(inflater, parent, false)
                ViewHolder.Bottom(binding, bottomInit)
            }
            SORT -> {
                val binding = ReplySortBinding.inflate(inflater, parent, false)
                ViewHolder.Sort(binding, sortInit)
            }
            else -> error("")
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        when (holder) {
            is ViewHolder.ReplyCard -> holder.bind(getItem(position) as Reply)
            is ViewHolder.PostCard -> holder.bind()
            is ViewHolder.Bottom -> Unit
            is ViewHolder.Sort -> Unit
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (holder is ViewHolder.PostCard && payloads.singleOrNull() is Post) holder.bind()
        else super.onBindViewHolder(holder, position, payloads)
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            ReplyListPost -> POST
            ReplyListOrder -> SORT
            ReplyListBottom -> BOTTOM
            is Reply -> REPLY
        }
    }
}

object ReplyDiffCallback : DiffUtil.ItemCallback<ReplyListElem>() {
    override fun areItemsTheSame(oldItem: ReplyListElem, newItem: ReplyListElem): Boolean {
        return (oldItem is Reply && newItem is Reply && oldItem.id == newItem.id) || oldItem == newItem
    }

    override fun areContentsTheSame(oldItem: ReplyListElem, newItem: ReplyListElem): Boolean {
        return oldItem == newItem
    }
}

@BindingAdapter("magic")
fun CardView.magic(reply: Reply) {
    val binding = DataBindingUtil.getBinding<ReplyCardBinding>(this)!!
    val context = context
    val showMenu = {
        val spannable = SpannableString(reply.content).apply { links(context as Activity) }
        val items = arrayOf(
            "回复", "复制内容", "自由复制", "踩", "举报"
        )
        MaterialAlertDialogBuilder(context).apply {
            setItems(items) { _: DialogInterface, i: Int ->
                when (i) {
                    0 -> binding.root.performClick()
                    1 -> copy(context, reply.content)
                    2 -> showSelectDialog(context, spannable)
                    3 -> (context as? PostDetailActivity)?.apply { model.dislike(binding) }
                    4 -> (context as? PostDetailActivity)?.apply {
                        MaterialAlertDialogBuilder(this).apply {
                            setTitle("举报 #${model.postId} / #${reply.id}")
                            setMessage("确定要举报吗？\n楼层被举报数次后将被屏蔽，我们一起维护无可奉告论坛环境。")
                            setPositiveButton("! 确认举报 !") { _, _ -> model.report(context, reply) }
                            setNegativeButton("> 手滑了 <", null)
                            setCancelable(true)
                            show()
                        }
                    }
                }
            }
            show()
        }
        true
    }
    setOnLongClickListener { showMenu() }
    binding.content.apply {
        movementMethod = MagicClickableMovementMethod
        isClickable = false
        isLongClickable = false
    }
    binding.content.requestLayout()
    binding.content.invalidate()
}

class ReplyListViewModel : ViewModel() {
    val post = MutableLiveData<Post>()
    val list = MutableLiveData(listOf<Reply>())
    val info = MutableLiveData("")
    val refresh = MutableLiveData(false)
    val bottom = MutableLiveData(BottomStatus.REFRESHING)
    val sending = MutableLiveData(false)
    val success = MutableLiveData(false)
    var sort = Network.ReplySort.EARLIEST
    val favor = MutableLiveData(false)
    var postId = ""
    private var last = "NULL"

    @ExperimentalTime
    fun refresh(context: Context) = viewModelScope.launch {
        refresh.value = true
        delay(300)
        try {
            val (pair, newList) = Network.fetchReply(postId, sort)
            val (last, newPost) = pair
            this@ReplyListViewModel.last = last
            post.value = newPost
            list.value = newList
            delay(100)
            bottom.value =
                if (list.value.isNullOrEmpty()) BottomStatus.NO_MORE else BottomStatus.IDLE
        } catch (e: Network.NotLoggedInException) {
            context.needLogin()
        } catch (e: Network.BannedException) {
            e.showLogout(context)
        } catch (e: CancellationException) {
            refresh.value = false
        } catch (e: Exception) {
            info.value = "网络错误"
            bottom.value = BottomStatus.NETWORK_ERROR
        } finally {
            refresh.value = false
        }
    }

    @ExperimentalTime
    fun more(context: Context) = viewModelScope.launch {
        bottom.value = BottomStatus.REFRESHING
        try {
            delay(300)
            val (pair, newList) = Network.fetchReply(postId, sort, last)
            val (last, newPost) = pair
            this@ReplyListViewModel.last = last
            post.value = newPost
            list.value = list.value!! + newList
            delay(100)
            bottom.value = if (newList.isEmpty()) BottomStatus.NO_MORE else BottomStatus.IDLE
        } catch (e: Network.NotLoggedInException) {
            context.needLogin()
        } catch (e: Network.BannedException) {
            e.showLogout(context)
        } catch (e: CancellationException) {
            bottom.value = BottomStatus.IDLE
        } catch (e: Exception) {
            info.value = "网络错误"
            bottom.value = BottomStatus.NETWORK_ERROR
        }
    }

    fun like(binding: ReplyCardBinding) = viewModelScope.launch {
        val old = binding.reply?.like
        try {
            binding.reply?.apply {
                when (like) {
                    Like.LIKE_WAIT, Like.DISLIKE_WAIT -> info.value = "手速太快啦，请稍后再试"
                    Like.LIKE -> {
                        like = Like.LIKE_WAIT
                        binding.invalidateAll()
                        Network.cancelLikeReply(postId, id)
                        like = Like.NORMAL
                        likeCount--
                    }
                    Like.NORMAL -> {
                        like = Like.LIKE_WAIT
                        binding.invalidateAll()
                        Network.likeReply(postId, id)
                        like = Like.LIKE
                        likeCount++
                    }
                    Like.DISLIKE -> {
                        like = Like.DISLIKE_WAIT
                        binding.invalidateAll()
                        Network.cancelDislikeReply(postId, id)
                        like = Like.NORMAL
                        likeCount++
                    }
                }
            }
        } catch (e: Network.NotLoggedInException) {
            binding.root.context.needLogin()
            binding.reply?.apply { like = old ?: Like.NORMAL }
        } catch (e: Network.BannedException) {
            e.showLogout(binding.root.context)
        } catch (e: Exception) {
            info.value = "网络错误"
            binding.reply?.apply { like = old ?: Like.NORMAL }
        } finally {
            binding.invalidateAll()
        }
    }

    fun dislike(binding: ReplyCardBinding) = viewModelScope.launch {
        val old = binding.reply?.like
        try {
            binding.reply?.apply {
                like = Like.DISLIKE_WAIT
                binding.invalidateAll()
                Network.dislikeReply(postId, id)
                like = Like.DISLIKE
                likeCount--
            }
        } catch (e: Network.NotLoggedInException) {
            binding.root.context.needLogin()
            binding.reply?.apply { like = old ?: Like.NORMAL }
        } catch (e: Network.BannedException) {
            e.showLogout(binding.root.context)
        } catch (e: Exception) {
            info.value = "网络错误"
            binding.reply?.apply { like = old ?: Like.NORMAL }
        } finally {
            binding.invalidateAll()
        }
    }

    fun reply(editText: EditText) = viewModelScope.launch {
        if (editText.text.toString().isBlank()) {
            info.value = "发送失败：内容为空"
            return@launch
        } else if (editText.text.count { it == '\n' } >= 20) {
            info.value = "发送失败：换行不能超过20次哟"
            return@launch
        }
        sending.value = true
        delay(300)
        try {
            Network.reply(postId, editText.tag as String, editText.text.toString())
            success.value = true
        } catch (e: Network.NotLoggedInException) {
            editText.context.needLogin()
        } catch (e: Network.BannedException) {
            e.showLogout(editText.context)
        } catch (e: Exception) {
            info.value = "网络错误"
        } finally {
            sending.value = false
        }
    }

    fun like(binding: PostCardBinding) = viewModelScope.launch {
        val old = binding.post?.like
        try {
            binding.post?.apply {
                when (like) {
                    Like.LIKE_WAIT, Like.DISLIKE_WAIT -> info.value = "手速太快啦，请稍后再试"
                    Like.LIKE -> {
                        like = Like.LIKE_WAIT
                        binding.invalidateAll()
                        Network.cancelLikePost(id)
                        like = Like.NORMAL
                        likeCount--
                    }
                    Like.NORMAL -> {
                        like = Like.LIKE_WAIT
                        binding.invalidateAll()
                        Network.likePost(id)
                        like = Like.LIKE
                        likeCount++
                    }
                    Like.DISLIKE -> {
                        like = Like.DISLIKE_WAIT
                        binding.invalidateAll()
                        Network.cancelDislikePost(id)
                        like = Like.NORMAL
                        likeCount++
                    }
                }
            }
        } catch (e: Network.NotLoggedInException) {
            binding.root.context.needLogin()
            binding.post?.apply { like = old ?: Like.NORMAL }
        } catch (e: Network.BannedException) {
            e.showLogout(binding.root.context)
        } catch (e: Exception) {
            info.value = "网络错误"
            binding.post?.apply { like = old ?: Like.NORMAL }
        } finally {
            binding.invalidateAll()
        }
    }

    fun dislike(binding: PostCardBinding) = viewModelScope.launch {
        val old = binding.post?.like
        try {
            binding.post?.apply {
                like = Like.DISLIKE_WAIT
                binding.invalidateAll()
                Network.dislikePost(id)
                like = Like.DISLIKE
                likeCount--
            }
        } catch (e: Network.NotLoggedInException) {
            binding.root.context.needLogin()
            binding.post?.apply { like = old ?: Like.NORMAL }
        } catch (e: Network.BannedException) {
            e.showLogout(binding.root.context)
        } catch (e: Exception) {
            info.value = "网络错误"
            binding.post?.apply { like = old ?: Like.NORMAL }
        } finally {
            binding.invalidateAll()
        }
    }

    fun favor(context: Context) = viewModelScope.launch {
        try {
            when (favor.value) {
                null -> info.value = "手速太快啦，请稍后再试"
                true -> {
                    delay(500)
                    favor.value = null
                    Network.deFavorPost(postId)
                    info.value = "取消收藏成功"
                    favor.value = false
                }
                false -> {
                    delay(500)
                    favor.value = null
                    Network.favorPost(postId)
                    info.value = "收藏成功"
                    favor.value = true
                }
            }
        } catch (e: Network.NotLoggedInException) {
            context.needLogin()
        } catch (e: Network.BannedException) {
            e.showLogout(context)
        } catch (e: Exception) {
            info.value = "网络错误"
        }
    }

    fun report(context: Context) = viewModelScope.launch {
        try {
            Network.report(postId)
            info.value = "举报成功"
        } catch (e: Network.NotLoggedInException) {
            context.needLogin()
        } catch (e: Network.BannedException) {
            e.showLogout(context)
        } catch (e: Exception) {
            info.value = "网络错误"
        }
    }

    fun report(context: Context, reply: Reply) = viewModelScope.launch {
        try {
            Network.reportReply(postId, reply.id)
            info.value = "举报成功"
        } catch (e: Network.NotLoggedInException) {
            context.needLogin()
        } catch (e: Network.BannedException) {
            e.showLogout(context)
        } catch (e: Exception) {
            info.value = "网络错误"
        }
    }

    fun tag(context: Context, tag: Post.Tag) = viewModelScope.launch {
        try {
            Network.tag(postId, tag)
            info.value = "已建议标记为 ${tag.display}"
        } catch (e: Network.NotLoggedInException) {
            context.needLogin()
        } catch (e: Network.BannedException) {
            e.showLogout(context)
        } catch (e: Exception) {
            info.value = "网络错误"
        }
    }
}
