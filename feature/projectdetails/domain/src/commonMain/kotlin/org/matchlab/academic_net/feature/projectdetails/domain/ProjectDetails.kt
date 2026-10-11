package org.matchlab.academic_net.feature.projectdetails.domain

import org.matchlab.academic_net.common.model.Project

/**
 * What the project page adds to the project about its initiator: the line under the name
 * ("Инициатор · Stanford") and the numbers next to it ("★ 4.9 · 12 завершённых проектов").
 */
data class InitiatorInfo(
    /** The initiator's university as the server writes it; may be blank. */
    val university: String,
    /** Average of the initiator's reviews, `null` while there are none. */
    val rating: Double?,
    /** How many projects the initiator has finished. */
    val completedProjects: Int,
)

/** A project as "04 Проект" shows it: the project itself and the facts about its initiator. */
data class ProjectDetails(
    val project: Project,
    val initiator: InitiatorInfo,
)
