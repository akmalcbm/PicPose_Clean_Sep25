package com.picpose.bestphotographyapp.navigation

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

enum class BottomTabReselectionAction {
    ScrollToTop
}

data class BottomTabReselectionEvent(
    val route: String,
    val action: BottomTabReselectionAction
)

class BottomTabReselectManager {
    private val _events = MutableSharedFlow<BottomTabReselectionEvent>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    fun emitScrollToTop(route: String) {
        _events.tryEmit(
            BottomTabReselectionEvent(
                route = route,
                action = BottomTabReselectionAction.ScrollToTop
            )
        )
    }

    fun scrollToTopEvents(route: String): Flow<Unit> {
        return _events
            .filter { event ->
                event.route == route && event.action == BottomTabReselectionAction.ScrollToTop
            }
            .map { Unit }
    }
}
