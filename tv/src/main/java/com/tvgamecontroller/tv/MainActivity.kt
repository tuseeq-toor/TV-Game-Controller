package com.tvgamecontroller.tv

import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.tvgamecontroller.tv.ui.TvHome
import com.tvgamecontroller.tv.ui.theme.TvHostTheme

class MainActivity : ComponentActivity() {
    private val viewModel: HostViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TvHostTheme {
                val state by viewModel.ui.collectAsState()
                TvHome(state)
            }
        }
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        viewModel.onPhysicalMotion(event)
        return super.onGenericMotionEvent(event)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        viewModel.onPhysicalKey(event)
        return super.dispatchKeyEvent(event)
    }
}
