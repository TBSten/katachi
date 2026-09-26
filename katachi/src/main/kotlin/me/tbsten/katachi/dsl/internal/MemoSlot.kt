package me.tbsten.katachi.dsl.internal

/**
 * One key of `FileConstraintSubject.memo`'s scratch map: created at most once, even when the
 * constraints asking for it run on several threads.
 *
 * Each caller brings its own `create`, and the one that gets the lock first runs it; the rest
 * wait and receive that value. A `create` that throws leaves the slot empty, so the next caller
 * runs *its own* `create` -- what the single-threaded map did, where a failed create stored
 * nothing. A per-key lock rather than `ConcurrentHashMap.computeIfAbsent`, because a `create`
 * that asks `memo` for another key would otherwise modify the map from inside its own compute.
 */
internal class MemoSlot {
    @Volatile
    private var value: Any? = null

    fun getOrCreate(create: () -> Any): Any {
        value?.let { return it }
        return synchronized(this) {
            value ?: create().also { value = it }
        }
    }
}
