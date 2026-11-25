package com.example.transferstation

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.transferstation.core.display.preferHighRefreshRate
import com.example.transferstation.presentation.ChatViewModel
import com.example.transferstation.ui.ChatScreen
import com.example.transferstation.ui.theme.TransferStationTheme

/** 管理单 Activity 的 Compose 聊天主界面和任务通知返回入口。 */
class MainActivity : ComponentActivity() {
    /** 设置应用级全屏聊天主界面及 URI、通知跳转处理。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        render(initialSessionId = intent.getStringExtra(EXTRA_SESSION_ID))
    }

    /** 返回前台时根据当前屏幕模式更新窗口的高刷新率偏好。 */
    override fun onResume() {
        super.onResume()
        window.preferHighRefreshRate(display)
    }

    /** 将完成通知指向的历史会话发送到当前活动页面。 */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(EXTRA_SESSION_ID)?.let { sessionId ->
            render(initialSessionId = sessionId)
        }
    }

    /** 以 ViewModel 当前外观偏好渲染聊天路由。 */
    private fun render(initialSessionId: String?) {
        setContent {
            val stateHolder: ChatViewModel = viewModel()
            val state by stateHolder.state.collectAsState()
            TransferStationTheme(appearance = state.snapshot.appearanceSettings) {
                ChatScreen(initialSessionId = initialSessionId, viewModel = stateHolder)
            }
        }
    }

    /** 供聊天完成通知定位相关历史会话。 */
    companion object {
        const val EXTRA_SESSION_ID = "com.example.transferstation.extra.SESSION_ID"
    }
}
