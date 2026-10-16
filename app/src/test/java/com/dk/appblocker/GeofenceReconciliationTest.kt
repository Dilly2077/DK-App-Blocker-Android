package com.dk.appblocker

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class GeofenceReconciliationTest {
    private val a = GeofenceSpec("a", 51.0, -.1, 150f)
    private val b = GeofenceSpec("b", 52.0, -.1, 200f)
    private class Backend(val failAdd: Boolean = false, val failRemove: Boolean = false) : GeofenceBackend {
        val calls = mutableListOf<String>()
        override suspend fun add(specs: List<GeofenceSpec>) { calls += "add:${specs.joinToString { it.id }}"; if (failAdd) error("registration failed") }
        override suspend fun remove(ids: List<String>) { calls += "remove:${ids.joinToString()}"; if (failRemove) error("removal failed") }
    }
    @Test fun unchangedRegionsAreNeverRemovedOrRegisteredAgain() = runBlocking {
        val backend = Backend(); GeofenceReconciliation.reconcile(listOf(a), listOf(a), emptySet(), backend)
        assertTrue(backend.calls.isEmpty())
    }
    @Test fun changedGeometryIsAddedBeforeObsoleteRemovalAndGetsANewEventId() = runBlocking {
        val changed = a.copy(latitude = 53.0)
        val backend = Backend(); GeofenceReconciliation.reconcile(listOf(a, b), listOf(changed, b), emptySet(), backend)
        assertEquals(listOf("add:a", "remove:${a.registrationId}"), backend.calls)
        assertNotEquals(a.registrationId, changed.registrationId)
    }
    @Test fun failedAddDoesNotRemoveExistingRegions() = runBlocking {
        val backend = Backend(failAdd = true)
        assertTrue(runCatching { GeofenceReconciliation.reconcile(listOf(a), listOf(b), emptySet(), backend) }.isFailure)
        assertEquals(listOf("add:b"), backend.calls)
    }
    @Test fun deletionRemovesOnlyObsoleteIdsIncludingLegacyRegistrations() = runBlocking {
        val backend = Backend(); GeofenceReconciliation.reconcile(listOf(a, b), listOf(b), setOf(a.id), backend)
        assertEquals(1, backend.calls.size)
        assertTrue(backend.calls.single().contains(a.registrationId)); assertFalse(backend.calls.single().contains(b.registrationId))
    }
    @Test fun capacityReplacementRemovesOnlyObsoleteBeforeAdding() = runBlocking {
        val previous = (1..100).map { a.copy(id = "id$it") }
        val changed = previous.dropLast(1) + previous.last().copy(latitude = 54.0)
        val backend = Backend(); GeofenceReconciliation.reconcile(previous, changed, emptySet(), backend)
        assertEquals("remove:${previous.last().registrationId}", backend.calls.first())
        assertEquals("add:id100", backend.calls.last())
    }
    @Test fun failureAtCapacityIsReportedWithoutTryingAnOverCapacityAdd() = runBlocking {
        val previous = (1..100).map { a.copy(id = "id$it") }
        val backend = Backend(failRemove = true)
        assertTrue(runCatching { GeofenceReconciliation.reconcile(previous, previous.dropLast(1) + b, emptySet(), backend) }.isFailure)
        assertEquals(1, backend.calls.size)
    }
    @Test fun failedRemovalRemainsRetryableAndOverLimitIsRejected() = runBlocking {
        val backend = Backend(failRemove = true)
        assertTrue(runCatching { GeofenceReconciliation.reconcile(listOf(a), emptyList(), emptySet(), backend) }.isFailure)
        val retry = Backend(); GeofenceReconciliation.reconcile(listOf(a), emptyList(), emptySet(), retry)
        assertEquals(1, retry.calls.size)
        assertTrue(runCatching { GeofenceReconciliation.reconcile(emptyList(), (0..100).map { a.copy(id = "$it") }, emptySet(), retry) }.isFailure)
    }
}
