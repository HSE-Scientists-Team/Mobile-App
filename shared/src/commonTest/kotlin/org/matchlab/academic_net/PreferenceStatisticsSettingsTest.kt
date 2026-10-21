package org.matchlab.academic_net

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.analytics.StatisticsPreference
import org.matchlab.academic_net.feature.settings.domain.usecase.ObserveStatisticsUseCase
import org.matchlab.academic_net.feature.settings.domain.usecase.SetStatisticsUseCase

/** The key of the choice on the disk (see the test of the preference on why it is spelled out). */
private const val Key = "analytics.statistics"

/**
 * [PreferenceStatisticsSettings] is the one place where the settings feature meets the choice of the analytics: the
 * switch "usage statistics" reads the preference, with "no choice yet" shown as "on", and writes to it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PreferenceStatisticsSettingsTest {
    private fun TestScope.preference(store: AuditedStore) =
        StatisticsPreference(store, StandardTestDispatcher(testScheduler))

    // ---- reading ----

    @Test
    fun aDeviceWithoutAChoiceReadsAsOnBeforeAndAfterTheStoreIsRead() = runTest {
        val preference = preference(AuditedStore())
        val settings = PreferenceStatisticsSettings(preference)
        assertEquals(true, settings.enabled.first(), "before the store is read")

        preference.load()

        assertNull(preference.enabled.value)
        assertEquals(true, settings.enabled.first(), "the store holds no choice")
    }

    @Test
    fun aStoredNoReadsAsOffAndAStoredYesAsOn() = runTest {
        listOf("0" to false, "1" to true).forEach { (stored, expected) ->
            val preference = preference(AuditedStore(mapOf(Key to stored)))
            preference.load()

            assertEquals(expected, PreferenceStatisticsSettings(preference).enabled.first(), "stored '$stored'")
        }
    }

    @Test
    fun aChoiceThatCannotBeReadReadsAsOn() = runTest {
        val store = AuditedStore(mapOf(Key to "0"))
        store.hidden = true
        val preference = preference(store)
        preference.load()

        assertEquals(true, PreferenceStatisticsSettings(preference).enabled.first())
    }

    @Test
    fun repeatedValuesAreToldOnce() = runTest {
        val preference = preference(AuditedStore())
        val settings = PreferenceStatisticsSettings(preference)
        val seen = mutableListOf<Boolean>()
        backgroundScope.launch { settings.enabled.collect { seen += it } }
        runCurrent()
        assertEquals(listOf(true), seen, "no choice: on")

        preference.load()
        runCurrent()
        assertEquals(listOf(true), seen, "the store holds no choice, so nothing changed")

        settings.setEnabled(true)
        runCurrent()
        assertEquals(listOf(true), seen, "a yes after the default of yes is the same value")

        settings.setEnabled(false)
        runCurrent()
        settings.setEnabled(false)
        runCurrent()
        settings.setEnabled(true)
        runCurrent()
        assertEquals(listOf(true, false, true), seen)
    }

    @Test
    fun theSwitchFollowsTheChoiceMadeElsewhere() = runTest {
        // The sign-up form saves the choice through the preference, not through the settings.
        val preference = preference(AuditedStore())
        val settings = PreferenceStatisticsSettings(preference)
        val seen = mutableListOf<Boolean>()
        backgroundScope.launch { settings.enabled.collect { seen += it } }
        runCurrent()

        preference.set(false)
        runCurrent()

        assertEquals(listOf(true, false), seen)
    }

    // ---- writing ----

    @Test
    fun savingWritesTheChoiceToThePreferenceAndTheStore() = runTest {
        val store = AuditedStore()
        val preference = preference(store)
        val settings = PreferenceStatisticsSettings(preference)

        settings.setEnabled(false)
        assertEquals(false, preference.enabled.value)
        assertEquals("0", store.peek(Key))

        settings.setEnabled(true)
        assertEquals(true, preference.enabled.value)
        assertEquals("1", store.peek(Key))
        assertEquals(listOf("write $Key", "write $Key"), store.mutations)
    }

    @Test
    fun aFailedYesReachesTheCallerAndChangesNothing() = runTest {
        val store = AuditedStore()
        store.failingKeys = setOf(Key)
        val preference = preference(store)
        val settings = PreferenceStatisticsSettings(preference)

        assertFailsWith<IllegalStateException> { settings.setEnabled(true) }

        assertNull(preference.enabled.value)
        assertNull(store.peek(Key))
        assertEquals(true, settings.enabled.first(), "no choice: still on")
    }

    @Test
    fun aFailedNoReachesTheCallerAndTheSwitchShowsOffAllTheSame() = runTest {
        val store = AuditedStore()
        store.failingKeys = setOf(Key)
        val preference = preference(store)
        val settings = PreferenceStatisticsSettings(preference)
        val seen = mutableListOf<Boolean>()
        backgroundScope.launch { settings.enabled.collect { seen += it } }
        runCurrent()

        assertFailsWith<IllegalStateException> { settings.setEnabled(false) }
        runCurrent()

        assertEquals(false, preference.enabled.value, "the refusal holds for this run")
        assertNull(store.peek(Key))
        assertEquals(false, settings.enabled.first())
        assertEquals(listOf(true, false), seen, "the screen is told that the switch is off")
    }

    @Test
    fun watchingTheSwitchWritesNothing() = runTest {
        val store = AuditedStore(mapOf(Key to "0"))
        val preference = preference(store)
        val settings = PreferenceStatisticsSettings(preference)

        preference.load()
        settings.enabled.first()

        assertEquals(emptyList(), store.mutations)
    }

    // ---- through the use cases of the settings feature ----

    @Test
    fun theUseCasesOfTheSettingsFeatureReachThePreferenceThroughTheAdapter() = runTest {
        val store = AuditedStore()
        val preference = preference(store)
        val settings = PreferenceStatisticsSettings(preference)

        assertEquals(true, ObserveStatisticsUseCase(settings)().first())
        val saved = SetStatisticsUseCase(settings)(false)

        assertEquals(true, saved.isSuccess)
        assertEquals(false, ObserveStatisticsUseCase(settings)().first())
        assertEquals("0", store.peek(Key))
    }

    @Test
    fun aFailedYesIsAFailedResultForTheScreenAndTheSwitchStaysWhereItWas() = runTest {
        val store = AuditedStore()
        val settings = PreferenceStatisticsSettings(preference(store))
        assertEquals(true, SetStatisticsUseCase(settings)(false).isSuccess)
        store.failingKeys = setOf(Key)

        val saved = SetStatisticsUseCase(settings)(true)

        assertEquals(true, saved.isFailure)
        assertEquals(false, ObserveStatisticsUseCase(settings)().first(), "a consent that was not saved switches nothing on")
        assertEquals("0", store.peek(Key))
    }

    @Test
    fun aFailedNoIsAFailedResultForTheScreenAndTheSwitchIsOffAllTheSame() = runTest {
        val store = AuditedStore()
        store.failingKeys = setOf(Key)
        val settings = PreferenceStatisticsSettings(preference(store))

        val saved = SetStatisticsUseCase(settings)(false)

        assertEquals(true, saved.isFailure)
        assertEquals(false, ObserveStatisticsUseCase(settings)().first(), "the refusal is in force although it was not saved")
    }
}
