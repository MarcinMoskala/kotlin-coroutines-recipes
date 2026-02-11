import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class OnEachBatchTest {

    @Test
    fun `should invoke action for full batches`() = runTest {
        val batches = mutableListOf<List<Int>>()
        val result = flowOf(1, 2, 3, 4, 5, 6)
            .onEachBatch(3) { batches.add(it) }
            .toList()

        assertEquals(listOf(1, 2, 3, 4, 5, 6), result)
        assertEquals(listOf(listOf(1, 2, 3), listOf(4, 5, 6)), batches)
    }

    @Test
    fun `should invoke action for remaining elements at the end`() = runTest {
        val batches = mutableListOf<List<Int>>()
        val result = flowOf(1, 2, 3, 4, 5)
            .onEachBatch(3) { batches.add(it) }
            .toList()

        assertEquals(listOf(1, 2, 3, 4, 5), result)
        assertEquals(listOf(listOf(1, 2, 3), listOf(4, 5)), batches)
    }

    @Test
    fun `should handle empty flow`() = runTest {
        val batches = mutableListOf<List<Int>>()
        val result = emptyFlow<Int>()
            .onEachBatch(3) { batches.add(it) }
            .toList()

        assertEquals(emptyList(), result)
        assertEquals(emptyList<List<Int>>(), batches)
    }

    @Test
    fun `should invoke action for remaining elements when exception occurs`() = runTest {
        val batches = mutableListOf<List<Int>>()
        val flow = flow {
            emit(1)
            emit(2)
            throw RuntimeException("Test exception")
        }

        try {
            flow.onEachBatch(3) { batches.add(it) }.collect()
        } catch (e: RuntimeException) {
            assertEquals("Test exception", e.message)
        }

        assertEquals(listOf(listOf(1, 2)), batches)
    }

    @Test
    fun `should emit elements downstream immediately`() = runTest {
        val emitted = mutableListOf<Int>()
        val batches = mutableListOf<List<Int>>()
        
        flowOf(1, 2, 3)
            .onEachBatch(2) { batches.add(it) }
            .collect {
                emitted.add(it)
                if (it == 1) {
                    assertEquals(0, batches.size, "Batch should not be invoked yet")
                }
                if (it == 2) {
                    assertEquals(1, batches.size, "Batch should be invoked after 2nd element")
                    assertEquals(listOf(1, 2), batches[0])
                }
            }
        
        assertEquals(listOf(1, 2, 3), emitted)
        assertEquals(listOf(listOf(1, 2), listOf(3)), batches)
    }

    @Test
    fun `should flush remaining elements when downstream fails`() = runTest {
        val batches = mutableListOf<List<Int>>()
        val flow = flowOf(1, 2)
        
        assertFailsWith<RuntimeException> {
            flow.onEachBatch(3) { batches.add(it) }
                .collect {
                    if (it == 2) throw RuntimeException("Downstream failure")
                }
        }
        
        assertEquals(listOf(listOf(1, 2)), batches, "Should flush remaining elements even if downstream fails")
    }

    @Test
    fun `should flush remaining elements when cancelled`() = runTest {
        val batches = mutableListOf<List<Int>>()
        val flow = flow {
            emit(1)
            emit(2)
            delay(1000)
            emit(3)
        }

        val job = launch {
            flow.onEachBatch(3) { batches.add(it) }.collect()
        }

        delay(500)
        job.cancelAndJoin()

        assertEquals(listOf(listOf(1, 2)), batches, "Should flush remaining elements when cancelled")
    }

    @Test
    fun `should propagate exception from action`() = runTest {
        val flow = flowOf(1, 2, 3)
        val exception = assertFailsWith<RuntimeException> {
            flow.onEachBatch(2) { 
                if (it.contains(2)) throw RuntimeException("Action failure")
            }.collect()
        }
        
        assertEquals("Action failure", exception.message)
    }
}
