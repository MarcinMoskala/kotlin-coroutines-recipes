package recipes

import kotlinx.coroutines.CancellationException

inline fun <T> retryWhen(
    predicate: (Throwable, retries: Int) -> Boolean,
    operation: () -> T
): T {
    var retries = 0
    var fromDownstream: Throwable? = null
    while (true) {
        try {
            return operation()
        } catch (e: Throwable) {
            if (fromDownstream != null) {
                e.addSuppressed(fromDownstream)
            }
            fromDownstream = e
            if (e is CancellationException || !predicate(e, retries++)) {
                throw e
            }
        }
    }
}
