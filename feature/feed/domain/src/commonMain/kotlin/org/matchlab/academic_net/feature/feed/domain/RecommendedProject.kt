package org.matchlab.academic_net.feature.feed.domain

import org.matchlab.academic_net.common.model.Project

/**
 * A project the server recommends, with what the card under its title says about the initiator.
 *
 * @property initiatorUniversity the university of the initiator as their profile has it, trimmed; blank when the profile
 * has none or could not be read (the project has the initiator's id only, API-GAP G47)
 */
data class RecommendedProject(
    val project: Project,
    val initiatorUniversity: String,
)
