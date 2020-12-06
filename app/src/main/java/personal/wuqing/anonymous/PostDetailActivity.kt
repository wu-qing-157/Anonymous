package personal.wuqing.anonymous

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.transition.*
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.app.ShareCompat
import androidx.core.app.SharedElementCallback
import androidx.core.view.MenuCompat
import androidx.core.widget.addTextChangedListener
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.material.behavior.HideBottomViewOnScrollBehavior
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import personal.wuqing.anonymous.databinding.ActivityPostBinding
import kotlin.math.max
import kotlin.math.min
import kotlin.time.ExperimentalTime

class PostDetailActivity : AppCompatActivity() {
    lateinit var binding: ActivityPostBinding
    val model by viewModels<ReplyListViewModel>()

    companion object {
        val sortMap = mapOf(
            R.id.earliest to Network.ReplySort.EARLIEST,
            R.id.newest to Network.ReplySort.NEWEST,
            R.id.host to Network.ReplySort.HOST,
            R.id.hot to Network.ReplySort.HOT,
        )
    }

    @ExperimentalTime
    private fun showFilter(button: MaterialButton) {
        PopupMenu(this, button, Gravity.NO_GRAVITY, R.attr.popupMenuStyle, 0).apply {
            MenuCompat.setGroupDividerEnabled(menu, true)
            menuInflater.inflate(R.menu.reply_sort, menu)
            setOnMenuItemClickListener {
                button.text = it.title
                model.sort = sortMap[it.itemId] ?: error("")
                dismiss()
                model.refresh(this@PostDetailActivity)
                true
            }
            show()
        }
    }

    private fun jump(to: Int) = model.viewModelScope.launch {
        val target =
            if (to == 0) 0
            else (binding.recycle.adapter as ReplyAdapter).currentList.indexOfFirst {
                it is Reply && it.id.toInt() == to
            }
        if (target == -1) {
            Snackbar.make(
                binding.root, "暂不支持跳转至还未加载的楼层", Snackbar.LENGTH_SHORT
            ).apply {
                anchorView = binding.bottomBar
                show()
            }
            return@launch
        }
        with(binding.recycle.layoutManager as LinearLayoutManager) {
            val params =
                binding.bottomBar.layoutParams as CoordinatorLayout.LayoutParams
            val behavior = params.behavior as HideBottomViewOnScrollBehavior
            if (findFirstCompletelyVisibleItemPosition() >= target) {
                binding.appbar.setExpanded(true, true)
                behavior.slideUp(binding.bottomBar)
                delay(50)
                binding.recycle.smoothScrollToPosition(max(target - 1, 0))
            } else if (findLastCompletelyVisibleItemPosition() <= target) {
                binding.appbar.setExpanded(false, true)
                behavior.slideDown(binding.bottomBar)
                binding.recycle.smoothScrollToPosition(
                    min(target + 1, binding.recycle.adapter!!.itemCount)
                )
            }
        }
        while (true) {
            delay(100)
            with(binding.recycle.findViewHolderForLayoutPosition(target)?.itemView ?: continue) {
                delay(100)
                isPressed = true
                delay(100)
                isPressed = false
            }
            break
        }
    }

    private fun reply(to: String, name: String, show: Boolean = true) =
        model.viewModelScope.launch {
            binding.replyHint.hint = if (to.isBlank()) "回复原贴" else "回复 #$to ($name)"
            binding.reply.tag = to
            with(binding.bottomBar.layoutParams as CoordinatorLayout.LayoutParams) {
                with(behavior as HideBottomViewOnScrollBehavior) {
                    if (show) slideUp(binding.bottomBar) else slideDown(binding.bottomBar)
                }
            }
            delay(100)
            binding.reply.apply {
                if (show) requestFocus() else clearFocus()
            }
            repeat(2) { // IDK Y, but repeating it really works
                getSystemService(InputMethodManager::class.java).apply {
                    if (show) showSoftInput(binding.reply, 0)
                    else hideSoftInputFromWindow(binding.reply.windowToken, 0)
                }
            }
        }

    @ExperimentalTime
    override fun onCreate(savedInstanceState: Bundle?) {
        applyTheme()
        super.onCreate(savedInstanceState)
        val context = this
        loadToken()

        binding = DataBindingUtil.setContentView(this, R.layout.activity_post)
        val fromUrl = intent.data != null
        if (fromUrl) model.postId =
            intent.data?.host?.takeIf { it.matches(Regex("[0-9]{6}")) } ?: ""
        else (intent.getSerializableExtra("post") as? Post)?.copy(showInDetail = true)?.also {
            model.post.value = it
            model.postId = it.id
            postponeEnterTransition()
        }
        setSupportActionBar(binding.toolbar)
        supportActionBar!!.title =
            model.postId.takeIf { it != "" }?.let { "#$it" } ?: "404 - Not Found"
        binding.toolbar.setNavigationOnClickListener { finishAfterTransition() }

        val adapter = ReplyAdapter(
            replyInit = {
                root.setOnClickListener { reply(reply!!.id, reply!!.name) }
                if (reply!!.showTo()) jump.setOnClickListener { jump(reply!!.toFloor) }
                likeButton.setOnClickListener { model.like(this) }
            },
            sortInit = {
                sort.setOnClickListener { showFilter(sort) }
            },
            postInit = {
                post = model.post.value!!
                root.setOnClickListener { reply("", "") }
                likeButton.setOnClickListener { model.like(this) }
            },
            bottomInit = {
                loadMore.setOnClickListener { model.more(context) }
                noMore.setOnClickListener {
                    model.refresh(context)
                    binding.recycle.smoothScrollToPosition(0)
                }
                networkError.setOnClickListener { model.more(context) }
                model.bottom.observe(context) {
                    fun visibility(v: Boolean) = if (v) View.VISIBLE else View.GONE
                    binding.apply {
                        loadMore.visibility = visibility(it == BottomStatus.IDLE)
                        noMore.visibility = visibility(it == BottomStatus.NO_MORE)
                        networkError.visibility = visibility(it == BottomStatus.NETWORK_ERROR)
                        bottomLoading.visibility = visibility(it == BottomStatus.REFRESHING)
                    }
                }
            }
        )

        binding.recycle.apply {
            layoutManager = LinearLayoutManager(context)
            this.adapter = adapter
            with(itemAnimator as SimpleItemAnimator) {
                supportsChangeAnimations = false
            }
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    if (newState == RecyclerView.SCROLL_STATE_DRAGGING) binding.reply.clearFocus()
                    context.getSystemService(InputMethodManager::class.java)
                        .hideSoftInputFromWindow(binding.reply.windowToken, 0)
                    if (with(layoutManager as LinearLayoutManager) {
                            findLastVisibleItemPosition() >= itemCount - 2 &&
                                    findFirstCompletelyVisibleItemPosition() > 0
                        }) {
                        if (newState == RecyclerView.SCROLL_STATE_DRAGGING)
                            with(binding.bottomBar.layoutParams as CoordinatorLayout.LayoutParams) {
                                with(behavior as HideBottomViewOnScrollBehavior) {
                                    slideDown(binding.bottomBar)
                                }
                            }
                        if (newState == RecyclerView.SCROLL_STATE_IDLE &&
                            model.bottom.value == BottomStatus.IDLE
                        ) model.more(context)
                    }
                }
            })
            viewTreeObserver.addOnPreDrawListener {
                startPostponedEnterTransition()
                true
            }
        }
        binding.swipeRefresh.setOnRefreshListener { model.refresh(context) }
        binding.reply.addTextChangedListener {
            binding.replyHint.isCounterEnabled = !it.isNullOrBlank()
        }
        binding.replySubmit.setOnClickListener { model.reply(binding.reply) }

        model.post.observe(context) {
            if (adapter.currentList.size > 1) adapter.notifyItemChanged(0)
        }
        model.list.observe(context) {
            model.post.value?.run {
                adapter.submitList(
                    listOf(ReplyListPost, ReplyListOrder) + it + ReplyListBottom,
                    if (adapter.currentList.size > 1 || savedInstanceState?.getBoolean("recreate") == true) {
                        {}
                    } else {
                        { binding.recycle.scrollToPosition(0) }
                    }
                )
            } ?: kotlin.run { adapter.submitList(listOf(ReplyListBottom)) }
        }
        model.refresh.observe(context) { binding.swipeRefresh.isRefreshing = it }
        model.info.observe(context) {
            if (!it.isNullOrBlank()) Snackbar.make(binding.root, it, Snackbar.LENGTH_SHORT).apply {
                anchorView = binding.bottomBar
                show()
            }
        }
        model.sending.observe(context) {
            binding.replySubmit.isEnabled = !it
            binding.replyProgress.visibility = if (it) View.VISIBLE else View.GONE
        }
        model.success.observe(context) {
            if (it) {
                reply("", "", false)
                binding.reply.setText("")
            }
        }

        model.more(context)
        window.enterTransition = Slide(Gravity.END).apply {
            interpolator = DecelerateInterpolator()
            excludeTarget(android.R.id.statusBarBackground, true)
        }
        window.returnTransition = Slide(Gravity.END).apply {
            interpolator = AccelerateInterpolator()
            excludeTarget(android.R.id.statusBarBackground, true)
        }
        window.exitTransition = Slide(Gravity.START).apply {
            interpolator = AccelerateInterpolator()
        }
        window.reenterTransition = Slide(Gravity.START).apply {
            interpolator = DecelerateInterpolator()
        }
        setEnterSharedElementCallback(object : SharedElementCallback() {
            override fun onSharedElementsArrived(
                sharedElementNames: MutableList<String>?,
                sharedElements: MutableList<View>?,
                listener: OnSharedElementsReadyListener?
            ) {
                super.onSharedElementsArrived(sharedElementNames, sharedElements, listener)
                sharedElementList = sharedElementNames?.toList()
            }
        })
        window.sharedElementEnterTransition = TransitionSet().apply {
            addTransition(TransitionSet().apply {
                addTransition(ChangeBounds())
                addTransition(ChangeClipBounds())
                addTransition(ChangeTransform())
            })
        }
        if (savedInstanceState?.getBoolean("recreate") == true) flag = RecreateFlag.RECREATE
    }

    override fun onCreateOptionsMenu(menu: Menu?) = true.also {
        menuInflater.inflate(R.menu.post_detail_menu, menu!!)
        model.post.observe(this) { model.favor.value = model.post.value?.favor }
        model.favor.observe(this) {
            menu.findItem(R.id.favor).apply {
                isEnabled = it != null
                setIcon(if (it == true) R.drawable.ic_star else R.drawable.ic_star_border)
                title = if (it == true) "取消收藏" else "收藏"
            }
        }
        binding.recycle.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                menu.findItem(R.id.refresh).isVisible =
                    with(recyclerView.layoutManager as LinearLayoutManager) {
                        findFirstCompletelyVisibleItemPosition() > 0
                    }
            }
        })
    }

    @ExperimentalTime
    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.favor -> true.also { model.favor(this) }
        R.id.share -> true.also {
            MaterialAlertDialogBuilder(this).apply {
                setTitle("分享 #${model.postId}")
                setItems(arrayOf("发送链接", "复制外部分享链接", "复制内部跳转链接")) { _, i ->
                    when (i) {
                        0 -> ShareCompat.IntentBuilder.from(this@PostDetailActivity)
                            .setType("text/plain")
                            .setChooserTitle("分享 #${model.postId}")
                            .setText("http://wukefenggao.cn/viewThread/${model.postId}")
                            .startChooser()
                        1 -> copy(
                            this@PostDetailActivity,
                            "http://wukefenggao.cn/viewThread/${model.postId}"
                        )
                        2 -> copy(this@PostDetailActivity, "wkfg://${model.postId}")
                    }
                }
                show()
            }
        }
        R.id.refresh -> true.also {
            binding.recycle.smoothScrollToPosition(0)
            model.viewModelScope.launch {
                delay(500)
                model.refresh(this@PostDetailActivity)
            }
        }
        R.id.report -> true.also { showReport(model.postId) { model.report(this) } }
        R.id.tag -> true.also { showTag(model.postId) { model.tag(this, it) } }
        else -> false
    }

    var sharedElementList: List<String>? = null

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            sharedElementList?.let {
                outState.putStringArrayList("android:pendingExitSharedElements", ArrayList(it))
            }
        }
        outState.putBoolean("recreate", true)
    }

    override fun finishAfterTransition() {
        window.exitTransition = Slide(Gravity.END)
        setResult(RESULT_OK, Intent().apply {
            putExtra("post", model.post.value)
            putExtra("position", intent.getIntExtra("position", 0))
        })
        super.finishAfterTransition()
    }

    override fun onRestart() {
        super.onRestart()
        setEnterSharedElementCallback(null as SharedElementCallback?)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == LOGIN_RESULT) loadToken()
    }

    enum class RecreateFlag {
        RECREATE, CALLED, NOTHING
    }

    var flag = RecreateFlag.NOTHING
    override fun onStop() {
        if (flag == RecreateFlag.NOTHING && !isFinishing) {
            flag = RecreateFlag.CALLED
            recreate()
        }
        if (flag == RecreateFlag.NOTHING && !isFinishing) flag = RecreateFlag.NOTHING
        super.onStop()
    }
}
