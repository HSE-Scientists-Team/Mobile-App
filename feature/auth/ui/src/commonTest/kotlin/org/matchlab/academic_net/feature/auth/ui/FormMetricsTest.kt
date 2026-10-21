package org.matchlab.academic_net.feature.auth.ui

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/** The numbers of the forms are those of the design: a change of one of them has to go through a look at Figma. */
class FormMetricsTest {
    @Test
    fun theFormIsPaddedBySixteenAndItsGroupsAreTwentyFourApart() {
        assertEquals(16.dp, FormMetrics.Inset)
        assertEquals(24.dp, FormMetrics.GroupGap)
    }

    @Test
    fun fieldsAreSixteenApartAndTheLinkFieldIsTwelveFromItsButton() {
        assertEquals(16.dp, FormMetrics.PartGap)
        assertEquals(12.dp, FormMetrics.LinkGap)
    }

    @Test
    fun buttonsAreEightApart() {
        assertEquals(8.dp, FormMetrics.ButtonGap)
    }
}
