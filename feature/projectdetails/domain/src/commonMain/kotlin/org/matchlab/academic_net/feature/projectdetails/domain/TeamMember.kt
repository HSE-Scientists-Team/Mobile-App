package org.matchlab.academic_net.feature.projectdetails.domain

import org.matchlab.academic_net.common.model.RoleMember

/**
 * A member of the project's team as "Команда" of 04 shows them.
 *
 * @property person the member with the numbers of their profile; without a profile ([RoleMember.hasProfile] is `false`)
 *  it has the id only.
 * @property roleTitle the role the member works on; empty when unknown.
 * @property isViewer the member is the user of the session.
 */
data class TeamMember(
    val person: RoleMember,
    val roleTitle: String,
    val isViewer: Boolean,
)
