import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds

class SharedDataSourceTest {

    private val infiniteEverySecondProducerFlow = generateSequence(0) { it + 1 }
        .asFlow()
        .onEach { delay(1000) }

    @Test
    fun should_use_builder_to_create_a_flow() = runTest {
        val pool = SharedDataSource(scope = backgroundScope) { key: String ->
            flowOf("1$key", "2$key", "3$key")
                .onEach { delay(1000) }
        }

        val flow: Flow<String> = pool.get("ABC")

        assertFirstFlowElements(
            flow,
            1000L to "1ABC",
            2000L to "2ABC",
            3000L to "3ABC",
        )
    }

    @Test
    fun should_reuse_the_same_flow_for_the_same_key() = runTest {
        var createdFlows = 0
        val pool = SharedDataSource(scope = backgroundScope) { _: String ->
            createdFlows++
            infiniteEverySecondProducerFlow
        }

        pool.get("A").launchIn(backgroundScope)
        pool.get("A").launchIn(backgroundScope)
        pool.get("B").launchIn(backgroundScope)
        pool.get("A").launchIn(backgroundScope)
        pool.get("B").launchIn(backgroundScope)
        pool.get("C").launchIn(backgroundScope)

        delay(4000)

        assertEquals(3, createdFlows)
    }

    @Test
    fun should_close_connection_when_there_are_no_active_listeners() = runTest {
        var openConnections = 0
        val pool = SharedDataSource(scope = backgroundScope) { _: String ->
            infiniteEverySecondProducerFlow
                .onStart { openConnections++ }
                .onCompletion { openConnections-- }
        }

        val listenerA1 = pool.get("A").launchIn(backgroundScope)
        val listenerA2 = pool.get("A").launchIn(backgroundScope)
        val listenerB1 = pool.get("B").launchIn(backgroundScope)
        val listenerB2 = pool.get("B").launchIn(backgroundScope)
        val listenerC1 = pool.get("C").launchIn(backgroundScope)

        delay(3000)

        assertEquals(3, openConnections)

        listenerB2.cancel()
        listenerC1.cancel()
        delay(1)

        assertEquals(2, openConnections)

        listenerA2.cancel()
        listenerB1.cancel() // The last cancelled here
        delay(1)

        assertEquals(1, openConnections)

        listenerA1.cancel()
        delay(1)

        assertEquals(0, openConnections)
    }

    @Test
    fun should_reply_last_value() = runTest {
        val pool = SharedDataSource(
            scope = backgroundScope,
            replayExpiration = 1000.milliseconds,
            replay = 1
        ) { _: String ->
            infiniteEverySecondProducerFlow
        }

        pool.get("A").launchIn(backgroundScope)

        delay(10500)

        var valueProduced: Int? = null
        pool.get("A")
            .onEach { valueProduced = it }
            .launchIn(backgroundScope)

        delay(1)
        assertEquals(9, valueProduced)
    }

    @Test
    fun should_keep_reply_for_specified_time() = runTest {
        val replayExpirationMillis = 5000.milliseconds
        val pool = SharedDataSource(
            scope = backgroundScope,
            replayExpiration = replayExpirationMillis,
            replay = 1
        ) { _: String ->
            infiniteEverySecondProducerFlow
        }

        val job = launch {
            pool.get("A").collect()
        }

        delay(10500)

        job.cancel()
        delay(replayExpirationMillis)

        var valueProduced: Int? = null
        pool.get("A")
            .onEach { valueProduced = it }
            .launchIn(backgroundScope)

        delay(1)
        assertEquals(9, valueProduced)
    }

    @Test
    fun should_not_keep_reply_for_longer_than_specified_time() = runTest {
        val replayExpiration = 5000.milliseconds
        val pool = SharedDataSource(
            scope = backgroundScope,
            replayExpiration = replayExpiration,
        ) { _: String ->
            infiniteEverySecondProducerFlow
        }

        val job = launch {
            pool.get("A").collect()
        }

        delay(10500)

        job.cancel()
        delay(replayExpiration + 1.milliseconds)

        var valueProduced: Int? = null
        pool.get("A")
            .onEach { valueProduced = it }
            .launchIn(backgroundScope)

        delay(1)
        assertNull(valueProduced)
    }

    @Test
    fun should_keep_lifecycle_order() = runTest {
        var eventsTrack = listOf<String>()
        val pool2 = SharedDataSource(backgroundScope) { key: String ->
            infiniteEverySecondProducerFlow
                .onStart { eventsTrack += "pool2 inner flow started for $key" }
                .onCompletion { eventsTrack += "pool2 inner flow completed for $key" }
        }

        val pool1 = SharedDataSource(backgroundScope) { key: String ->
            pool2.get("A")
                .onStart { eventsTrack += "pool1 inner flow started for $key" }
                .onCompletion { eventsTrack += "pool1 inner flow completed for $key" }
        }

        val job = pool1.get("A")
            .onStart { eventsTrack += "outer flow started" }
            .onCompletion { eventsTrack += "outer flow completed" }
            .launchIn(backgroundScope)

        delay(1)
        assertEquals(listOf(
            "outer flow started",
            "pool1 inner flow started for A",
            "pool2 inner flow started for A",
        ), eventsTrack)
        eventsTrack = emptyList()
        job.cancelAndJoin()
        delay(1)
        assertEquals(listOf(
            "outer flow completed",
            "pool1 inner flow completed for A",
            "pool2 inner flow completed for A",
        ), eventsTrack)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun <T> TestScope.assertFirstFlowElements(flow: Flow<T>, vararg elements: Pair<Long, T>) {
        assertEquals(
            elements.toList(),
            flow.map { Pair(currentTime, it) }.take(elements.size).toList()
        )
    }
}
