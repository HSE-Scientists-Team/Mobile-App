package org.matchlab.academic_net.feature.settings.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PrivacySettingsTest {
    private val off = PrivacySettings(showParticipatedProjects = false, showContacts = false)

    @Test
    fun withSwitchesOnlyTheGivenOption() {
        assertEquals(PrivacySettings(true, false), off.with(PrivacyOption.ParticipatedProjects, true))
        assertEquals(PrivacySettings(false, true), off.with(PrivacyOption.Contacts, true))
    }

    @Test
    fun withKeepsTheOtherOption() {
        val on = PrivacySettings(true, true)

        assertEquals(PrivacySettings(false, true), on.with(PrivacyOption.ParticipatedProjects, false))
        assertEquals(PrivacySettings(true, false), on.with(PrivacyOption.Contacts, false))
    }

    @Test
    fun isEnabledReadsTheOption() {
        val settings = PrivacySettings(showParticipatedProjects = true, showContacts = false)

        assertTrue(settings.isEnabled(PrivacyOption.ParticipatedProjects))
        assertFalse(settings.isEnabled(PrivacyOption.Contacts))
    }
}
