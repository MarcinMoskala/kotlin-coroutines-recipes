import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import recipes.ConnectionPool
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds

class ConnectionPoolTest {

    private val infiniteEverySecondProducerFlow = generateSequence(0) { it + 1 }
        .asFlow()
        .onEach { delay(1000) }

    @Test
    fun should_use_builder_to_create_a_flow() = runTest {
        val pool = ConnectionPool(scope = this) { key: String ->
            flowOf("1$key", "2$key", "3$key")
                .onEach { delay(1000) }
        }

        val flow: Flow<String> = pool.getConnection("ABC")

        assertFirstFlowElements(
            flow,
            1000L to "1ABC",
            2000L to "2ABC",
            3000L to "3ABC",
        )

        coroutineContext.cancelChildren()
    }

    @Test
    fun should_reuse_the_same_flow_for_the_same_key() = runTest {
        var createdFlows = 0
        val pool = ConnectionPool(scope = this) { _: String ->
            createdFlows++
            infiniteEverySecondProducerFlow
        }

        pool.getConnection("A").launchIn(this)
        pool.getConnection("A").launchIn(this)
        pool.getConnection("B").launchIn(this)
        pool.getConnection("A").launchIn(this)
        pool.getConnection("B").launchIn(this)
        pool.getConnection("C").launchIn(this)

        delay(4000)

        assertEquals(3, createdFlows)

        coroutineContext.cancelChildren()
    }

    @Test
    fun should_close_connection_when_there_are_no_active_listeners() = runTest {
        var openConnections = 0
        val pool = ConnectionPool(scope = this) { _: String ->
            infiniteEverySecondProducerFlow
                .onStart { openConnections++ }
                .onCompletion { openConnections-- }
        }

        val listenerA1 = pool.getConnection("A").launchIn(this)
        val listenerA2 = pool.getConnection("A").launchIn(this)
        val listenerB1 = pool.getConnection("B").launchIn(this)
        val listenerB2 = pool.getConnection("B").launchIn(this)
        val listenerC1 = pool.getConnection("C").launchIn(this)

        delay(3000)

        assertEquals(3, openConnections)

        listenerB2.cancel()
        listenerC1.cancel() // The last cancelled here
        delay(1) // TODO: Close after some time?

        assertEquals(2, openConnections)

        listenerA2.cancel()
        listenerB1.cancel() // The last cancelled here
        delay(1)

        assertEquals(1, openConnections)

        listenerA1.cancel()
        delay(1)

        assertEquals(0, openConnections)

        coroutineContext.cancelChildren()
    }

    @Test
    fun should_reply_last_value() = runTest {
        val pool = ConnectionPool(
            scope = this,
            replayExpiration = 1000.milliseconds,
            replay = 1
        ) { _: String ->
            infiniteEverySecondProducerFlow
        }

        pool.getConnection("A").launchIn(this)

        delay(10500)

        var valueProduced: Int? = null
        pool.getConnection("A")
            .onEach { valueProduced = it }
            .launchIn(this)

        delay(1)
        assertEquals(9, valueProduced)

        coroutineContext.cancelChildren()
    }

    @Test
    fun should_keep_reply_for_specified_time() = runTest {
        val replayExpirationMillis = 5000.milliseconds
        val pool = ConnectionPool(
            scope = this,
            replayExpiration = replayExpirationMillis,
            replay = 1
        ) { _: String ->
            infiniteEverySecondProducerFlow
        }

        val job = launch {
            pool.getConnection("A").collect()
        }

        delay(10500)

        job.cancel()
        delay(replayExpirationMillis)

        var valueProduced: Int? = null
        pool.getConnection("A")
            .onEach { valueProduced = it }
            .launchIn(this)

        delay(1)
        assertEquals(9, valueProduced)

        coroutineContext.cancelChildren()
    }

    @Test
    fun should_not_keep_reply_for_longer_than_specified_time() = runTest {
        val replayExpiration = 5000.milliseconds
        val pool = ConnectionPool(
            scope = this,
            replayExpiration = replayExpiration,
        ) { _: String ->
            infiniteEverySecondProducerFlow
        }

        val job = launch {
            pool.getConnection("A").collect()
        }

        delay(10500)

        job.cancel()
        delay(replayExpiration + 1.milliseconds)

        var valueProduced: Int? = null
        pool.getConnection("A")
            .onEach { valueProduced = it }
            .launchIn(this)

        delay(1)
        assertNull(valueProduced)

        coroutineContext.cancelChildren()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
suspend fun <T> TestScope.assertFirstFlowElements(flow: Flow<T>, vararg elements: Pair<Long, T>) {
    assertEquals(
        elements.toList(),
        flow.map { Pair(currentTime, it) }.take(elements.size).toList()
    )
}