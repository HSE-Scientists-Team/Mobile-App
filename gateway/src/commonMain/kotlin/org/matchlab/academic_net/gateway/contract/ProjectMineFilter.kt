package org.matchlab.academic_net.gateway.contract

/**
 * The values of the `mine` parameter of `GET /api/v1/projects` (schema `ProjectMineFilter` of `docs/openapi.yaml`, v2.6.0;
 * the semantics are described there, no more values exist). The filter needs a signed-in session.
 */
object ProjectMineFilter {
    /** Only the projects the viewer owns. */
    const val Owner = "owner"

    /** Only the projects the viewer takes part in actively, without their own. */
    const val Participant = "participant"
}
