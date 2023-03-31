package recipes

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

fun <T> suspendLazy(
    initializer: suspend () -> T
): suspend () -> T {
    var innerInitializer: (suspend () -> T)? = initializer
    val mutex = Mutex()
    var holder: Any? = Any()

    return {
        @Suppress("UNCHECKED_CAST")
        if (innerInitializer == null) holder as T
        else mutex.withLock {
            innerInitializer?.let {
                holder = it()
                innerInitializer = null
            }
            holder as T
        }
    }
}
