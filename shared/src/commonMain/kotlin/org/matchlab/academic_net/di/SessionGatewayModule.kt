package org.matchlab.academic_net.di

import me.tatarka.inject.annotations.Provides
import org.matchlab.academic_net.feature.auth.domain.AccessToken
import org.matchlab.academic_net.feature.auth.domain.AuthRepository
import org.matchlab.academic_net.feature.auth.domain.Session
import org.matchlab.academic_net.gateway.Gateway
import org.matchlab.academic_net.gateway.GatewaySession

/**
 * The calls of the signed-in user to the Gateway. The data sources of the features that talk to the server take a
 * `Lazy<GatewaySession>` (see [DataSourcesModule]): it is opened at the first call of the session.
 */
internal interface SessionGatewayModule {
    /**
     * The session client over the process's [Gateway], with the token of [session] in every request. A 401 on any of
     * its calls goes to [AuthRepository.expireSession], which asks the server whether the token is really refused and
     * only then ends the session (so one hiccup of the server does not sign the user out). API-GAP G03: the API does
     * not describe the lifetime of the token or its renewal, so the token is good until the server refuses it.
     *
     * It is closed with the session graph ([SessionResources.close]) and with nothing else: the engine of the
     * Gateway and the calls of the next session are not touched.
     */
    @Provides
    @SessionScope
    fun gatewaySession(
        gateway: Gateway,
        session: Session,
        auth: AuthRepository,
        resources: SessionResources,
    ): GatewaySession = resources.closeOnEnd(
        gateway.openSession(session.accessToken.value) { rejected -> auth.expireSession(AccessToken(rejected)) },
    )
}
