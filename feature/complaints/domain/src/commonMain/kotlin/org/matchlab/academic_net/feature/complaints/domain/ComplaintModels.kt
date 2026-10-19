package org.matchlab.academic_net.feature.complaints.domain

import org.matchlab.academic_net.common.model.Collaborator
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.UserId

/** Whom or what the complaint is about. */
sealed interface ComplaintTarget {
    /** A person (17, from their profile 09 or from a chat 13). */
    data class User(val id: UserId) : ComplaintTarget

    /** A project (17b, from its page 04). */
    data class Project(val id: ProjectId) : ComplaintTarget
}

/**
 * A reason of a complaint: [code] goes as `subject` (`docs/api-values.md`; API-GAP G68: the API names no values, so the
 * codes are the app's own). The table of codes lives in [UserComplaintReason] and [ProjectComplaintReason].
 */
sealed interface ComplaintReason {
    val code: String
}

/** The reasons of a complaint about a person (17), in the order of the frame. */
enum class UserComplaintReason(override val code: String) : ComplaintReason {
    /** "Спам или реклама" */
    Spam("1"),

    /** "Оскорбления" */
    Insults("2"),

    /** "Фальсификация в портфолио" */
    PortfolioFraud("3"),

    /** "Фейковый аккаунт" */
    FakeAccount("4"),

    /** "Мошенничество" */
    Scam("5"),

    /** "Другое" */
    Other("6"),
}

/** The reasons of a complaint about a project (17b), in the order of the frame. */
enum class ProjectComplaintReason(override val code: String) : ComplaintReason {
    /** "Спам или реклама" */
    Spam("1"),

    /** "Недостоверная информация" */
    FalseInformation("2"),

    /** "Мошенничество" */
    Scam("3"),

    /** "Другое" */
    Other("4"),
}

/** The reasons offered for [target]: [UserComplaintReason] or [ProjectComplaintReason] entries, in the order of the frame. */
fun complaintReasonsFor(target: ComplaintTarget): List<ComplaintReason> = when (target) {
    is ComplaintTarget.User -> UserComplaintReason.entries
    is ComplaintTarget.Project -> ProjectComplaintReason.entries
}

/** The card at the top of 17 / 17b. */
sealed interface ComplaintSubject {
    /** The person complained about. */
    data class PersonSubject(val person: Collaborator) : ComplaintSubject

    /** The project complained about: its title and its initiator's name and university. */
    data class ProjectSubject(
        val title: String,
        val initiatorName: String,
        val initiatorUniversity: String,
    ) : ComplaintSubject
}
