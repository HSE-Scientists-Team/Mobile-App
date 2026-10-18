package org.matchlab.academic_net.di

import me.tatarka.inject.annotations.Provides
import org.matchlab.academic_net.Backend
import org.matchlab.academic_net.gateway.Gateway
import org.matchlab.academic_net.gateway.GatewayConfig
import org.matchlab.academic_net.toConfig

/**
 * Makes the one [Gateway] client of the process. The app uses the HTTP engine of the platform ([Default]); a test
 * hands the component a factory that builds the Gateway over a mock engine, so that the Gateway mode can be run and
 * checked without a network.
 */
internal fun interface GatewayFactory {
    /** The client for [config]. It is asked for at most once per component, at the first use of the Gateway. */
    fun create(config: GatewayConfig): Gateway

    companion object {
        /** The engine of the platform: OkHttp on Android, NSURLSession on iOS. */
        val Default: GatewayFactory = GatewayFactory { config -> Gateway(config) }
    }
}

/**
 * The Gateway client, for the whole process. It is the dependency of the sign-in data source and of the session
 * clients, and nothing asks for it in the [Backend.Mock] mode: the places that may need it take a `Lazy<Gateway>` and
 * ask only when the mode says so, so the client of the mock mode is never created.
 */
internal interface GatewayModule {
    /**
     * Created at the first use and then shared. In the [Backend.Mock] mode there is no server, so asking for the
     * Gateway there is a programming error and is reported as one, instead of quietly building a client that would
     * call the production server.
     */
    @Provides
    @AppScope
    fun gateway(backend: Backend, factory: GatewayFactory): Gateway = when (backend) {
        Backend.Mock -> error("The mock backend has no Gateway: ask for it only when the backend is Backend.Gateway")
        is Backend.Gateway -> factory.create(backend.toConfig())
    }
}
