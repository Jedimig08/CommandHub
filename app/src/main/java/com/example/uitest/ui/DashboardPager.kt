package com.example.uitest.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.uitest.util.KeepScreenOn
import com.example.uitest.viewmodel.DashboardViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DashboardPager(
    viewModel: DashboardViewModel = viewModel(),
) {
    KeepScreenOn(enabled = viewModel.keepScreenOn)

    val presets = viewModel.statePresets

    if (presets.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
    } else {
        val pagerState = rememberPagerState { presets.size }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { pageIndex ->
            val safePageIndex = pageIndex.coerceAtMost(presets.lastIndex)
            DashboardPage(
                modules = presets[safePageIndex],
                pageIndex = safePageIndex,
                totalPages = presets.size,
                viewModel = viewModel,
            )
        }
    }
}