import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Collects elements from the upstream flow and invokes [action] with a list of elements
 * as soon as [batchSize] elements are accumulated. When the flow terminates
 * (normal completion, cancellation, or exception), [action] is invoked with
 * the remaining collected elements.
 *
 * Elements are emitted downstream immediately as they are received from upstream.
 */
fun <T> Flow<T>.onEachBatch(
    batchSize: Int,
    action: (List<T>) -> Unit
): Flow<T> = flow {
    var collected = mutableListOf<T>()
    try {
        collect { value ->
            collected += value
            if (collected.size >= batchSize) {
                val batch = collected
                collected = mutableListOf()
                action(batch)
            }
            emit(value)
        }
    } finally {
        if (collected.isNotEmpty()) {
            val batch = collected
            collected = mutableListOf()
            action(batch)
        }
    }
}