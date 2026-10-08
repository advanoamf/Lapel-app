package com.lapel.server

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse
import java.time.Clock
import java.util.Base64

/** AWS Lambda entry point for the Function URL (HTTP API v2 payload format). */
class Handler : RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    override fun handleRequest(event: APIGatewayV2HTTPEvent, context: Context?): APIGatewayV2HTTPResponse {
        val body = event.body?.let { if (event.isBase64Encoded) String(Base64.getDecoder().decode(it)) else it }
        val response = api.handle(
            Request(
                method = event.requestContext?.http?.method ?: "GET",
                path = event.rawPath ?: "/",
                query = event.queryStringParameters.orEmpty(),
                headers = event.headers.orEmpty(),
                body = body,
            ),
        )
        return APIGatewayV2HTTPResponse.builder()
            .withStatusCode(response.status)
            .withHeaders(
                mapOf(
                    "Content-Type" to response.contentType,
                    "Cache-Control" to "no-store",
                    "X-Content-Type-Options" to "nosniff",
                ),
            )
            .withBody(response.body)
            .build()
    }

    private companion object {
        // Created once per Lambda instance (and captured by SnapStart).
        val api: Api by lazy {
            val store = DynamoRecordStore(System.getenv("TABLE_NAME") ?: error("TABLE_NAME not set"))
            val clock = Clock.systemUTC()
            Api(AuthService(System.getenv("LAPEL_PASSWORD_HASH").orEmpty(), store, clock), SyncService(store, clock))
        }
    }
}
