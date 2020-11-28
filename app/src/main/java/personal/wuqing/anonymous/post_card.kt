package personal.wuqing.anonymous

import android.content.*
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.text.style.TextAppearanceSpan
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import personal.wuqing.anonymous.databinding.PostCardBinding
import personal.wuqing.anonymous.databinding.PostFilterBinding
import personal.wuqing.anonymous.databinding.RecycleBottomBinding
import java.io.Serializable
import kotlin.time.ExperimentalTime

@ExperimentalTime
data class Post constructor(
    var showInDetail: Boolean,
    val id: String,
    val update: String,
    val post: String,
    val title: String,
    val content: String,
    var like: Boolean,
    var likeCount: Int,
    val replyCount: Int,
    var favor: Boolean,
    val readCount: Int,
    val colorG: ColorG,
    val nameG: NameG,
) : Serializable {
    constructor(json: JSONObject, showInDetail: Boolean) : this(
        showInDetail = showInDetail,
        id = json.getString("ThreadID"),
        update = json.getString("LastUpdateTime").untilNow().display(),
        post = json.getString("PostTime").untilNow().display(),
        title = json.getString("Title"),
        content = json.getString("Summary"),
        like = json.getInt("WhetherLike") == 1,
        likeCount = json.getInt("Like"),
        replyCount = json.getInt("Comment"),
        favor = json.has("WhetherFavour") && json.getInt("WhetherFavour") == 1,
        readCount = json.getInt("Read"),
        colorG = ColorG(json.getLong("RandomSeed")),
        nameG = NameG(json.getString("AnonymousType"), json.getLong("RandomSeed")),
    )

    val avatar = colorG[0]
    fun avatarC() = nameG[0].split(" ").last()[0].toString()

    fun id() = if (showInDetail) nameG[0] else "#$id"
    fun titleWithLink() =
        if (showInDetail) SpannableString(title).apply { links() } else title

    fun contentWithLink() =
        if (showInDetail) SpannableString(content).apply { links() } else content

    fun likeCount() = likeCount.toString()
    fun replyCount() = replyCount.toString()
    fun readCount() = readCount.toString()
    fun contentMaxLines() = if (showInDetail) Int.MAX_VALUE else 5

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

    fun likeIcon(context: Context) = ContextCompat.getDrawable(
        context, if (like) R.drawable.ic_thumb_up else R.drawable.ic_thumb_up_outlined
    )

    fun favorIcon(context: Context) = ContextCompat.getDrawable(
        context, if (favor) R.drawable.ic_favorite else R.drawable.ic_favorite_border
    )

    @ExperimentalUnsignedTypes
    fun likeIconTint(context: Context) = iconTint(context, like)

    @ExperimentalUnsignedTypes
    fun favorIconTint(context: Context) = iconTint(context, favor)

    @ExperimentalUnsignedTypes
    fun replyIconTint(context: Context) = iconTint(context, false)

    @ExperimentalUnsignedTypes
    fun readIconTint(context: Context) = iconTint(context, false)

    @ExperimentalUnsignedTypes
    fun menuIconTint(context: Context) = iconTint(context, false)

    enum class Category(val id: Int) {
        ALL(0),
        SPORT(1),
        MUSIC(2),
        SCIENCE(3),
        IT(4),
        ENTERTAINMENT(5),
        EMOTION(6),
        SOCIAL(7),
        OTHERS(8)
    }
}

@ExperimentalTime
class PostAdapter(
    private val filterInit: PostFilterBinding.() -> Unit,
    private val postInit: PostCardBinding.() -> Unit,
    private val bottomInit: RecycleBottomBinding.() -> Unit,
) : ListAdapter<Post, PostAdapter.ViewHolder>(PostDiffCallback) {
    companion object {
        const val FILTER = 1
        const val POST = 0
        const val BOTTOM = 2
    }

    sealed class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        class PostCard(
            private val binding: PostCardBinding,
            private val init: PostCardBinding.() -> Unit
        ) : ViewHolder(binding.root) {
            fun bind(item: Post, position: Int) {
                binding.apply {
                    post = item
                    root.tag = position
                    init()
                    executePendingBindings()
                }
            }
        }

        class Filter(
            binding: PostFilterBinding,
            init: PostFilterBinding.() -> Unit
        ) : ViewHolder(binding.root) {
            init {
                binding.init()
            }
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
            POST -> {
                val binding = PostCardBinding.inflate(inflater, parent, false)
                ViewHolder.PostCard(binding, postInit)
            }
            FILTER -> {
                val binding = PostFilterBinding.inflate(inflater, parent, false)
                ViewHolder.Filter(binding, filterInit)
            }
            BOTTOM -> {
                val binding = RecycleBottomBinding.inflate(inflater, parent, false)
                ViewHolder.Bottom(binding, bottomInit)
            }
            else -> error("")
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        if (holder is ViewHolder.PostCard) holder.bind(getItem(position), position)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (holder is ViewHolder.PostCard && payloads.singleOrNull() is Post)
            holder.bind(payloads.single() as Post, position)
        else super.onBindViewHolder(holder, position, payloads)
    }

    override fun getItemViewType(position: Int): Int {
        return when (position) {
            0 -> FILTER
            currentList.size - 1 -> BOTTOM
            else -> POST
        }
    }
}

@ExperimentalTime
object PostDiffCallback : DiffUtil.ItemCallback<Post>() {
    override fun areItemsTheSame(oldItem: Post, newItem: Post): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: Post, newItem: Post): Boolean {
        return oldItem == newItem
    }
}

@ExperimentalTime
@BindingAdapter("textMagic")
fun CardView.textMagic(post: Post) {
    val binding = DataBindingUtil.getBinding<PostCardBinding>(this)!!
    val context = context
    val showMenu = {
        val spannable = SpannableString("${post.title}\n${post.content}").apply {
            setSpan(
                TextAppearanceSpan(context, R.attr.textAppearanceSubtitle1),
                0, post.title.length,
                Spanned.SPAN_INCLUSIVE_INCLUSIVE
            )
            setSpan(
                StyleSpan(Typeface.BOLD),
                0, post.title.length,
                Spanned.SPAN_INCLUSIVE_INCLUSIVE
            )
        }
        val links = spannable.links()
        val displayLinks =
            if (post.showInDetail) arrayOf()
            else links.map { (it, _) -> "跳转到 $it" }.toTypedArray()
        val items = arrayOf(
            if (post.favor) "取消收藏" else "收藏",
            "复制标题", "复制内容", "自由复制",
            *displayLinks
        )
        MaterialAlertDialogBuilder(context).apply {
            setItems(items) { _: DialogInterface, i: Int ->
                when (i) {
                    0 -> when (context) {
                        is MainActivity -> context.model.favor(binding)
                        is PostDetailActivity -> context.model.favor(binding)
                    }
                    1 -> copy(context, post.title)
                    2 -> copy(context, post.content)
                    3 -> showSelectDialog(context, spannable)
                    else -> context.startActivity(Intent(Intent.ACTION_VIEW, links[i - 4].second))
                }
            }
            show()
        }
        true
    }
    binding.menu.setOnClickListener { showMenu() }
    setOnLongClickListener { showMenu() }
    if (post.showInDetail) for (view in listOf(binding.title, binding.content)) view.apply {
        movementMethod = MagicClickableMovementMethod
        isClickable = false
        isLongClickable = false
    }
}

@ExperimentalTime
class PostListViewModel : ViewModel() {
    val list = MutableLiveData(listOf<Post>())
    val info = MutableLiveData("")
    val refresh = MutableLiveData(false)
    var bottom = MutableLiveData(BottomStatus.REFRESHING)

    enum class Category(val category: Post.Category, val type: Network.PostType) {
        ALL(Post.Category.ALL, Network.PostType.TIME),
        HOT(Post.Category.ALL, Network.PostType.TRENDING),
        SPORTS(Post.Category.SPORT, Network.PostType.TIME),
        MUSIC(Post.Category.MUSIC, Network.PostType.TIME),
        SCIENCE(Post.Category.SCIENCE, Network.PostType.TIME),
        IT(Post.Category.IT, Network.PostType.TIME),
        ENTERTAIN(Post.Category.ENTERTAINMENT, Network.PostType.TIME),
        EMOTION(Post.Category.EMOTION, Network.PostType.TIME),
        SOCIAL(Post.Category.SOCIAL, Network.PostType.TIME),
        MY(Post.Category.ALL, Network.PostType.MY),
        REPLY(Post.Category.ALL, Network.PostType.MY),
        FAVOUR(Post.Category.ALL, Network.PostType.FAVOURED)
    }

    var category = Category.ALL
    val search = MutableLiveData<String>(null)
    var refreshingJob: Job? = null
    private var last = "NULL"

    fun refresh(context: Context) {
        refreshingJob?.cancel(CancellationException())
        refreshingJob = viewModelScope.launch {
            refresh.value = true
            delay(300)
            try {
                val (last, newList) =
                    if (search.value.isNullOrBlank())
                        Network.fetchPost(category.type, category.category)
                    else
                        Network.search(search.value!!)
                this@PostListViewModel.last = last
                list.value = newList
                delay(100)
                bottom.value =
                    if (list.value.isNullOrEmpty()) BottomStatus.NO_MORE else BottomStatus.IDLE
            } catch (e: Network.NotLoggedInException) {
                context.needLogin()
            } catch (e: CancellationException) {
                refresh.value = false
            } catch (e: Exception) {
                Log.d("network_error", e.toString())
                info.value = "网络错误"
                bottom.value = BottomStatus.NETWORK_ERROR
            } finally {
                refresh.value = false
            }
        }
    }

    fun more(context: Context) {
        refreshingJob?.cancel(CancellationException())
        refreshingJob = viewModelScope.launch {
            try {
                bottom.value = BottomStatus.REFRESHING
                delay(300)
                val (last, new) =
                    if (search.value.isNullOrBlank())
                        Network.fetchPost(category.type, category.category, last)
                    else
                        Network.search(search.value!!, last)
                this@PostListViewModel.last = last
                list.value = list.value!! + new
                delay(100)
                bottom.value = if (new.isEmpty()) BottomStatus.NO_MORE else BottomStatus.IDLE
            } catch (e: Network.NotLoggedInException) {
                context.needLogin()
            } catch (e: CancellationException) {
                bottom.value = BottomStatus.IDLE
            } catch (e: Exception) {
                bottom.value = BottomStatus.NETWORK_ERROR
            } finally {
                if (bottom.value == BottomStatus.REFRESHING)
                    bottom.value = BottomStatus.NETWORK_ERROR
            }
        }
    }

    fun like(binding: PostCardBinding) = viewModelScope.launch {
        try {
            binding.post?.apply {
                if (like) {
                    Network.unlikePost(id)
                    like = false
                    likeCount--
                } else {
                    Network.likePost(id)
                    like = true
                    likeCount++
                }
            }
            binding.invalidateAll()
        } catch (e: Network.NotLoggedInException) {
            binding.root.context.needLogin()
        } catch (e: Exception) {
            info.value = "网络错误"
        }
    }

    fun favor(binding: PostCardBinding) = viewModelScope.launch {
        try {
            binding.post?.apply {
                if (favor) Network.deFavorPost(id) else Network.favorPost(id)
                favor = !favor
                info.value = if (favor) "收藏成功" else "取消收藏成功"
            }
            binding.invalidateAll()
        } catch (e: Network.NotLoggedInException) {
            binding.root.context.needLogin()
        } catch (e: Exception) {
            info.value = "网络错误"
        }
    }
}

