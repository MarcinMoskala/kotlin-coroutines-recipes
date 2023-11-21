import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.internal.SynchronizedObject
import kotlinx.coroutines.internal.synchronized
import kotlin.time.Duration

class SharedDataSource<K, V>(
    private val scope: CoroutineScope,
    private val replayExpiration: Duration = Duration.INFINITE,
    private val replay: Int = 0,
    private val builder: (K) -> Flow<V>,
) {
    private val connections = mutableMapOf<K, SharedFlow<V>>()
    @OptIn(InternalCoroutinesApi::class)
    private val lock = object : SynchronizedObject() {}
    
    @OptIn(InternalCoroutinesApi::class)
    fun get(key: K): SharedFlow<V> = synchronized(lock) {
        connections.getOrPut(key) {
            builder(key).shareIn(
                scope,
                started = SharingStarted.WhileSubscribed(
                    replayExpirationMillis = replayExpiration.inWholeMilliseconds,
                ),
                replay = replay,
            )
        }
    }
}
