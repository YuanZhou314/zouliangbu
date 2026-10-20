package com.zouliangbu.footprint

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.zouliangbu.footprint.ui.AppRoot
import com.zouliangbu.footprint.ui.theme.FootprintTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FootprintTheme {
                AppRoot()
            }
        }
    }
}