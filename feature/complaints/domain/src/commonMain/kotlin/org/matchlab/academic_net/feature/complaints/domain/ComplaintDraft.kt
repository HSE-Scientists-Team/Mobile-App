package org.matchlab.academic_net.feature.complaints.domain

/** The longest text "Что произошло" the app sends: `CreateComplaint.description` has `maxLength: 10000` in `docs/openapi.yaml`. */
const val ComplaintDescriptionMaxLength = 10_000

/** What is wrong with a [ComplaintDraft]; the form shows an error under the field. */
enum class ComplaintDraftError { ReasonMissing, DescriptionMissing, DescriptionTooLong }

/**
 * The form 17. [reason] is the chosen one (`null`: none yet), [description] the text "Что произошло".
 */
data class ComplaintDraft(
    val target: ComplaintTarget,
    val reason: ComplaintReason? = null,
    val description: String = "",
) {
    /** The draft with the description trimmed. */
    fun normalized(): ComplaintDraft = copy(description = description.trim())

    /**
     * What is wrong with the draft as it is (call it on [normalized]): no reason, or a reason that is not among
     * [complaintReasonsFor] the target, is [ComplaintDraftError.ReasonMissing]; a description empty after trimming is
     * [ComplaintDraftError.DescriptionMissing]; one longer than [ComplaintDescriptionMaxLength] is
     * [ComplaintDraftError.DescriptionTooLong].
     */
    fun validate(): Set<ComplaintDraftError> = buildSet {
        if (reason == null || reason !in complaintReasonsFor(target)) add(ComplaintDraftError.ReasonMissing)
        val text = description.trim()
        if (text.isEmpty()) add(ComplaintDraftError.DescriptionMissing)
        if (text.length > ComplaintDescriptionMaxLength) add(ComplaintDraftError.DescriptionTooLong)
    }
}

/** A complaint that passed validation. */
data class Complaint(
    val target: ComplaintTarget,
    val reason: ComplaintReason,
    val description: String,
)
