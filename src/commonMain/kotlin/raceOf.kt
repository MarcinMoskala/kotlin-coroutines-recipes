package recipes

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.selects.select

suspend fun <T> raceOf(
    racer: suspend CoroutineScope.() -> T,
    vararg racers: suspend CoroutineScope.() -> T
): T = coroutineScope {
    select {
        (listOf(racer) + racers).forEach { racer ->
            async { racer() }.onAwait {
                coroutineContext.job.cancelChildren()
                it
            }
        }
    }
}