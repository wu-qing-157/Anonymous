package personal.wuqing.anonymous

import android.content.Intent
import android.os.Bundle
import android.transition.Slide
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.widget.addTextChangedListener
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.material.behavior.HideBottomViewOnScrollBehavior
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import personal.wuqing.anonymous.databinding.ActivityPostBinding
import kotlin.time.ExperimentalTime

@ExperimentalTime
class PostDetailActivity : AppCompatActivity() {
    lateinit var binding: ActivityPostBinding
    val model by viewModels<ReplyListViewModel>()

    private fun <T> LiveData<T>.observe(f: (T) -> Unit) = observe(this@PostDetailActivity, f)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadToken()
        val fromURL = (intent.getSerializableExtra("post") as? Post)?.also {
            it.showInDetail = true
            model.post.value = it
            postponeEnterTransition()
        } == null
        binding = DataBindingUtil.setContentView(this, R.layout.activity_post)
        binding.reply.tag = "0"
        setSupportActionBar(binding.toolbar)
        val id =
            if (fromURL) intent.data?.host?.takeIf { it.matches(Regex("[0-9]{6}")) }
            else model.post.value!!.id
        supportActionBar!!.title = id?.let { "#$id" } ?: "404 - Not Found"
        binding.toolbar.setNavigationOnClickListener { finishAfterTransition() }
        val adapter = ReplyAdapter(
            replyInit = {
                root.setOnClickListener {
                    model.viewModelScope.launch {
                        binding.replyHint.hint = "回复 ${reply?.id()} (${reply?.name})"
                        binding.reply.tag = reply?.id
                        val params =
                            binding.bottomBar.layoutParams as CoordinatorLayout.LayoutParams
                        val behavior = params.behavior as HideBottomViewOnScrollBehavior
                        behavior.slideUp(binding.bottomBar)
                        binding.reply.requestFocus()
                        delay(100)
                        repeat(2) { // IDK Y, but repeating it really works
                            getSystemService(InputMethodManager::class.java).showSoftInput(
                                binding.reply, 0
                            )
                        }
                    }
                }
                if (reply!!.showTo()) jump.apply {
                    setOnClickListener {
                        model.viewModelScope.launch {
                            if ((binding.recycle.layoutManager as LinearLayoutManager).run {
                                    findFirstCompletelyVisibleItemPosition() > reply!!.toFloor
                                }) binding.appbar.setExpanded(true, true)
                            binding.recycle.smoothScrollToPosition(reply!!.toFloor)
                            while (true) {
                                val view = binding.recycle
                                    .findViewHolderForLayoutPosition(reply!!.toFloor)?.itemView
                                delay(100)
                                (view ?: continue).apply {
                                    isPressed = true
                                    delay(200)
                                    isPressed = false
                                }
                                break
                            }
                        }
                    }
                }
                likeButton.setOnClickListener { model.like(this) }
            },
            postInit = {
                post = model.post.value
                root.setOnClickListener {
                    binding.replyHint.hint = "回复原帖"
                    binding.reply.tag = "0"
                    val params = binding.bottomBar.layoutParams as CoordinatorLayout.LayoutParams
                    val behavior = params.behavior as HideBottomViewOnScrollBehavior
                    behavior.slideUp(binding.bottomBar)
                    binding.reply.requestFocus()
                    getSystemService(InputMethodManager::class.java).showSoftInput(binding.reply, 0)
                }
                likeButton.setOnClickListener { model.like(this) }
                favourButton.setOnClickListener { model.favor(this) }
                executePendingBindings()
            },
            bottomInit = {
                loadMore.setOnClickListener {
                    model.more(this@PostDetailActivity)
                }
                noMore.setOnClickListener {
                    model.refresh(this@PostDetailActivity)
                }
                networkError.setOnClickListener {
                    model.more(this@PostDetailActivity)
                }
                model.bottom.observe {
                    Log.e("bs", "bottom")
                    fun v(b: Boolean) = if (b) View.VISIBLE else View.INVISIBLE
                    binding.apply {
                        loadMore.visibility = v(it == BottomStatus.IDLE)
                        noMore.visibility = v(it == BottomStatus.NO_MORE)
                        networkError.visibility = v(it == BottomStatus.NETWORK_ERROR)
                        bottomLoading.visibility = v(it == BottomStatus.REFRESHING)
                    }
                }
            }
        )
        binding.apply {
            recycle.apply {
                layoutManager = LinearLayoutManager(context)
                this.adapter = adapter
                (itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
                addOnScrollListener(object : RecyclerView.OnScrollListener() {
                    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                        if (newState == RecyclerView.SCROLL_STATE_IDLE &&
                            model.bottom.value == BottomStatus.IDLE &&
                            (layoutManager as LinearLayoutManager).run {
                                findLastVisibleItemPosition() >= itemCount - 2
                            }
                        ) model.more(context)
                        if (newState == RecyclerView.SCROLL_STATE_DRAGGING) reply.clearFocus()
                        getSystemService(InputMethodManager::class.java)
                            .hideSoftInputFromWindow(reply.windowToken, 0)
                    }
                })
                viewTreeObserver.addOnPreDrawListener {
                    startPostponedEnterTransition()
                    true
                }
            }
            swipeRefresh.apply {
                setOnRefreshListener { model.refresh(context) }
            }
            replySubmit.setOnClickListener { model.reply(reply) }
            reply.addTextChangedListener { replyHint.isCounterEnabled = !it.isNullOrBlank() }
        }
        model.apply {
            post.observe {
                adapter.notifyItemChanged(0, it)
            }
            list.observe {
                if (post.value == null) adapter.submitList(listOf(ReplyListBottom))
                else adapter.submitList(listOf(ReplyListPost) + it + ReplyListBottom) {
                    binding.recycle.smoothScrollToPosition(0)
                }
            }
            refresh.observe { binding.swipeRefresh.isRefreshing = it }
            info.observe {
                if (!it.isNullOrEmpty())
                    Snackbar.make(binding.root, it, Snackbar.LENGTH_SHORT).apply {
                        anchorView = binding.bottomBar
                        show()
                    }
            }
            sending.observe {
                binding.replySubmit.isEnabled = !it
                binding.replyProgress.visibility = if (it) View.VISIBLE else View.INVISIBLE
            }
            success.observe {
                if (it) {
                    binding.replyHint.hint = "回复原帖"
                    binding.reply.tag = "0"
                    val params = binding.bottomBar.layoutParams as CoordinatorLayout.LayoutParams
                    val behavior = params.behavior as HideBottomViewOnScrollBehavior
                    behavior.slideDown(binding.bottomBar)
                    getSystemService(InputMethodManager::class.java)
                        .hideSoftInputFromWindow(binding.reply.windowToken, 0)
                    binding.reply.clearFocus()
                    model.refresh(this@PostDetailActivity)
                    Snackbar.make(binding.swipeRefresh, "发送成功", Snackbar.LENGTH_SHORT).show()
                    binding.reply.setText("")
                }
            }
            more(this@PostDetailActivity, id = id)
        }
        window.enterTransition = Slide(Gravity.END)
        postponeEnterTransition()
    }

    override fun finishAfterTransition() {
        window.exitTransition = Slide(Gravity.END)
        setResult(RESULT_OK, Intent().apply {
            putExtra("post", model.post.value)
            putExtra("position", intent.getIntExtra("position", 0))
        })
        super.finishAfterTransition()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == LOGIN_RESULT) loadToken()
    }
}
