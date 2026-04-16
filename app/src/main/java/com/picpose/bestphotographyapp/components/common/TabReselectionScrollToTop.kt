package com.picpose.bestphotographyapp.components.common

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

@Composable
fun ObserveTabReselectionScrollToTop(
    scrollToTopEvents: Flow<Unit>,
    listState: LazyListState
) {
    LaunchedEffect(scrollToTopEvents, listState) {
        scrollToTopEvents.collectLatest {
            if (listState.layoutInfo.totalItemsCount == 0) return@collectLatest

            val alreadyNearTop =
                listState.firstVisibleItemIndex == 0 &&
                    listState.firstVisibleItemScrollOffset <= 8

            if (alreadyNearTop) return@collectLatest

            listState.animateScrollToItem(0)
        }
    }
}
