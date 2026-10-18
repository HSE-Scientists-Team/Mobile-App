package org.matchlab.academic_net.common.result

/**
 * The server the app talks to cannot do this yet, because its API leaves out what the operation needs (values of a
 * field, a format, a call). [gap] is the id of the gap in `docs/api-gaps.md`, e.g. "G40".
 *
 * The screens do not get here in the normal course: they read the capability flags of their feature (`…Capabilities`)
 * and hide or switch off the action with a word of explanation. A data source throws this when it is asked anyway,
 * instead of sending a request on the off chance. It is not a [RemoteFailure]: nothing was sent, and asking again does
 * not help until the gap is closed.
 */
class FeatureUnavailableException(val gap: String) :
    UnsupportedOperationException("Not available on this server: API gap $gap")
