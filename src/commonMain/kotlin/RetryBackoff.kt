package recipes

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random
import kotlin.time.Duration

fun <T> Flow<T>.retryBackoff(
    minDelay: Duration,
    maxDelay: Duration = Duration.INFINITE,
    maxAttempts: Int = Int.MAX_VALUE,
    backoffFactor: Double = 2.0,
    transient: Boolean = true,
    jitterFactor: Double = 0.1,
    random: Random = Random,
    successAfterRetry: suspend (attempts: Int) -> Unit = {},
    beforeRetry: suspend (cause: Throwable, currentAttempts: Int, totalAttempts: Int) -> Unit = { _, _, _ -> },
    retriesExhausted: suspend (cause: Throwable) -> Unit = {},
    retryCondition: suspend (cause: Throwable, currentAttempts: Int, totalAttempts: Int) -> Boolean = { _, _, _, -> true }
): Flow<T> {
    require(jitterFactor in 0.0..1.0)
    require(maxAttempts > 0)
    require(backoffFactor > 1.0)
    require(minDelay in Duration.ZERO..maxDelay)
    
    return flow {
        var attemptsInRow = 0
        var isRetrying = false
        this@retryBackoff
            .retryWhen { cause, totalAttempts ->
                isRetrying = true
                val currentAttempts = if (transient) attemptsInRow else totalAttempts.toInt()
                if (currentAttempts == maxAttempts) {
                    retriesExhausted(cause)
                    return@retryWhen false
                }
                
                val effectiveDelay =
                    calculateBackoffDelay(minDelay, maxDelay, currentAttempts, jitterFactor, random, backoffFactor)
                
                beforeRetry(cause, currentAttempts, totalAttempts.toInt())
                val shouldRetry = retryCondition(cause, currentAttempts, totalAttempts.toInt())
                if (!shouldRetry) return@retryWhen false
                delay(effectiveDelay.toLong())
                attemptsInRow++
                true
            }
            .onEach {
                if (isRetrying) {
                    successAfterRetry(attemptsInRow)
                    isRetrying = false
                }
                if (transient) attemptsInRow = 0
            }
            .collect(this)
    }
}

// TODO: Publish once unit-tested
internal suspend fun <T> retryBackoff(
    minDelay: Duration,
    maxDelay: Duration = Duration.INFINITE,
    maxAttempts: Int = Int.MAX_VALUE,
    backoffFactor: Double = 2.0,
    jitterFactor: Double = 0.1,
    random: Random = Random,
    beforeRetry: suspend (cause: Throwable, attempts: Int) -> Unit = { _, _ -> },
    retriesExhausted: suspend (cause: Throwable) -> Unit = {},
    block: () -> T,
): T {
    require(jitterFactor in 0.0..1.0)
    require(maxAttempts > 0)
    require(backoffFactor > 1.0)
    require(minDelay in Duration.ZERO..maxDelay)
    
    return retryWhen(
        predicate = { cause, currentAttempts ->
            if (currentAttempts == maxAttempts) {
                retriesExhausted(cause)
                return@retryWhen false
            }
            
            val effectiveDelay = calculateBackoffDelay(
                minDelay, maxDelay, currentAttempts, jitterFactor, random,
                backoffFactor
            )
            beforeRetry(cause, currentAttempts)
            delay(effectiveDelay.toLong())
            true
        },
        operation = block,
    )
}

private fun calculateBackoffDelay(
    minDelay: Duration,
    maxDelay: Duration,
    currentAttempts: Int,
    jitterFactor: Double,
    random: Random,
    backoffFactor: Double,
): Double {
    val minDelayMillis = minDelay.inWholeMilliseconds.toDouble()
    val maxDelayMillis = maxDelay.inWholeMilliseconds.toDouble()
    val baseDelay = (minDelayMillis * backoffFactor.pow(currentAttempts))
        .coerceAtMost(maxDelayMillis)
    
    return if (jitterFactor == 0.0) {
        baseDelay
    } else {
        val jitterOffset = baseDelay * jitterFactor
        val minJitter = max(minDelayMillis, baseDelay - jitterOffset)
        val maxJitter = min(maxDelayMillis, baseDelay + jitterOffset)
        if (minJitter == maxJitter) {
            minJitter
        } else {
            random.nextDouble(minJitter, maxJitter)
        }
    }
}