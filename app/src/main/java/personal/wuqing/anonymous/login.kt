package personal.wuqing.anonymous

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Patterns
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import personal.wuqing.anonymous.databinding.ActivityLoginBinding
import java.util.*
import kotlin.time.ExperimentalTime

@ExperimentalTime
fun Context.needLogin() = (this as Activity).apply {
    startActivity(Intent(this, LoginActivity::class.java))
    finish()
}

@ExperimentalTime
class LoginViewModel : ViewModel() {
    val count = MutableLiveData(0)
    val loginProcess = MutableLiveData(false)
    val info = MutableLiveData("")
    val result = MutableLiveData(false)
    private var timer: CountDownTimer? = null

    fun code(email: String) = viewModelScope.launch {
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches())
            info.value = "邮箱格式不合法"
        else
            try {
                count.value = -1
                delay(200)
                if (Network.requestLoginCode(email)) {
                    timer?.cancel()
                    timer = object : CountDownTimer(60000, 1000) {
                        override fun onFinish() {
                            timer = null
                            count.value = 0
                        }

                        override fun onTick(millisUntilFinished: Long) {
                            count.value = millisUntilFinished.toInt() / 1000
                        }
                    }.apply { start() }
                    info.value = "已发送验证码"
                } else {
                    info.value = "使用的邮箱不支持登录"
                }
            } catch (e: Exception) {
                info.value = "网络错误"
            } finally {
                if (timer == null) count.value = 0
            }
    }

    fun login(email: String, code: String, device: String) = viewModelScope.launch {
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches())
            info.value = "邮箱格式不合法"
        else if (!code.matches(Regex("[0-9]{6}")))
            info.value = "验证码错误"
        else
            try {
                loginProcess.value = true
                delay(200)
                if (Network.login(email, code, device))
                    result.value = true
                else
                    info.value = "验证码错误"
            } catch (e: Exception) {
                info.value = "网络错误"
            } finally {
                loginProcess.value = false
            }
    }
}

@ExperimentalTime
class LoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = getSharedPreferences("login", MODE_PRIVATE)
        if ("uuid" !in prefs) with(prefs.edit()) {
            putString("uuid", UUID.randomUUID().toString())
            apply()
        }
        super.onCreate(savedInstanceState)
        val model by viewModels<LoginViewModel>()
        val binding =
            DataBindingUtil.setContentView<ActivityLoginBinding>(this, R.layout.activity_login)
        binding.sendCode.setOnClickListener { model.code(binding.email.text.toString()) }
        binding.login.setOnClickListener {
            model.login(
                binding.email.text.toString(), binding.code.text.toString(),
                prefs.getString("uuid", "")!!
            )
        }
        binding.code.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE && binding.login.isEnabled) {
                binding.login.performClick()
                true
            } else false
        }
        model.count.observe(this) {
            binding.apply {
                when (it) {
                    0 -> {
                        sendCode.text = "发送验证码"
                        sendCode.isEnabled = true
                        codeLoading.visibility = View.INVISIBLE
                    }
                    -1 -> {
                        sendCode.text = "发送验证码"
                        sendCode.isEnabled = false
                        codeLoading.visibility = View.VISIBLE
                    }
                    else -> {
                        sendCode.text = it.toString()
                        sendCode.isEnabled = false
                        codeLoading.visibility = View.INVISIBLE
                    }
                }
            }
        }
        model.loginProcess.observe(this) {
            binding.login.isEnabled = !it
            binding.loginLoading.visibility = if (it) View.VISIBLE else View.INVISIBLE
        }
        model.info.observe(this) {
            if (it != "") Snackbar.make(binding.root, it, Snackbar.LENGTH_SHORT).show()
        }
        model.result.observe(this) {
            if (it) {
                with(prefs.edit()) {
                    putString("token", Network.token)
                    apply()
                }
                Toast.makeText(this, "登录成功", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, MainActivity::class.java))
            }
        }
    }
}