package me.tbsten.katachi.dsl.internal

// Per-key lock, create runs at most once across threads; a throwing create leaves the slot empty so the next
// caller runs its own.
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
