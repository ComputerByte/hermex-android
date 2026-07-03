package com.hermex.android.core.network

import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PersistentCookieJarTest {
    @Test
    fun `cookie saved from a response is returned for a matching subsequent request`() {
        val jar = PersistentCookieJar(FakeCookieStore())
        val url = "https://hermes.example.com/api/sessions".toHttpUrl()
        val cookie = Cookie.Builder()
            .name("session")
            .value("abc123")
            .domain("hermes.example.com")
            .path("/")
            .expiresAt(System.currentTimeMillis() + 60_000)
            .httpOnly()
            .build()

        jar.saveFromResponse(url, listOf(cookie))
        val loaded = jar.loadForRequest(url)

        assertEquals(1, loaded.size)
        assertEquals("abc123", loaded.first().value)
    }

    @Test
    fun `cookie for one host is never returned for a different host`() {
        val jar = PersistentCookieJar(FakeCookieStore())
        val hostA = "https://hermes-a.example.com/".toHttpUrl()
        val hostB = "https://hermes-b.example.com/".toHttpUrl()
        val cookie = Cookie.Builder()
            .name("session")
            .value("only-for-a")
            .domain("hermes-a.example.com")
            .path("/")
            .expiresAt(System.currentTimeMillis() + 60_000)
            .build()

        jar.saveFromResponse(hostA, listOf(cookie))

        assertEquals(1, jar.loadForRequest(hostA).size)
        assertTrue(jar.loadForRequest(hostB).isEmpty())
    }

    @Test
    fun `a new jar instance backed by the same store rehydrates persisted cookies`() {
        val store = FakeCookieStore()
        val url = "https://hermes.example.com/".toHttpUrl()
        val cookie = Cookie.Builder()
            .name("session")
            .value("persisted")
            .domain("hermes.example.com")
            .path("/")
            .expiresAt(System.currentTimeMillis() + 60_000)
            .build()

        val firstJar = PersistentCookieJar(store)
        firstJar.saveFromResponse(url, listOf(cookie))
        // saveFromResponse persists asynchronously on an IO-dispatcher scope; give it a moment.
        runBlocking { kotlinx.coroutines.delay(200) }

        val secondJar = PersistentCookieJar(store)
        val reloaded = secondJar.loadForRequest(url)

        assertEquals(1, reloaded.size)
        assertEquals("persisted", reloaded.first().value)
    }

    @Test
    fun `expired cookies are not returned`() {
        val jar = PersistentCookieJar(FakeCookieStore())
        val url = "https://hermes.example.com/".toHttpUrl()
        val expired = Cookie.Builder()
            .name("session")
            .value("stale")
            .domain("hermes.example.com")
            .path("/")
            .expiresAt(System.currentTimeMillis() - 1_000)
            .build()

        jar.saveFromResponse(url, listOf(expired))

        assertTrue(jar.loadForRequest(url).isEmpty())
    }

    @Test
    fun `clear removes all cookies`() {
        val jar = PersistentCookieJar(FakeCookieStore())
        val url = "https://hermes.example.com/".toHttpUrl()
        val cookie = Cookie.Builder()
            .name("session")
            .value("abc123")
            .domain("hermes.example.com")
            .path("/")
            .expiresAt(System.currentTimeMillis() + 60_000)
            .build()
        jar.saveFromResponse(url, listOf(cookie))

        jar.clear()

        assertTrue(jar.loadForRequest(url).isEmpty())
    }
}
