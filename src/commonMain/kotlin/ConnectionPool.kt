
package recipes

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.internal.SynchronizedObject
import kotlinx.coroutines.internal.synchronized
import kotlin.time.Duration

@OptIn(InternalCoroutinesApi::class)
class ConnectionPool<K, V>(
    private val scope: CoroutineScope,
    private val replay: Int = 0,
    private val stopTimeout: Duration = Duration.ZERO,
    private val replayExpiration: Duration = Duration.ZERO,
    private val builder: (K) -> Flow<V>,
) {
    private val connections = mutableMapOf<K, Flow<V>>()
    private val LOCK = SynchronizedObject()

    fun getConnection(key: K): Flow<V> = synchronized(LOCK) {
        connections.getOrPut(key) {
            builder(key).shareIn(
                scope,
                started = SharingStarted.WhileSubscribed(
                    stopTimeoutMillis =
                    stopTimeout.inWholeMilliseconds,
                    replayExpirationMillis =
                    replayExpiration.inWholeMilliseconds,
                ),
                replay = replay,
            )
        }
    }
}
