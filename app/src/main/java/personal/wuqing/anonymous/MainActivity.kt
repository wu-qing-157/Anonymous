package personal.wuqing.anonymous

import android.animation.LayoutTransition
import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import android.transition.Fade
import android.transition.Slide
import android.transition.TransitionSet
import android.util.Pair
import android.view.*
import android.widget.LinearLayout
import android.widget.PopupMenu
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import personal.wuqing.anonymous.databinding.ActivityMainBinding
import personal.wuqing.anonymous.databinding.PostCardBinding
import kotlin.time.ExperimentalTime

@ExperimentalTime
class MainActivity : AppCompatActivity() {
    inner class BlogOnClickListener : View.OnClickListener {
        override fun onClick(v: View?) {
            val binding = DataBindingUtil.getBinding<PostCardBinding>(v!!)!!
            val intent = Intent(this@MainActivity, PostDetailActivity::class.java)
            val options =
                ActivityOptions.makeSceneTransitionAnimation(
                    this@MainActivity,
                    Pair.create(v, "post"),
                    Pair.create(binding.avatar, "avatar"),
                    Pair.create(binding.id, "id"),
                    Pair.create(binding.dot, "dot"),
                    Pair.create(binding.update, "update"),
                    Pair.create(binding.title, "title"),
                    Pair.create(binding.favourButton, "favour"),
                    Pair.create(binding.content, "content"),
                    Pair.create(binding.likeButton, "like"),
                    Pair.create(binding.replyButton, "reply"),
                    Pair.create(binding.readButton, "read"),
                    Pair.create(
                        findViewById(android.R.id.statusBarBackground),
                        Window.STATUS_BAR_BACKGROUND_TRANSITION_NAME
                    ),
                    Pair.create(this@MainActivity.binding.fab, "bottom"),
                )
            intent.putExtra("post", DataBindingUtil.getBinding<PostCardBinding>(v)!!.post)
            window.exitTransition = TransitionSet().apply {
                addTransition(Slide(Gravity.START).apply {
                    excludeTarget(R.id.appbar, true)
                })
                addTransition(Fade().apply {
                    addTarget(R.id.appbar)
                })
            }
            model.viewModelScope.launch {
                startActivity(intent, options.toBundle())
            }
        }
    }

    lateinit var binding: ActivityMainBinding
    val model by viewModels<PostListViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        getSharedPreferences("login", MODE_PRIVATE).apply {
            if (contains("token")) Network.token = getString("token", "")!!
            else {
                startActivity(Intent(this@MainActivity, LoginActivity::class.java))
                finish()
            }
        }
        val adapter = PostAdapter(
            postInit = {
                root.setOnClickListener(BlogOnClickListener())
                likeButton.setOnClickListener { model.like(this) }
                favourButton.setOnClickListener { model.favour(this) }
            },
            filterInit = {
                category.setOnClickListener {
                    PopupMenu(
                        this@MainActivity, category,
                        Gravity.NO_GRAVITY, R.attr.popupMenuStyle, 0
                    ).apply {
                        MenuCompat.setGroupDividerEnabled(menu, true)
                        menuInflater.inflate(R.menu.show_categories, menu)
                        setOnMenuItemClickListener { item ->
                            category.text = item.title
                            model.category = when (item.itemId) {
                                R.id.all -> PostListViewModel.Category.ALL
                                R.id.hot -> PostListViewModel.Category.HOT
                                R.id.sports -> PostListViewModel.Category.SPORTS
                                R.id.music -> PostListViewModel.Category.MUSIC
                                R.id.science -> PostListViewModel.Category.SCIENCE
                                R.id.it -> PostListViewModel.Category.IT
                                R.id.entertain -> PostListViewModel.Category.ENTERTAIN
                                R.id.emotion -> PostListViewModel.Category.EMOTION
                                R.id.social -> PostListViewModel.Category.SOCIAL
                                R.id.my -> PostListViewModel.Category.MY
                                R.id.my_reply -> PostListViewModel.Category.REPLY
                                R.id.my_favorite -> PostListViewModel.Category.FAVOUR
                                else -> error("")
                            }
                            dismiss()
                            model.refresh(this@MainActivity)
                            true
                        }
                        show()
                    }
                }
                model.search.observe(this@MainActivity) {
                    layout.visibility = if (it.isNullOrBlank()) View.VISIBLE else View.GONE
                }
            },
            bottomInit = {
                loadMore.setOnClickListener {
                    model.more(this@MainActivity)
                }
                noMore.setOnClickListener {
                    model.refresh(this@MainActivity)
                    binding.recycle.smoothScrollToPosition(0)
                }
                networkError.setOnClickListener {
                    model.refresh(this@MainActivity)
                }
                model.bottom.observe(this@MainActivity) {
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
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        setSupportActionBar(binding.toolbar)
        binding.apply {
            fab.setOnClickListener {
                val intent = Intent(this@MainActivity, NewPostActivity::class.java)
                val options = ActivityOptions.makeSceneTransitionAnimation(
                    this@MainActivity,
                    Pair.create(binding.fab, "new_post"),
                    Pair.create(
                        findViewById(android.R.id.statusBarBackground),
                        Window.STATUS_BAR_BACKGROUND_TRANSITION_NAME
                    ),
//                    Pair.create(this@MainActivity.binding.appbar, "appbar"),
                )
                window.exitTransition = Fade()
                model.viewModelScope.launch {
                    delay(200)
                    startActivity(intent, options.toBundle())
                }
            }
            recycle.apply {
                layoutManager = LinearLayoutManager(context)
                this.adapter = adapter
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
            swipeRefresh.apply {
                setOnRefreshListener {
                    model.refresh(context)
                }
            }
        }
        model.apply {
            list.observe(this@MainActivity) {
                adapter.submitList(listOf(null) + it + listOf(null))
            }
            refresh.observe(this@MainActivity) {
                binding.swipeRefresh.isRefreshing = it
            }
            info.observe(this@MainActivity) {
                if (!it.isNullOrEmpty())
                    Snackbar.make(binding.root, it, Snackbar.LENGTH_SHORT).show()
            }
            search.observe(this@MainActivity) {
                if (it != null) refresh(this@MainActivity)
            }
            more(this@MainActivity)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?) = true.also {
        menuInflater.inflate(R.menu.main_menu, menu)
        val search = menu!!.findItem(R.id.app_bar_search)
        val searchView = search.actionView as SearchView
        search.setOnActionExpandListener(object :
            MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem?) = true
            override fun onMenuItemActionCollapse(item: MenuItem?) = true.also {
                model.search.value = ""
            }
        })
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = true.also {
                model.search.value = query
            }

            override fun onQueryTextChange(newText: String?) = false
        })
        searchView.findViewById<LinearLayout>(R.id.search_bar).layoutTransition = LayoutTransition()
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.refresh -> {
            binding.recycle.smoothScrollToPosition(0)
            model.viewModelScope.launch {
                delay(500)
                model.refresh(this@MainActivity)
            }
            true
        }
        else -> false
    }
}
