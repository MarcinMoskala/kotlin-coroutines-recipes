package recipes

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Emits value every [period], if there is one. Emits immediately, if time between
 * two consecutively emitted values is greater or equals [period]
 * */
fun <T> Flow<T>.enqueueWithDelay(period: Duration = 1.seconds): Flow<T> {
    val periodMs = period.inWholeMilliseconds
    return flow {
        var lastEmitTime = 0L
        this@enqueueWithDelay.collect { value ->
            val currentTime = currentTimeMillis()
            val elapsedTime = currentTime - lastEmitTime

            val waitTime = periodMs - elapsedTime
            if (waitTime > 0) delay(waitTime)

            emit(value)
            lastEmitTime = currentTimeMillis()
        }
    }
}

private fun currentTimeMillis(): Long = Clock.System.now().toEpochMilliseconds()