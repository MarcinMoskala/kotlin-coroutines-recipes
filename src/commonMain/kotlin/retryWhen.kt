package recipes

inline fun <T> retryWhen(
    predicate: (Throwable, retries: Int) -> Boolean,
    operation: () -> T
): T {
    var retries = 0
    while (true) {
        try {
            return operation()
        } catch (e: Throwable) {
            if (!predicate(e, retries++)) {
                throw e
            }
        }
    }
}
