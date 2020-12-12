package org.wkfg.anonymous

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.*
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.MenuCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.transition.platform.MaterialContainerTransform
import com.google.android.material.transition.platform.MaterialContainerTransformSharedElementCallback
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.wkfg.anonymous.databinding.ActivityNewBinding

class NewPostModel : ViewModel() {
    val sending = MutableLiveData(false)
    val success = MutableLiveData(false)
    val info = MutableLiveData("")

    fun send(
        title: String, category: Post.Category?, noTag: Boolean, tag: Post.Tag?, content: String,
        theme: NameTheme?, random: Boolean, context: Context
    ) = viewModelScope.launch {
        delay(300)
        when {
            title.isBlank() -> info.value = "请输入标题"
            content.isBlank() -> info.value = "请输入内容"
            content.count { it == '\n' } > 20 -> info.value = "换行不能超过20次哟"
            category == null -> info.value = "请选择类别"
            theme == null -> info.value = "请选择匿名主题"
            noTag -> info.value = "请选择标签"
            else -> try {
                sending.value = true
                delay(300)
                Network.post(title, category, tag, content, theme, random)
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

const val NEW_POST_RESULT = 20201207 and 0xffff

class NewPostActivity : AppCompatActivity() {
    lateinit var binding: ActivityNewBinding
    private val model by viewModels<NewPostModel>()

    companion object {
        val categoryMap = mapOf(
            R.id.campus to Post.Category.CAMPUS,
            R.id.entertain to Post.Category.ENTERTAINMENT,
            R.id.emotion to Post.Category.EMOTION,
            R.id.science to Post.Category.SCIENCE,
            R.id.it to Post.Category.IT,
            R.id.social to Post.Category.SOCIAL,
            R.id.music to Post.Category.MUSIC,
            R.id.movie to Post.Category.MOVIE,
            R.id.art to Post.Category.ART,
            R.id.life to Post.Category.LIFE,
        )
        val themeMap = mapOf(
            R.id.alice_and_bob to NameTheme.ALICE_AND_BOB,
            R.id.us_president to NameTheme.US_PRESIDENT,
            R.id.tarot to NameTheme.TAROT,
        )
        val tagMap = mapOf(
            R.id.sex to Post.Tag.SEX,
            R.id.politics to Post.Tag.POLITICS,
            R.id.fake to Post.Tag.FAKE,
            R.id.battle to Post.Tag.BATTLE,
            R.id.uncomfortable to Post.Tag.UNCOMFORTABLE,
            R.id.no to null
        )
    }

    private fun showCategory(button: MaterialButton) {
        PopupMenu(this, button, Gravity.NO_GRAVITY, R.attr.popupMenuStyle, 0).apply {
            MenuCompat.setGroupDividerEnabled(menu, true)
            menuInflater.inflate(R.menu.categories, menu)
            setOnMenuItemClickListener {
                binding.category.text = it.title
                binding.category.tag = categoryMap[it.itemId] ?: error("")
                dismiss()
                true
            }
            show()
        }
    }

    private fun showTheme(button: MaterialButton) {
        PopupMenu(this, button, Gravity.NO_GRAVITY, R.attr.popupMenuStyle, 0).apply {
            MenuCompat.setGroupDividerEnabled(menu, true)
            menuInflater.inflate(R.menu.name_theme, menu)
            setOnMenuItemClickListener {
                binding.theme.text = it.title
                binding.theme.tag = themeMap[it.itemId] ?: error("")
                dismiss()
                true
            }
            show()
        }
    }

    private fun showTag(button: MaterialButton) {
        PopupMenu(this, button, Gravity.NO_GRAVITY, R.attr.popupMenuStyle, 0).apply {
            MenuCompat.setGroupDividerEnabled(menu, true)
            menuInflater.inflate(R.menu.tags, menu)
            setOnMenuItemClickListener {
                binding.tag.text = it.title
                binding.tag.tag = tagMap[it.itemId]
                dismiss()
                true
            }
            show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applyTheme()
        super.onCreate(savedInstanceState)
        postponeEnterTransition()
        loadToken()
        if (intent?.action == Intent.ACTION_SEND) {
            Log.d("trysend", intent.type ?: "null")
            Log.d("trysenddata", intent.getStringExtra(Intent.EXTRA_TEXT) ?: "null")
        }
        binding =
            DataBindingUtil.setContentView<ActivityNewBinding>(this, R.layout.activity_new).apply {
                theme.tag = NameTheme.ALICE_AND_BOB
                tag.tag = Unit
                category.setOnClickListener { showCategory(category) }
                categoryRow.setOnClickListener { showCategory(category) }
                tag.setOnClickListener { showTag(tag) }
                tagRow.setOnClickListener { showTag(tag) }
                theme.setOnClickListener { showTheme(theme) }
                themeRow.setOnClickListener { showTheme(theme) }
                shuffleRow.setOnClickListener { shuffle.isChecked = !shuffle.isChecked }
            }
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finishAfterTransition() }

        model.sending.observe(this) {
            findViewById<View>(R.id.send).visibility = if (it) View.GONE else View.VISIBLE
            binding.progress.visibility = if (it) View.VISIBLE else View.GONE
            binding.apply {
                listOf(
                    category, categoryRow, tag, tagRow, theme, themeRow, shuffle, shuffleRow,
                    title, content,
                ).forEach { view -> view.isEnabled = !it }
            }
        }
        model.success.observe(this) {
            if (it) {
                setResult(NEW_POST_RESULT)
                finishAfterTransition()
            }
        }
        model.info.observe(this) {
            if (!it.isNullOrBlank()) {
                Snackbar.make(binding.layout, it, Snackbar.LENGTH_SHORT).show()
                model.info.value = ""
            }
        }

        findViewById<View>(android.R.id.content).transitionName = "bottom"
        setEnterSharedElementCallback(MaterialContainerTransformSharedElementCallback())
        MaterialContainerTransform().apply {
            addTarget(android.R.id.content)
            isElevationShadowEnabled = false
            startContainerColor = Color.TRANSPARENT
            containerColor = TypedValue().run {
                theme.resolveAttribute(R.attr.colorSurface, this, true)
                data
            }
            fadeMode = MaterialContainerTransform.FADE_MODE_THROUGH
            duration = 400L
            window.sharedElementEnterTransition = this
            window.sharedElementReturnTransition = this
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
        R.id.send -> binding.run {
            model.send(
                title = title.text.toString(),
                category = category.tag as? Post.Category,
                noTag = tag.tag == Unit,
                tag = tag.tag as? Post.Tag,
                content = content.text.toString(),
                theme = theme.tag as? NameTheme,
                random = shuffle.isChecked,
                this@NewPostActivity
            )
            true
        }
        else -> false
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == LOGIN_RESULT) loadToken()
    }
}
