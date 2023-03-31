import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import recipes.retryWhen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RetryWhenTest {

    @Test
    fun should_retry_n_times() {
        var startedTimes = 0

        runCatching {
            retryWhen(predicate = { _, retries -> retries < 3 }) {
                startedTimes++
                throw MyException
            }
        }

        assertEquals(4, startedTimes)
    }

    @Test
    fun should_retry_certain_exceptions() {
        val exceptions = mutableListOf(
            ExceptionToRetry1,
            ExceptionToRetry2,
            ExceptionToRetry3,
            MyException,
            ExceptionToRetry1,
        )

        val result = runCatching {
            retryWhen(predicate = { e, _ -> e is ExceptionToRetry }) {
                throw exceptions.removeFirst()
            }
        }

        assertEquals(1, exceptions.size)
        assertEquals(MyException, result.exceptionOrNull())
    }

    @Test
    fun should_wrap_exceptions() {
        val exceptions = mutableListOf(
            ExceptionToRetry1,
            ExceptionToRetry2,
            ExceptionToRetry3,
            MyException,
        )

        val result = runCatching {
            retryWhen(predicate = { e, _ -> e is ExceptionToRetry }) {
                throw exceptions.removeFirst()
            }
        }

        val e1 = result.exceptionOrNull()
        assertIs<MyException>(e1)
        val e2 = e1.suppressedExceptions.first()
        assertIs<ExceptionToRetry3>(e2)
        val e3 = e2.suppressedExceptions.first()
        assertIs<ExceptionToRetry2>(e3)
        val e4 = e3.suppressedExceptions.first()
        assertIs<ExceptionToRetry1>(e4)
    }

    @Test
    fun should_not_retry_cancellation() = runTest {
        withTimeout(100_000) {
            val job = launch {
                retryWhen(predicate = { _, _ -> true }) {
                    delay(1000)
                }
            }
            delay(100)
            job.cancelAndJoin()
        }
    }

    @Test
    fun should_predicate_be_executed_after_fail() {
        val eventsSequence = mutableListOf<Event>()
        runCatching {
            eventsSequence += Started
            retryWhen(predicate = { _, retries ->
                eventsSequence += RetryPredicateCalled
                retries < 3
            }) {
                eventsSequence += BodyExecutedCalled
                throw MyException
            }
            eventsSequence += Ended
        }

        val expectedSequence = listOf(
            Started,
            BodyExecutedCalled,
            RetryPredicateCalled,
            BodyExecutedCalled,
            RetryPredicateCalled,
            BodyExecutedCalled,
            RetryPredicateCalled,
            BodyExecutedCalled,
        )
        assertEquals(expectedSequence, expectedSequence)
    }

    abstract class Event
    object Started: Event()
    object Ended: Event()
    object RetryPredicateCalled: Event()
    object BodyExecutedCalled: Event()

    private object MyException : Exception("Some message")

    open class ExceptionToRetry : Exception()
    object ExceptionToRetry1 : ExceptionToRetry()
    object ExceptionToRetry2 : ExceptionToRetry()
    object ExceptionToRetry3 : ExceptionToRetry()
}