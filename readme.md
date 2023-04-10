[![](https://jitpack.io/v/MarcinMoskala/kotlin-coroutines-recipes.svg)](https://jitpack.io/#MarcinMoskala/kotlin-coroutines-recipes)

# Kotlin Coroutines Recipes

This repository contains Kotlin Coroutines functions that are useful in everyday projects. Feel free to use them in your projects, both as a dependency, or by copy-pasting my code. Also feel free to share your own functions, that you find useful, but remamber that they need to be properly tested.

## Dependency

[![](https://jitpack.io/v/MarcinMoskala/kotlin-coroutines-recipes.svg)](https://jitpack.io/#MarcinMoskala/kotlin-coroutines-recipes)

Add the dependency in your module `build.gradle(.kts)`:

```
// build.gradle / build.gradle.kts
dependencies {
    implementation("com.github.MarcinMoskala.kotlin-coroutines-recipes:kotlin-coroutines-recipes:<version>")
}
```

Add it in your root `build.gradle(.kts)` at the repositories block:

```
// build.gradle
repositories {
    // ...
    maven { url 'https://jitpack.io' }
}

// build.gradle.kts
repositories {
    // ...
    maven("https://jitpack.io")
}
```

This library can currently be used on Kotlin/JVM, Kotlin/JS and in common modules

## Recipes

### `mapAsync`

Function `mapAsync` allows you to concurrently map a collection of elements to a collection of results of suspending functions. It is useful when you want to run multiple suspending functions in parallel. You can limit the number of concurrent operations by passing `concurrency` parameter.

```kotlin
suspend fun getCourses(requestAuth: RequestAuth?, user: User): List<UserCourse> =
    courseRepository.getAllCourses()
        .mapAsync { composeUserCourse(user, it) }
        .filterNot { courseShouldBeHidden(user, it) }
        .sortedBy { it.state.ordinal }
```

See [implementation](https://github.com/MarcinMoskala/kotlin-coroutines-recipes/blob/master/src/commonMain/kotlin/mapAsync.kt).

### `retryWhen`

Function `retryWhen` allows you to retry an operation when a given predicate is true. It is useful when you want to retry an operation multiple times under certain conditions.

```kotlin
// Example
suspend fun checkConnection(): Boolean = retryWhen(
    predicate = { _, retries -> retries < 3 },
    operation = { api.connected() }
)
```

See [implementation](https://github.com/MarcinMoskala/kotlin-coroutines-recipes/blob/master/src/commonMain/kotlin/retryWhen.kt).

### `raceOf`

Function `raceOf` allows you to run multiple suspending functions in parallel and return the result of the first one that finishes. 

```kotlin
suspend fun fetchUserData(): UserData = raceOf(
    { service1.fetchUserData() },
    { service2.fetchUserData() }
)
```

See [implementation](https://github.com/MarcinMoskala/kotlin-coroutines-recipes/blob/master/src/commonMain/kotlin/raceOf.kt).

## `suspendLazy`

Function `suspendLazy` allows you to create a function that represents a lazy suspending property. It is useful when you want to create a suspending property that is initialized only once, but you don't want to block the thread that is accessing it.

```kotlin
val userData: suspend () -> UserData = suspendLazy {
    service.fetchUserData()
}

suspend fun getUserData(): UserData = userData()
```

See [implementation](https://github.com/MarcinMoskala/kotlin-coroutines-recipes/blob/master/src/commonMain/kotlin/suspendLazy.kt).

## `ConnectionPool`

Class `ConnectionPool` allows you to reuse the same flow connections created based on a key. It makes sure there is no more than a single active connection for a given key. It is useful when you want to reuse the same flow for multiple subscribers.

```kotlin
val connectionPool = ConnectionPool<String, Flow<String>>(scope) { userId ->
    observeMessages(userId)
}

fun observeMessages(userId: String): Flow<String> = connectionPool.getConnection(userId)
```

See [implementation](https://github.com/MarcinMoskala/kotlin-coroutines-recipes/blob/master/src/commonMain/kotlin/ConnectionPool.kt).
