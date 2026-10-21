package org.matchlab.academic_net.gateway.internal

import org.matchlab.academic_net.common.result.RemoteFailure

/** OkHttp reports every transport error as a `java.io.IOException`, which the common mapping already covers. */
internal actual fun platformFailure(cause: Throwable): RemoteFailure? = null
