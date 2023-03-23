package recipes

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val NOT_SET = Any()

fun <T> suspendLazy(
    initializer: suspend () -> T
): suspend () -> T {
    var initializer: (suspend () -> T)? = initializer
    val mutex = Mutex()
    var holder: Any? = Any()

    return {
        if (initializer == null) holder as T
        else mutex.withLock {
            initializer?.let {
                holder = it()
                initializer = null
            }
            holder as T
        }
    }
}
