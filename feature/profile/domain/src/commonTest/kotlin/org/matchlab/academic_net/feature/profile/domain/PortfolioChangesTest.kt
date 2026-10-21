package org.matchlab.academic_net.feature.profile.domain

import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * What saving a work or a document does: when it asks the server at all, in which order, and what a failure of the
 * second request means for the first. The record is a plain string here, the rule does not look inside.
 */
class PortfolioChangesTest {
    private val base = "base"
    private val saved = "saved"

    private class Calls {
        val order = mutableListOf<String>()
    }

    private suspend fun save(
        changed: Boolean,
        send: Boolean,
        sendsForVerification: Boolean = true,
        update: EditOutcome<String> = EditOutcome.Done("saved"),
        submit: suspend () -> EditOutcome<Unit> = { EditOutcome.Done(Unit) },
        calls: Calls = Calls(),
        updateFailure: Throwable? = null,
    ): PortfolioChange<String> = savePortfolioItem(
        base = "base",
        changed = changed,
        send = send,
        sendsForVerification = sendsForVerification,
        update = {
            calls.order += "update"
            updateFailure?.let { throw it }
            update
        },
        submit = {
            calls.order += "submit"
            submit()
        },
    )

    // ---- nothing to do ----

    @Test
    fun nothingChangedAndNothingToSendAsksTheServerNothing() = runTest {
        val calls = Calls()

        val result = save(changed = false, send = false, calls = calls)

        assertEquals(PortfolioChange.Saved(base, sent = false), result)
        assertEquals(emptyList(), calls.order)
    }

    @Test
    fun nothingChangedAndAskedToSendARecordWithNothingToVerifyAsksNothing() = runTest {
        val calls = Calls()

        val result = save(changed = false, send = true, sendsForVerification = false, calls = calls)

        assertEquals(PortfolioChange.Saved(base, sent = false), result)
        assertEquals(emptyList(), calls.order)
    }

    // ---- a change ----

    @Test
    fun aChangeIsSavedAndNotSentWhenNobodyAskedToSend() = runTest {
        val calls = Calls()

        val result = save(changed = true, send = false, calls = calls)

        assertEquals(PortfolioChange.Saved(saved, sent = false), result)
        assertEquals(listOf("update"), calls.order)
    }

    @Test
    fun aChangeThatIsSentIsSavedFirstAndThenSent() = runTest {
        val calls = Calls()

        val result = save(changed = true, send = true, calls = calls)

        assertEquals(PortfolioChange.Saved(saved, sent = true), result)
        assertEquals(listOf("update", "submit"), calls.order)
    }

    @Test
    fun aChangeWithNothingToVerifyIsSavedButNeverSent() = runTest {
        val calls = Calls()

        val result = save(changed = true, send = true, sendsForVerification = false, calls = calls)

        assertEquals(PortfolioChange.Saved(saved, sent = false), result)
        assertEquals(listOf("update"), calls.order)
    }

    @Test
    fun aSendThatDoesNotGoThroughAfterASaveLeavesTheRecordSaved() = runTest {
        for (outcome in listOf(EditOutcome.Conflict, EditOutcome.NotFound)) {
            val result = save(changed = true, send = true, submit = { outcome })
            assertEquals(PortfolioChange.Saved(saved, sent = false), result, "$outcome")
        }
    }

    @Test
    fun aSendThatFailsAfterASaveLeavesTheRecordSaved() = runTest {
        val result = save(changed = true, send = true, submit = { throw IllegalStateException("no network") })

        assertEquals(PortfolioChange.Saved(saved, sent = false), result)
    }

    @Test
    fun aCancellationDuringTheSendAfterASaveIsNotSwallowed() = runTest {
        assertFailsWith<CancellationException> {
            save(changed = true, send = true, submit = { throw CancellationException("screen left") })
        }
    }

    @Test
    fun aSaveThatFindsAConflictEndsThereAndSendsNothing() = runTest {
        val calls = Calls()

        val result = save(changed = true, send = true, update = EditOutcome.Conflict, calls = calls)

        assertEquals(PortfolioChange.Conflict, result)
        assertEquals(listOf("update"), calls.order)
    }

    @Test
    fun aSaveOfARecordThatIsGoneEndsThereAndSendsNothing() = runTest {
        val calls = Calls()

        val result = save(changed = true, send = true, update = EditOutcome.NotFound, calls = calls)

        assertEquals(PortfolioChange.NotFound, result)
        assertEquals(listOf("update"), calls.order)
    }

    @Test
    fun aFailedSaveIsThrownAndSendsNothing() = runTest {
        val calls = Calls()

        assertFailsWith<IllegalStateException> {
            save(changed = true, send = true, updateFailure = IllegalStateException("no network"), calls = calls)
        }
        assertEquals(listOf("update"), calls.order)
    }

    // ---- only sending ----

    @Test
    fun sendingARecordThatWasNotChangedIsTheOnlyRequest() = runTest {
        val calls = Calls()

        val result = save(changed = false, send = true, calls = calls)

        assertEquals(PortfolioChange.Saved(base, sent = true), result)
        assertEquals(listOf("submit"), calls.order)
    }

    @Test
    fun aConflictOrAGoneRecordOnTheOnlyRequestIsReported() = runTest {
        assertEquals(PortfolioChange.Conflict, save(changed = false, send = true, submit = { EditOutcome.Conflict }))
        assertEquals(PortfolioChange.NotFound, save(changed = false, send = true, submit = { EditOutcome.NotFound }))
    }

    @Test
    fun aFailureOfTheOnlyRequestIsTheFailureOfTheCall() = runTest {
        val failure = assertFailsWith<IllegalStateException> {
            save(changed = false, send = true, submit = { throw IllegalStateException("no network") })
        }

        assertEquals("no network", failure.message)
    }
}
