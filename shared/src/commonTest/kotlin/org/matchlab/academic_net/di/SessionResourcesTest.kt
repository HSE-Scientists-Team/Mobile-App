package org.matchlab.academic_net.di

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** What a session graph lets go of when the session ends: its scope and its resources, once, in order, whatever goes wrong. */
@OptIn(ExperimentalAtomicApi::class)
class SessionResourcesTest {
    private class Resource(
        private val name: String,
        private val journal: MutableList<String>,
        private val failure: Throwable? = null,
    ) : AutoCloseable {
        val closes = AtomicInt(0)

        override fun close() {
            closes.fetchAndAdd(1)
            journal += name
            failure?.let { throw it }
        }
    }

    private val journal = mutableListOf<String>()

    private fun resource(name: String, failure: Throwable? = null) = Resource(name, journal, failure)

    @Test
    fun aNewSessionIsOpenAndHoldsNothing() {
        val resources = SessionResources()

        assertFalse(resources.isClosed)
        assertEquals(0, resources.openCount)
        assertTrue(resources.scope.coroutineContext[kotlinx.coroutines.Job]!!.isActive)
    }

    @Test
    fun aRegisteredResourceIsHandedBackAndCounted() {
        val resources = SessionResources()
        val first = resource("a")

        assertSame(first, resources.closeOnEnd(first))
        resources.closeOnEnd(resource("b"))

        assertEquals(2, resources.openCount)
        assertEquals(emptyList(), journal, "nothing is closed before the session ends")
    }

    @Test
    fun theResourcesAreClosedLastRegisteredFirst() {
        val resources = SessionResources()
        resources.closeOnEnd(resource("first"))
        resources.closeOnEnd(resource("second"))
        resources.closeOnEnd(resource("third"))

        resources.close()

        assertEquals(listOf("third", "second", "first"), journal)
        assertTrue(resources.isClosed)
        assertEquals(0, resources.openCount)
    }

    @Test
    fun theScopeIsCancelledWhenTheSessionEnds() {
        val resources = SessionResources()
        val job = resources.scope.coroutineContext[kotlinx.coroutines.Job]!!

        resources.close()

        assertTrue(job.isCancelled)
    }

    @Test
    fun theScopeIsCancelledBeforeTheResourcesAreClosed() {
        val resources = SessionResources()
        val job = resources.scope.coroutineContext[kotlinx.coroutines.Job]!!
        var cancelledWhenClosing: Boolean? = null
        resources.closeOnEnd(
            object : AutoCloseable {
                override fun close() {
                    cancelledWhenClosing = job.isCancelled
                }
            },
        )

        resources.close()

        // Nothing may still wait for an answer of a client that is being closed.
        assertEquals(true, cancelledWhenClosing)
    }

    @Test
    fun closingTwiceClosesEachResourceOnce() {
        val resources = SessionResources()
        val only = resource("only")
        resources.closeOnEnd(only)

        resources.close()
        resources.close()

        assertEquals(1, only.closes.load())
        assertEquals(listOf("only"), journal)
    }

    @Test
    fun aResourceRegisteredAfterTheEndIsClosedAtOnceAndNotKept() {
        val resources = SessionResources()
        resources.close()
        val late = resource("late")

        val returned = resources.closeOnEnd(late)

        assertSame(late, returned)
        assertEquals(1, late.closes.load())
        assertEquals(0, resources.openCount)
        assertEquals(listOf("late"), journal)
    }

    @Test
    fun aResourceThatFailsToCloseDoesNotStopTheOthers() {
        val resources = SessionResources()
        val before = resource("before")
        val broken = resource("broken", IllegalStateException("cannot close"))
        val after = resource("after")
        resources.closeOnEnd(before)
        resources.closeOnEnd(broken)
        resources.closeOnEnd(after)

        val thrown = assertFailsWith<IllegalStateException> { resources.close() }

        assertEquals("cannot close", thrown.message)
        assertEquals(listOf("after", "broken", "before"), journal, "the ones after the broken one are closed too")
        assertEquals(1, before.closes.load())
        assertTrue(resources.isClosed)
    }

    @Test
    fun theFirstFailureIsThrownAndTheLaterOnesAreAttachedToIt() {
        val resources = SessionResources()
        resources.closeOnEnd(resource("c", IllegalStateException("third failure")))
        resources.closeOnEnd(resource("b", IllegalArgumentException("second failure")))
        resources.closeOnEnd(resource("a", IllegalStateException("first failure")))

        val thrown = assertFailsWith<IllegalStateException> { resources.close() }

        // Closed in the order a, b, c: the failure of "a" is the first.
        assertEquals("first failure", thrown.message)
        assertEquals(listOf("second failure", "third failure"), thrown.suppressedExceptions.map { it.message })
    }

    @Test
    fun aFailureIsThrownOnceAndASecondCloseIsQuiet() {
        val resources = SessionResources()
        resources.closeOnEnd(resource("broken", IllegalStateException("cannot close")))

        assertFailsWith<IllegalStateException> { resources.close() }
        resources.close()

        assertEquals(listOf("broken"), journal)
    }

    @Test
    fun aLateResourceThatFailsToCloseThrowsToWhoeverRegisteredIt() {
        val resources = SessionResources()
        resources.close()

        assertFailsWith<IllegalStateException> {
            resources.closeOnEnd(resource("late", IllegalStateException("cannot close")))
        }
        assertEquals(0, resources.openCount)
    }

    @Test
    fun registeringWhileTheSessionEndsFromAnotherThreadLosesNothing() = runTest {
        val resources = SessionResources()
        val all = List(300) { Resource("r$it", mutableListOf()) }

        withContext(Dispatchers.Default) {
            val registering = all.map { resource -> launch { resources.closeOnEnd(resource) } }
            val ending = launch { resources.close() }
            (registering + ending).joinAll()
        }

        // Each one was closed by the end of the session or, if it came later, at once: exactly once either way.
        assertTrue(all.all { it.closes.load() == 1 })
        assertEquals(0, resources.openCount)
        assertTrue(resources.isClosed)
    }

    @Test
    fun endingTheSessionFromSeveralThreadsClosesEachResourceOnce() = runTest {
        val resources = SessionResources()
        val all = List(50) { Resource("r$it", mutableListOf()) }
        all.forEach { resources.closeOnEnd(it) }

        withContext(Dispatchers.Default) {
            List(8) { launch { resources.close() } }.joinAll()
        }

        assertTrue(all.all { it.closes.load() == 1 })
    }
}
