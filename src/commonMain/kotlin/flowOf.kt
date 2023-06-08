package recipes

import kotlinx.coroutines.flow.flow

fun <T> flowOf(producer: suspend () -> T) = flow { emit(producer()) }
