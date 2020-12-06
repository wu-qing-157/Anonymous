package personal.wuqing.anonymous

import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import android.transition.*
import android.view.*
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.SearchView
import androidx.core.app.SharedElementCallback
import androidx.core.view.MenuCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.transition.platform.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import personal.wuqing.anonymous.databinding.ActivityMainBinding
import personal.wuqing.anonymous.databinding.PostCardBinding
import java.util.*
import kotlin.time.ExperimentalTime

class MainActivity : AppCompatActivity() {
    lateinit var binding: ActivityMainBinding
    val model by viewModels<PostListViewModel>()

    companion object {
        const val NEW_POST = 1
        const val POST_DETAIL = 2
        const val SETTINGS = 3
        val filterMap = mapOf(
            R.id.all to PostListViewModel.Category.ALL,
            R.id.hot to PostListViewModel.Category.HOT,
            R.id.campus to PostListViewModel.Category.SPORTS,
            R.id.music to PostListViewModel.Category.MUSIC,
            R.id.science to PostListViewModel.Category.SCIENCE,
            R.id.it to PostListViewModel.Category.IT,
            R.id.entertain to PostListViewModel.Category.ENTERTAIN,
            R.id.emotion to PostListViewModel.Category.EMOTION,
            R.id.social to PostListViewModel.Category.SOCIAL,
            R.id.my to PostListViewModel.Category.MY,
            R.id.unread to PostListViewModel.Category.UNREAD,
            R.id.my_favorite to PostListViewModel.Category.FAVOUR,
        )
    }

    private fun openDetail(post: PostCardBinding) {
        val intent = Intent(this, PostDetailActivity::class.java)
        val options = ActivityOptions.makeSceneTransitionAnimation(
            this, *post.run {
                listOf(
                    root, id, update, dot, binding.fab,
                    findViewById(android.R.id.statusBarBackground), binding.appbar,
                ).map { it.pair() }.toTypedArray()
            }
        )
        intent.putExtra("post", post.post)
        intent.putExtra("position", (binding.recycle.adapter as? PostAdapter)?.run {
            currentList.indexOfFirst { it is Post && it.id == post.post!!.id }
        } ?: -1)
        setExitSharedElementCallback(null as SharedElementCallback?)
        window.exitTransition = Slide(Gravity.START).apply {
            interpolator = AccelerateInterpolator()
        }
        window.reenterTransition = Slide(Gravity.START).apply {
            interpolator = DecelerateInterpolator()
        }
        startActivityForResult(intent, POST_DETAIL, options.toBundle())
    }

    private fun openNewPost() {
        val intent = Intent(this, NewPostActivity::class.java)
        val options = ActivityOptions.makeSceneTransitionAnimation(
            this, binding.fab.pair(),
        )
        setExitSharedElementCallback(MaterialContainerTransformSharedElementCallback())
        window.exitTransition = Hold()
        window.reenterTransition = Hold()
        startActivityForResult(intent, NEW_POST, options.toBundle())
    }

    @ExperimentalTime
    private fun showFilter(button: MaterialButton) {
        PopupMenu(this, button, Gravity.NO_GRAVITY, R.attr.popupMenuStyle, 0).apply {
            MenuCompat.setGroupDividerEnabled(menu, true)
            menuInflater.inflate(R.menu.show_categories, menu)
            setOnMenuItemClickListener {
                button.text = it.title
                model.category = filterMap[it.itemId] ?: error("")
                dismiss()
                model.refresh(this@MainActivity)
                true
            }
            show()
        }
    }

    @ExperimentalTime
    override fun onCreate(savedInstanceState: Bundle?) {
        applyTheme()
        super.onCreate(savedInstanceState)
        loadToken()
        model.viewModelScope.launch {
            try {
                Network.verifyToken()
            } catch (e: Network.NotLoggedInException) {
                needLogin()
            } catch (e: Exception) {
                model.info.value = "网络错误"
            }
        }
        val adapter = PostAdapter(
            postInit = {
                root.setOnClickListener {
                    if (expanded.visibility != View.VISIBLE) {
                        (binding.recycle.adapter as? PostAdapter)?.apply {
                            currentList.indexOfFirst { it is Post && it.id == post!!.id }
                                .takeIf { it != -1 }?.let {
                                    notifyItemChanged(it, post!!.copy(expanded = true))
                                }
                        }
                    } else openDetail(this)
                }
            },
            filterInit = {
                category.setOnClickListener { showFilter(category) }
                model.search.observe(this@MainActivity) {
                    layout.visibility = if (it.isNullOrBlank()) View.VISIBLE else View.GONE
                }
            },
            bottomInit = {
                loadMore.setOnClickListener { model.more(this@MainActivity) }
                noMore.setOnClickListener {
                    model.refresh(this@MainActivity)
                    binding.recycle.smoothScrollToPosition(0)
                }
                networkError.setOnClickListener { model.more(this@MainActivity) }
                model.bottom.observe(this@MainActivity) {
                    fun visibility(b: Boolean) = if (b) View.VISIBLE else View.INVISIBLE
                    binding.apply {
                        loadMore.visibility = visibility(it == BottomStatus.IDLE)
                        noMore.visibility = visibility(it == BottomStatus.NO_MORE)
                        networkError.visibility = visibility(it == BottomStatus.NETWORK_ERROR)
                        bottomLoading.visibility = visibility(it == BottomStatus.REFRESHING)
                    }
                }
            }
        )

        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        setSupportActionBar(binding.toolbar)
        binding.fab.setOnClickListener { openNewPost() }
        binding.recycle.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            this.adapter = adapter
            with(itemAnimator as SimpleItemAnimator) { supportsChangeAnimations = false }
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    if (newState == RecyclerView.SCROLL_STATE_IDLE &&
                        model.bottom.value == BottomStatus.IDLE &&
                        (layoutManager as LinearLayoutManager).run {
                            findLastVisibleItemPosition() >= itemCount - 2
                        }
                    ) model.more(context)
                }
            })
        }
        binding.swipeRefresh.apply { setOnRefreshListener { model.refresh(this@MainActivity) } }

        model.list.observe(this) {
            adapter.submitList(listOf(PostListFilter) + it + PostListBottom)
        }
        model.refresh.observe(this) { binding.swipeRefresh.isRefreshing = it }
        model.info.observe(this) {
            if (!it.isNullOrBlank())
                Snackbar.make(binding.swipeRefresh, it, Snackbar.LENGTH_SHORT).show()
        }
        model.search.observe(this) { it?.let { model.refresh(this) } }

        model.more(this)
    }

    override fun onCreateOptionsMenu(menu: Menu) = true.also {
        menuInflater.inflate(R.menu.main_menu, menu)
        menu.findItem(R.id.app_bar_search).apply {
            setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
                override fun onMenuItemActionExpand(item: MenuItem?) = true
                override fun onMenuItemActionCollapse(item: MenuItem?) = true.also {
                    model.search.value = ""
                }
            })
            with(actionView as SearchView) {
                setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                    override fun onQueryTextChange(newText: String?) = false
                    override fun onQueryTextSubmit(query: String?) = true.also {
                        model.search.value = query
                    }
                })
                // TODO: search bar transition
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
        R.id.refresh -> {
            binding.recycle.smoothScrollToPosition(0)
            model.viewModelScope.launch {
                delay(500)
                model.refresh(this@MainActivity)
            }
            true
        }
        R.id.logout -> {
            clearToken()
            needLogin()
            model.list.value = listOf()
            true
        }
        R.id.settings -> {
            startActivityForResult(Intent(this, SettingsActivity::class.java), SETTINGS)
            true
        }
        else -> false
    }

    override fun onActivityReenter(resultCode: Int, data: Intent?) {
        if (resultCode == RESULT_OK) (data?.getSerializableExtra("post") as? Post)?.apply {
            data.getIntExtra("position", 0).takeIf { it > 0 }?.let {
                binding.recycle.adapter?.notifyItemChanged(
                    it, copy(showInDetail = false, expanded = true, like = Like.LIKE_WAIT)
                )
            }
        }
    }

    @ExperimentalTime
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (resultCode) {
            LOGIN_RESULT -> {
                loadToken()
                model.refresh(this)
            }
            NEW_POST_RESULT -> {
                Snackbar.make(binding.swipeRefresh, "发帖成功", Snackbar.LENGTH_SHORT).show()
                model.refresh(this)
            }
            SettingsActivity.SETTINGS_RESULT -> {
                applyTheme()
                recreate()
            }
        }
    }
}
