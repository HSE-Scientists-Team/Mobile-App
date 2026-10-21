package org.matchlab.academic_net.gateway

import io.ktor.client.engine.HttpClientEngine

/**
 * The HTTP engine of the platform: OkHttp on Android, NSURLSession (Ktor's Darwin engine) on iOS. Tests
 * use `MockEngine` instead and hand it to [Gateway] themselves.
 *
 * The engine is created already set to [GatewayConfig.connectTimeout] and [GatewayConfig.requestTimeout],
 * so a call made outside [Gateway] gets the same patience. Whoever creates an engine closes it; [Gateway]
 * never does.
 */
expect fun platformHttpEngine(config: GatewayConfig): HttpClientEngine
