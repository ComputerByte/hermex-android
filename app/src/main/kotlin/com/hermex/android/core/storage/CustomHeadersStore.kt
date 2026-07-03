package com.hermex.android.core.storage

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** [snapshot] must be synchronous and cheap -- [com.hermex.android.core.network.NetworkModule]'s
 * OkHttp interceptor calls it on every outgoing request, and OkHttp interceptors run on whatever
 * thread issued the call, never inside a coroutine. */
interface CustomHeadersStore {
    val headers: StateFlow<List<CustomHttpHeader>>
    fun snapshot(): List<CustomHttpHeader>
    suspend fun load(): List<CustomHttpHeader>
    suspend fun save(headers: List<CustomHttpHeader>)
}

/** Default used wherever no real store is wired in (most unit tests construct `NetworkModule`
 * without exercising custom headers at all) -- always empty, every write is a no-op. */
object NoOpCustomHeadersStore : CustomHeadersStore {
    override val headers: StateFlow<List<CustomHttpHeader>> =
        MutableStateFlow<List<CustomHttpHeader>>(emptyList()).asStateFlow()

    override fun snapshot(): List<CustomHttpHeader> = emptyList()
    override suspend fun load(): List<CustomHttpHeader> = emptyList()
    override suspend fun save(headers: List<CustomHttpHeader>) = Unit
}
