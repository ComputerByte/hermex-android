package com.hermex.android.core.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for [EncryptedCookieStore] backed by a real Android [Context].
 *
 * These tests verify the full [CookieStore] contract: save / load / clear round-trips,
 * per-server isolation, and transparent migration from [InMemoryCookieStore] (as a
 * stand-in for the legacy [DataStoreCookieStore]).
 *
 * Note: The actual migration from [DataStoreCookieStore] is tested via the public [load]
 * path -- [EncryptedCookieStore] passes a [DataStoreCookieStore] as [legacyCookieStore],
 * and the first [load] triggers [EncryptedCookieStore.migrateIfNeeded] internally.
 */
@RunWith(AndroidJUnit4::class)
class EncryptedCookieStoreTest {

    private val appContext: Context = ApplicationProvider.getApplicationContext()

    /** Unique server ID per test to avoid cross-test pollution. */
    private val serverId = "test_server_${java.util.UUID.randomUUID().toString().take(8)}"

    private lateinit var store: EncryptedCookieStore

    @Before
    fun setUp() {
        // Clear any pre-existing state for this serverId by instantiating and clearing.
        val cleaner = EncryptedCookieStore(appContext, serverId)
        runBlocking { cleaner.clear() }
    }

    @After
    fun tearDown() {
        if (::store.isInitialized) runBlocking { store.clear() }
    }

    @Test
    fun saveAndLoad_roundTrips() = runBlocking {
        store = EncryptedCookieStore(appContext, serverId)
        store.save("cookie_value_abc")
        val loaded = store.load()
        assertEquals("cookie_value_abc", loaded)
    }

    @Test
    fun load_returnsNull_whenNothingSaved() = runBlocking {
        store = EncryptedCookieStore(appContext, serverId)
        val loaded = store.load()
        assertNull(loaded)
    }

    @Test
    fun clear_removesValue() = runBlocking {
        store = EncryptedCookieStore(appContext, serverId)
        store.save("value_to_clear")
        assertEquals("value_to_clear", store.load())
        store.clear()
        val afterClear = store.load()
        assertNull(afterClear)
    }

    @Test
    fun twoServerIds_doNotShareCookies() = runBlocking {
        val serverA = "test_A_${java.util.UUID.randomUUID().toString().take(8)}"
        val serverB = "test_B_${java.util.UUID.randomUUID().toString().take(8)}"
        val storeA = EncryptedCookieStore(appContext, serverA)
        val storeB = EncryptedCookieStore(appContext, serverB)
        try {
            storeA.save("cookie_for_A")
            assertEquals("cookie_for_A", storeA.load())
            // B should see nothing (different server, different key)
            assertNull(storeB.load())
            // Save B, confirm B sees its own value and A still sees A's value
            storeB.save("cookie_for_B")
            assertEquals("cookie_for_B", storeB.load())
            assertEquals("cookie_for_A", storeA.load())
        } finally {
            storeA.clear()
            storeB.clear()
        }
    }

    @Test
    fun migrateFrom_copiesLegacyValue() = runBlocking {
        val legacy = InMemoryCookieStore()
        legacy.save("legacy_cookie_value")

        store = EncryptedCookieStore(appContext, serverId, legacy)
        val loaded = store.load()

        assertNotNull("Encrypted store should have migrated legacy value", loaded)
        assertEquals("legacy_cookie_value", loaded)

        // After migration, legacy store should be empty (only after encrypted save succeeded)
        val legacyAfter = legacy.load()
        assertNull("Legacy store should be cleared after successful migration", legacyAfter)
    }

    @Test
    fun migrateFrom_doesNotRunTwice() = runBlocking {
        val legacy = InMemoryCookieStore()
        legacy.save("migration_value")

        store = EncryptedCookieStore(appContext, serverId, legacy)
        assertEquals("migration_value", store.load())

        // Legacy is now empty after first migration
        // Second load should NOT read from legacy (encrypted store already has the value)
        val legacyAfter = legacy.load()
        assertNull(legacyAfter)

        // Update encrypted store to a different value
        store.save("updated_value")
        assertEquals("updated_value", store.load())

        // Legacy is still empty, proving migration doesn't run again
        assertNull(legacy.load())
    }

    @Test
    fun migrateFrom_noops_whenLegacyIsNull() = runBlocking {
        store = EncryptedCookieStore(appContext, serverId, null)
        val loaded = store.load()
        assertNull(loaded)
    }

    @Test
    fun migrateFrom_noops_whenLegacyEmpty() = runBlocking {
        val legacy = InMemoryCookieStore()
        // Don't save anything to legacy
        store = EncryptedCookieStore(appContext, serverId, legacy)
        val loaded = store.load()
        assertNull(loaded)
    }

    @Test
    fun migrateFrom_doesNotOverwriteExistingEncryptedValue() = runBlocking {
        // Save directly to encrypted store first
        val preMigrateStore = EncryptedCookieStore(appContext, serverId)
        preMigrateStore.save("existing_encrypted_value")

        // Now create a store with a legacy cookie that conflicts
        val legacy = InMemoryCookieStore()
        legacy.save("legacy_value_should_not_win")

        store = EncryptedCookieStore(appContext, serverId, legacy)
        val loaded = store.load()

        // Encrypted store already had the value, so migration should not overwrite it
        assertEquals("existing_encrypted_value", loaded)
    }
}
