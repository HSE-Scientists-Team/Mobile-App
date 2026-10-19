package org.matchlab.academic_net.common.model

import kotlin.jvm.JvmInline

@JvmInline
value class ProjectId(val value: String)

@JvmInline
value class RoleId(val value: String)

@JvmInline
value class UserId(val value: String)

/** The id of an application or an invitation to a role of a project (`ProjectJoinRequest.id`). */
@JvmInline
value class ProjectJoinId(val value: String)
