import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime

import kotlinx.coroutines.test.runTest
import recipes.retryBackoff
import kotlin.random.Random

import kotlin.test.Test

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class RetryBackoffFlowTest {

    private fun failingNTimesFlow(failingTimes: Int): Flow<String> {
        var attempt = 0
        return flow {
            when (attempt++) {
                in 0 until failingTimes -> throw TestException("Error$attempt")
                else -> emit("ABC")
            }
        }
    }

    @Test
    fun should_retry_single_failing_call() = runTest {
        val error = object : Throwable() {}
        var first = true
        flow {
            if (first) {
                first = false
                throw error
            } else {
                emit("ABC")
            }
        }.testBackoffRetry(
            testScope = this,
            minDelay = 1.seconds,
            maxDelay = 5.seconds,
            maxAttempts = 11,
            jitterFactor = 0.0,
            expectResult = Result.success(listOf("ABC")),
            expectSuccessAfterRetry = listOf(
                SuccessAfterRetryCall(attempts=1, time=1000)
            ),
            expectRetriesExhaustedCalls = listOf(),
            expectBeforeRetryCalls = listOf(
                BeforeRetryCall(error, 0, 0, 0),
            )
        )
    }

    @Test
    fun should_retry_multiple_times_with_growing_backoff() = runTest {
        failingNTimesFlow(10).testBackoffRetry(
            testScope = this,
            minDelay = 1.seconds,
            maxAttempts = 11,
            jitterFactor = 0.0,
            expectResult = Result.success(listOf("ABC")),
            expectRetriesExhaustedCalls = listOf(), // Should not call retriesExhausted when maxAttempts is not reached
            expectSuccessAfterRetry = listOf(SuccessAfterRetryCall(attempts=10, time=512000 + 256000 + 128000 + 64000 + 32000 + 16000 + 8000 + 4000 + 2000 + 1000)),
            expectBeforeRetryCalls = listOf(
                BeforeRetryCall(TestException("Error1"), 0, 0, 0),
                BeforeRetryCall(TestException("Error2"), 1, 1, 1000),
                BeforeRetryCall(TestException("Error3"), 2, 2, 2000 + 1000),
                BeforeRetryCall(TestException("Error4"), 3, 3, 4000 + 2000 + 1000),
                BeforeRetryCall(TestException("Error5"), 4, 4, 8000 + 4000 + 2000 + 1000),
                BeforeRetryCall(TestException("Error6"), 5, 5, 16000 + 8000 + 4000 + 2000 + 1000),
                BeforeRetryCall(TestException("Error7"), 6, 6, 32000 + 16000 + 8000 + 4000 + 2000 + 1000),
                BeforeRetryCall(TestException("Error8"), 7, 7, 64000 + 32000 + 16000 + 8000 + 4000 + 2000 + 1000),
                BeforeRetryCall(
                    TestException("Error9"),
                    8,
                    8,
                    128000 + 64000 + 32000 + 16000 + 8000 + 4000 + 2000 + 1000
                ),
                BeforeRetryCall(
                    TestException("Error10"),
                    9,
                    9,
                    256000 + 128000 + 64000 + 32000 + 16000 + 8000 + 4000 + 2000 + 1000
                ),
            )
        )
    }

    @Test
    fun should_calculate_only_following_calls_for_max_attempts() = runTest {
        var attempt = 0
        flow {
            while (true) {
                when (attempt++ % 4) {
                    3 -> emit("Result$attempt")
                    else -> throw TestException("Call$attempt")
                }
            }
        }.testBackoffRetry(
            testScope = this,
            elementsToExpect = 3,
            minDelay = 1.seconds,
            maxAttempts = 4,
            jitterFactor = 0.0,
            expectResult = Result.success(listOf("Result4", "Result8", "Result12")),
            expectRetriesExhaustedCalls = listOf(),
            expectSuccessAfterRetry = listOf(
                SuccessAfterRetryCall(attempts = 3, time = 4000 + 2000 + 1000),
                SuccessAfterRetryCall(attempts = 3, time = 4000 + 2000 + 1000 + 4000 + 2000 + 1000),
                SuccessAfterRetryCall(
                    attempts = 3,
                    time = 4000 + 2000 + 1000 + 4000 + 2000 + 1000 + 4000 + 2000 + 1000
                ),
            ),
            expectBeforeRetryCalls = listOf(
                BeforeRetryCall(TestException("Call1"), 0, 0, 0),
                BeforeRetryCall(TestException("Call2"), 1, 1, 1000),
                BeforeRetryCall(TestException("Call3"), 2, 2, 2000 + 1000),
                BeforeRetryCall(TestException("Call5"), 0, 3, 4000 + 2000 + 1000),
                BeforeRetryCall(TestException("Call6"), 1, 4, 1000 + 4000 + 2000 + 1000),
                BeforeRetryCall(TestException("Call7"), 2, 5, 2000 + 1000 + 4000 + 2000 + 1000),
                BeforeRetryCall(TestException("Call9"), 0, 6, 4000 + 2000 + 1000 + 4000 + 2000 + 1000),
                BeforeRetryCall(TestException("Call10"), 1, 7, 1000 + 4000 + 2000 + 1000 + 4000 + 2000 + 1000),
                BeforeRetryCall(TestException("Call11"), 2, 8, 2000 + 1000 + 4000 + 2000 + 1000 + 4000 + 2000 + 1000),
            )
        )
    }

    @Test
    fun should_calculate_all_calls_for_non_transient() = runTest {
        var attempt = 0
        flow {
            while (true) {
                when (attempt++ % 4) {
                    3 -> emit("Result$attempt")
                    else -> throw TestException("Call$attempt")
                }
            }
        }.testBackoffRetry(
            testScope = this,
            elementsToExpect = 3,
            minDelay = 1.seconds,
            transient = false,
            jitterFactor = 0.0,
            expectResult = Result.success(listOf("Result4", "Result8", "Result12")),
            expectRetriesExhaustedCalls = listOf(),
            expectSuccessAfterRetry = listOf(
                SuccessAfterRetryCall(attempts = 3, time = 4000 + 2000 + 1000),
                SuccessAfterRetryCall(attempts = 6, time = 32000 + 16000 + 8000 + 4000 + 2000 + 1000),
                SuccessAfterRetryCall(attempts = 9, time = 256000 + 128000 + 64000 + 32000 + 16000 + 8000 + 4000 + 2000 + 1000)
            ),
            expectBeforeRetryCalls = listOf(
                BeforeRetryCall(TestException("Call1"), 0, 0, 0),
                BeforeRetryCall(TestException("Call2"), 1, 1, 1000),
                BeforeRetryCall(TestException("Call3"), 2, 2, 2000 + 1000),
                BeforeRetryCall(TestException("Call5"), 3, 3, 4000 + 2000 + 1000),
                BeforeRetryCall(TestException("Call6"), 4, 4, 8000 + 4000 + 2000 + 1000),
                BeforeRetryCall(TestException("Call7"), 5, 5, 16000 + 8000 + 4000 + 2000 + 1000),
                BeforeRetryCall(TestException("Call9"), 6, 6, 32000 + 16000 + 8000 + 4000 + 2000 + 1000),
                BeforeRetryCall(TestException("Call10"), 7, 7, 64000 + 32000 + 16000 + 8000 + 4000 + 2000 + 1000),
                BeforeRetryCall(TestException("Call11"), 8, 8, 128000 + 64000 + 32000 + 16000 + 8000 + 4000 + 2000 + 1000),
            )
        )
    }

    @Test
    fun should_use_backoff_factor() = runTest {
        failingNTimesFlow(10).testBackoffRetry(
            testScope = this,
            minDelay = 1.seconds,
            backoffFactor = 3.0,
            maxAttempts = 5,
            jitterFactor = 0.0,
            expectResult = Result.failure(TestException("Error6")),
            expectRetriesExhaustedCalls = listOf(
                RetryExhaustedCall(TestException("Error6"), 121000)
            ),
            expectSuccessAfterRetry = listOf(),
            expectBeforeRetryCalls = listOf(
                BeforeRetryCall(TestException("Error1"), 0, 0, 0),
                BeforeRetryCall(TestException("Error2"), 1, 1, 1000),
                BeforeRetryCall(TestException("Error3"), 2, 2, 3000 + 1000),
                BeforeRetryCall(TestException("Error4"), 3, 3, 9000 + 3000 + 1000),
                BeforeRetryCall(TestException("Error5"), 4, 4, 27000 + 9000 + 3000 + 1000),
            )
        )
    }

    @Test
    fun should_stop_backoff_growing_once_max_reached() = runTest {
        failingNTimesFlow(10).testBackoffRetry(
            testScope = this,
            minDelay = 1.seconds,
            maxDelay = 5.seconds,
            maxAttempts = 11,
            jitterFactor = 0.0,
            expectResult = Result.success(listOf("ABC")),
            expectRetriesExhaustedCalls = listOf(), // Should not call retriesExhausted when maxAttempts is not reached
            expectSuccessAfterRetry = listOf(
                SuccessAfterRetryCall(10, 7 * 5000 + 4000 + 2000 + 1000)
            ),
            expectBeforeRetryCalls = listOf(
                BeforeRetryCall(TestException("Error1"), 0, 0, 0),
                BeforeRetryCall(TestException("Error2"), 1, 1, 1000),
                BeforeRetryCall(TestException("Error3"), 2, 2, 2000 + 1000),
                BeforeRetryCall(TestException("Error4"), 3, 3, 4000 + 2000 + 1000),
                // Delay is random due to jitter, but once it reached maxDelay it should be around this value
                BeforeRetryCall(TestException("Error5"), 4, 4, 5000 + 4000 + 2000 + 1000),
                BeforeRetryCall(TestException("Error6"), 5, 5, 2 * 5000 + 4000 + 2000 + 1000),
                BeforeRetryCall(TestException("Error7"), 6, 6, 3 * 5000 + 4000 + 2000 + 1000),
                BeforeRetryCall(TestException("Error8"), 7, 7, 4 * 5000 + 4000 + 2000 + 1000),
                BeforeRetryCall(TestException("Error9"), 8, 8, 5 * 5000 + 4000 + 2000 + 1000),
                BeforeRetryCall(TestException("Error10"), 9, 9, 6 * 5000 + 4000 + 2000 + 1000),
            )
        )
    }

    @Test
    fun should_never_have_delay_smaller_than_min_and_bigger_than_max() = runTest {
        var delayTimes = listOf<Long>()
        failingNTimesFlow(100).retryBackoff(
            minDelay = 2.seconds + 750.milliseconds,
            maxDelay = 4.seconds + 500.milliseconds,
            maxAttempts = 100,
            jitterFactor = 1.0,
            beforeRetry = { _, _, attempt ->
                if (attempt != 0) { // The first time millis is not a delay
                    val delay = this.currentTime - delayTimes.sum()
                    delayTimes += delay
                }
            }
        ).catch { /* no-op */ }
            .collect()

        assertEquals(99, delayTimes.size)
        assertTrue("All delay times should be between 2750 and 4500 ms, those outside the limit are ${delayTimes.filter { it !in 2750..4500 }}") {
            delayTimes.all { it in 2750..4500 }
        }
    }

    @Test
    fun should_retry_until_max_attempts_reached() = runTest {
        failingNTimesFlow(10).testBackoffRetry(
            testScope = this,
            minDelay = 2.seconds,
            maxDelay = 10.seconds,
            maxAttempts = 4,
            jitterFactor = 0.0,
            expectResult = Result.failure(TestException("Error5")),
            expectSuccessAfterRetry = listOf(),
            expectRetriesExhaustedCalls = listOf(
                RetryExhaustedCall(TestException("Error5"), 24000),
            ),
            expectBeforeRetryCalls = listOf(
                BeforeRetryCall(TestException("Error1"), 0, 0, 0),
                BeforeRetryCall(TestException("Error2"), 1, 1, 2000),
                BeforeRetryCall(TestException("Error3"), 2, 2, 6000),
                BeforeRetryCall(TestException("Error4"), 3, 3, 14000),
            )
        )
    }

    @Test
    fun should_add_random_jitter() = runTest {
        failingNTimesFlow(10).testBackoffRetry(
            testScope = this,
            minDelay = 1.seconds,
            maxDelay = 5.seconds,
            maxAttempts = 10,
            jitterFactor = 0.5,
            random = Random(12345),
            expectResult = Result.success(listOf("ABC")),
            expectRetriesExhaustedCalls = listOf(),
            expectSuccessAfterRetry = listOf(SuccessAfterRetryCall(attempts=10, time=36192)),
            expectBeforeRetryCalls = listOf(
                BeforeRetryCall(TestException("Error1"), 0, 0, 0),
                BeforeRetryCall(TestException("Error2"), 1, 1, 1385),
                BeforeRetryCall(TestException("Error3"), 2, 2, 4056),
                BeforeRetryCall(TestException("Error4"), 3, 3, 8020),
                BeforeRetryCall(TestException("Error5"), 4, 4, 12565),
                BeforeRetryCall(TestException("Error6"), 5, 5, 16693),
                BeforeRetryCall(TestException("Error7"), 6, 6, 20180),
                BeforeRetryCall(TestException("Error8"), 7, 7, 24589),
                BeforeRetryCall(TestException("Error9"), 8, 8, 28439),
                BeforeRetryCall(TestException("Error10"), 9, 9, 33133),
            )
        )
    }

    suspend fun <T> Flow<T>.testBackoffRetry(
        testScope: TestScope,
        minDelay: Duration,
        maxDelay: Duration = Duration.INFINITE,
        backoffFactor: Double = 2.0,
        maxAttempts: Int = Int.MAX_VALUE,
        transient: Boolean = true,
        jitterFactor: Double = 0.1,
        random: Random = Random,
        expectResult: Result<List<T>>,
        expectSuccessAfterRetry: List<SuccessAfterRetryCall>,
        expectRetriesExhaustedCalls: List<RetryExhaustedCall>,
        expectBeforeRetryCalls: List<BeforeRetryCall>,
        elementsToExpect: Int = 1,
    ) {
        var beforeRetryCalls = listOf<BeforeRetryCall>()
        var retriesExhaustedCalls = listOf<RetryExhaustedCall>()
        var successAfterRetryCalls = listOf<SuccessAfterRetryCall>()
        val result = runCatching {
            this.retryBackoff(
                minDelay = minDelay,
                maxDelay = maxDelay,
                maxAttempts = maxAttempts,
                backoffFactor = backoffFactor,
                transient = transient,
                jitterFactor = jitterFactor,
                random = random,
                successAfterRetry = { attempts ->
                    successAfterRetryCalls += SuccessAfterRetryCall(attempts, testScope.currentTime)
                },
                beforeRetry = { cause, currentAttempts, totalAttempts ->
                    beforeRetryCalls += BeforeRetryCall(cause, currentAttempts, totalAttempts, testScope.currentTime)
                },
                retriesExhausted = {
                    retriesExhaustedCalls += RetryExhaustedCall(it, testScope.currentTime)
                }
            ).take(elementsToExpect)
                .toList()
        }
        assertEquals(expectResult, result)
        assertEquals(expectRetriesExhaustedCalls, retriesExhaustedCalls)
        assertEquals(expectBeforeRetryCalls, beforeRetryCalls)
        assertEquals(expectSuccessAfterRetry, successAfterRetryCalls)
    }

    data class BeforeRetryCall(val cause: Throwable, val currentAttempts: Int, val totalAttempts: Int, val time: Long)
    data class RetryExhaustedCall(val cause: Throwable, val time: Long)
    data class SuccessAfterRetryCall(val attempts: Int, val time: Long)

    data class TestException(override val message: String) : Exception(message)
}