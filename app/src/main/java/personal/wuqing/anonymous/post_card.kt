package personal.wuqing.anonymous

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.databinding.BindingAdapter
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
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
    val id: String,
    val update: String,
    val post: String,
    val title: String,
    val content: String,
    var like: Boolean,
    var likeCount: Int,
    val replyCount: Int,
    var favoured: Boolean,
    val readCount: Int,
    val nameG: NameG,
) : Serializable {
    constructor(json: JSONObject) : this(
        id = json.getString("ThreadID"),
        update = json.getString("LastUpdateTime").untilNow().display(),
        post = json.getString("PostTime").untilNow().display(),
        title = json.getString("Title"),
        content = json.getString("Summary"),
        like = json.getInt("WhetherLike") == 1,
        likeCount = json.getInt("Like"),
        replyCount = json.getInt("Comment"),
        favoured = json.has("WhetherFavour") && json.getInt("WhetherFavour") == 1,
        readCount = json.getInt("Read"),
        nameG = NameG(json.getString("AnonymousType"), json.getLong("RandomSeed"))
    )

    fun id() = "#$id"
    fun likeCount() = likeCount.toString()
    fun replyCount() = replyCount.toString()
    fun readCount() = readCount.toString()

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
@BindingAdapter("postLike")
fun MaterialButton.postLike(item: Post) {
    iconTint = ColorStateList.valueOf(
        if (item.like)
            ContextCompat.getColor(context, R.color.design_default_color_primary)
        else
            0x777777 + (0xff shl 24)
    )
    icon = if (item.like) ContextCompat.getDrawable(context, R.drawable.ic_thumb_up)
    else ContextCompat.getDrawable(context, R.drawable.ic_thumb_up_outlined)
}

@ExperimentalTime
@BindingAdapter("postFavour")
fun MaterialButton.postFavour(item: Post) {
    iconTint = ColorStateList.valueOf(
        if (item.favoured)
            ContextCompat.getColor(context, R.color.design_default_color_primary)
        else
            0x777777 + (0xff shl 24)
    )
    icon = if (item.favoured) ContextCompat.getDrawable(context, R.drawable.ic_star_rate)
    else ContextCompat.getDrawable(context, R.drawable.ic_star_rate_outlined)
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

    fun refresh(context: Context) {
        refreshingJob?.cancel(CancellationException())
        viewModelScope.launch {
            refresh.value = true
            delay(300)
            try {
                list.value =
                    if (search.value.isNullOrBlank())
                        Network.fetchPost(category.type, category.category)
                    else
                        Network.search(search.value!!)
                delay(100)
                bottom.value =
                    if (list.value.isNullOrEmpty()) BottomStatus.NO_MORE else BottomStatus.IDLE
            } catch (e: Network.NotLoggedInException) {
                context.needLogin()
            } catch (e: Exception) {
                info.value = "网络错误"
                bottom.value = BottomStatus.NETWORK_ERROR
            } finally {
                refresh.value = false
            }
        }
    }

    fun more(context: Context) {
        refreshingJob?.cancel(CancellationException())
        viewModelScope.launch {
            try {
                bottom.value = BottomStatus.REFRESHING
                delay(300)
                val last = list.value?.lastOrNull()?.id ?: "NULL"
                val new = if (search.value.isNullOrBlank())
                    Network.fetchPost(category.type, category.category, last)
                else Network.search(search.value!!, last)
                list.value = list.value!! + new
                delay(100)
                bottom.value = if (new.isEmpty()) BottomStatus.NO_MORE else BottomStatus.IDLE
            } catch (e: Network.NotLoggedInException) {
                context.needLogin()
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

    fun favour(binding: PostCardBinding) = viewModelScope.launch {
        try {
            binding.post?.apply {
                if (favoured) Network.deFavourPost(id) else Network.favourPost(id)
                favoured = !favoured
            }
            binding.invalidateAll()
        } catch (e: Network.NotLoggedInException) {
            binding.root.context.needLogin()
        } catch (e: Exception) {
            info.value = "网络错误"
        }
    }
}

