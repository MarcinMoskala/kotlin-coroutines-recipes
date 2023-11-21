package recipes

import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine

fun <T> flowOf(producer: suspend () -> T) = flow { emit(producer()) }

val neverFlow = flow<Nothing> { suspendCancellableCoroutine<Unit> { } }

fun <T> infiniteFlowOf(producer: suspend () -> T) = flow<T> {
    while (true) {
        emit(producer())
    }
}
