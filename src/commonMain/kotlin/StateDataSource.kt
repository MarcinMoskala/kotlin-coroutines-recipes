import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.internal.SynchronizedObject
import kotlinx.coroutines.internal.synchronized
import kotlin.time.Duration

class StateDataSource<K, V>(
    private val scope: CoroutineScope,
    private val initial: V,
    private val replayExpiration: Duration = Duration.INFINITE,
    private val builder: (K) -> Flow<V>,
) {
    private val connections = mutableMapOf<K, StateFlow<V>>()

    @OptIn(InternalCoroutinesApi::class)
    private val lock = object : SynchronizedObject() {}
    
    @OptIn(InternalCoroutinesApi::class)
    fun get(key: K): StateFlow<V> = synchronized(lock) {
        connections.getOrPut(key) {
            builder(key).stateIn(
                scope = scope,
                initialValue = initial,
                started = SharingStarted.WhileSubscribed(
                    replayExpirationMillis = replayExpiration.inWholeMilliseconds,
                ),
            )
        }
    }
    
    @OptIn(InternalCoroutinesApi::class)
    fun all(): List<StateFlow<V>> = synchronized(lock) {
        connections.values.toList()
    }
}
