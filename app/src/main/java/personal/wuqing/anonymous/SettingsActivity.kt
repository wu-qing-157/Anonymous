package personal.wuqing.anonymous

import android.net.Uri
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SettingsActivity : AppCompatActivity() {

    companion object {
        const val SETTINGS_RESULT = 20201206 and 0xffff
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applyTheme()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, SettingsFragment())
                .commit()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        setResult(SETTINGS_RESULT)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        android.R.id.home -> {
            finish()
            true
        }
        else -> false
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)
            findPreference<Preference>("zen")?.setOnPreferenceClickListener {
                MaterialAlertDialogBuilder(requireContext()).apply {
                    setTitle("The Zen of WKFG, by T. He")
                    setMessage(R.string.zen)
                    setCancelable(true)
                    show()
                }
                true
            }
            findPreference<Preference>("logout")?.setOnPreferenceClickListener {
                MaterialAlertDialogBuilder(requireContext()).apply {
                    setTitle("退出登录")
                    setPositiveButton("确定") { _, _ ->
                        requireActivity().apply {
                            clearToken()
                            needLogin()
                            finish()
                        }
                    }
                    setNegativeButton("手滑了", null)
                    setCancelable(true)
                    show()
                }
                true
            }
            listOf(
                "net" to "http://wukefenggao.cn",
                "android" to "https://github.com/wu-qing-157/Anonymous",
                "rule" to "http://wukefenggao.cn/code",
            ).forEach { (key, url) ->
                findPreference<Preference>(key)?.setOnPreferenceClickListener {
                    requireContext().launchCustomTab(Uri.parse(url))
                    true
                }
            }
            findPreference<ListPreference>("color")?.apply {
                entries = themes.map { (_, style) ->
                    SpannableString("主题色 强调色").apply {
                        setSpan(
                            TagSpan(
                                requireContext().obtainStyledAttributes(
                                    style, intArrayOf(R.attr.colorPrimary)
                                ).run {
                                    getColor(0, 0).also { recycle() }
                                },
                                requireContext().obtainStyledAttributes(
                                    style, intArrayOf(R.attr.colorOnPrimary)
                                ).run {
                                    getColor(0, 0).also { recycle() }
                                },
                                bold = false
                            ), 0, 3, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        setSpan(
                            TagSpan(
                                requireContext().obtainStyledAttributes(
                                    style, intArrayOf(R.attr.colorSecondary)
                                ).run {
                                    getColor(0, 0).also { recycle() }
                                },
                                requireContext().obtainStyledAttributes(
                                    style, intArrayOf(R.attr.colorOnSecondary)
                                ).run {
                                    getColor(0, 0).also { recycle() }
                                },
                                bold = false
                            ), 4, 7, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                }.toTypedArray()
                entryValues = themes.map { (value, _) -> value }.toTypedArray()
                setOnPreferenceChangeListener { _, _ ->
                    requireActivity().recreate()
                    true
                }
            }
        }
    }
}
