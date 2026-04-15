package com.picpose.bestphotographyapp.components.common

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable

object PicPoseWindowInsets {
    @Composable
    fun topAppBar(): WindowInsets = TopAppBarDefaults.windowInsets

    @Composable
    fun screenContent(): WindowInsets =
        WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
}
