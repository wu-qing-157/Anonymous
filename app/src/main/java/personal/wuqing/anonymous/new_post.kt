package personal.wuqing.anonymous

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.transition.Fade
import android.transition.Slide
import android.transition.TransitionSet
import android.view.*
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import personal.wuqing.anonymous.databinding.ActivityNewBinding
import kotlin.time.ExperimentalTime

@ExperimentalTime
class NewPostModel : ViewModel() {
    val sending = MutableLiveData(false)
    val success = MutableLiveData(false)
    val info = MutableLiveData("")

    fun send(
        title: String, category: Post.Category?, content: String,
        theme: NameTheme?, random: Boolean, context: Context
    ) = viewModelScope.launch {
        delay(300)
        when {
            title.isBlank() -> info.value = "请输入标题"
            content.isBlank() -> info.value = "请输入内容"
            category == null -> info.value = "请选择类别"
            theme == null -> info.value = "请选择匿名主题"
            else -> try {
                sending.value = true
                delay(300)
                Network.post(title, category, content, theme, random)
                success.value = true
            } catch (e: Network.NotLoggedInException) {
                context.needLogin()
            } catch (e: Exception) {
                info.value = "网络错误"
            } finally {
                sending.value = false
            }
        }
    }
}

@ExperimentalTime
class NewPostActivity : AppCompatActivity() {
    lateinit var binding: ActivityNewBinding
    val model by viewModels<NewPostModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        postponeEnterTransition()
        binding = DataBindingUtil.setContentView(this, R.layout.activity_new)
        getSharedPreferences("login", MODE_PRIVATE).apply {
            if (contains("token")) Network.token = getString("token", "")!!
            else {
                startActivity(Intent(this@NewPostActivity, LoginActivity::class.java))
                finish()
            }
        }
        setSupportActionBar(binding.toolbar)
        binding.apply {
            toolbar.setNavigationOnClickListener { finishAfterTransition() }
            theme.tag = NameTheme.ALICE_AND_BOB
            fun showCategoryMenu() = PopupMenu(
                this@NewPostActivity, category,
                Gravity.NO_GRAVITY, R.attr.popupMenuStyle, 0
            ).apply {
                MenuCompat.setGroupDividerEnabled(menu, true)
                menuInflater.inflate(R.menu.categories, menu)
                setOnMenuItemClickListener { item ->
                    category.text = item.title
                    category.tag = when (item.itemId) {
                        R.id.sports -> Post.Category.SPORT
                        R.id.music -> Post.Category.MUSIC
                        R.id.science -> Post.Category.SCIENCE
                        R.id.it -> Post.Category.IT
                        R.id.entertain -> Post.Category.ENTERTAINMENT
                        R.id.emotion -> Post.Category.EMOTION
                        R.id.social -> Post.Category.SOCIAL
                        R.id.others -> Post.Category.OTHERS
                        else -> error("")
                    }
                    dismiss()
                    true
                }
                show()
            }

            fun showThemeMenu() = PopupMenu(
                this@NewPostActivity, theme,
                Gravity.NO_GRAVITY, R.attr.popupMenuStyle, 0
            ).apply {
                MenuCompat.setGroupDividerEnabled(menu, true)
                menuInflater.inflate(R.menu.name_theme, menu)
                setOnMenuItemClickListener { item ->
                    theme.text = item.title
                    theme.tag = when (item.itemId) {
                        R.id.alice_and_bob -> NameTheme.ALICE_AND_BOB
                        R.id.us_president -> NameTheme.US_PRESIDENT
                        R.id.tarot -> NameTheme.TAROT
                        else -> error("")
                    }
                    dismiss()
                    true
                }
                show()
            }
            category.setOnClickListener { showCategoryMenu() }
            categoryRow.setOnClickListener { showCategoryMenu() }
            theme.setOnClickListener { showThemeMenu() }
            themeRow.setOnClickListener { showThemeMenu() }
            shuffleRow.setOnClickListener { shuffle.isChecked = !shuffle.isChecked }
        }
        model.apply {
            sending.observe(this@NewPostActivity) {
                binding.apply {
                    findViewById<View>(R.id.send).visibility = if (it) View.GONE else View.VISIBLE
                    progress.visibility = if (it) View.VISIBLE else View.GONE
                    category.isEnabled = !it
                    theme.isEnabled = !it
                    shuffle.isEnabled = !it
                    shuffleRow.isEnabled = !it
                    title.isEnabled = !it
                    content.isEnabled = !it
                }
            }
            success.observe(this@NewPostActivity) {
                if (it) {
                    Toast.makeText(this@NewPostActivity, "发送成功", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            info.observe(this@NewPostActivity) {
                if (!it.isNullOrBlank())
                    Snackbar.make(binding.layout, it, Snackbar.LENGTH_SHORT).show()
            }
        }
        TransitionSet().apply {
            addTransition(Slide(Gravity.END).apply {
                excludeTarget(binding.appbar, true)
            })
            addTransition(Fade().apply {
                addTarget(binding.appbar)
            })
        }.let {
            window.enterTransition = it
            window.returnTransition = it
        }
        window.decorView.viewTreeObserver.addOnPreDrawListener {
            startPostponedEnterTransition()
            true
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?) = true.also {
        menuInflater.inflate(R.menu.new_post_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.send -> true.also {
            model.send(
                title = binding.title.text.toString(),
                category = binding.category.tag as? Post.Category,
                content = binding.content.text.toString(),
                theme = binding.theme.tag as? NameTheme,
                random = binding.shuffle.isChecked,
                this
            )
        }
        else -> false
    }
}
