package com.commandhub.server.routes

import com.commandhub.data.AuthManager
import com.commandhub.data.isLoopbackAddress
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.request.header
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.response.respondText

fun Application.installAuthInterceptor(authManager: AuthManager) {
    intercept(ApplicationCallPipeline.Call) {
        val path = call.request.path()
        val remoteHost = call.request.local.remoteHost

        // Local loopback requests (127.0.0.1, localhost, ::1) are always trusted as admin
        if (isLoopbackAddress(remoteHost)) {
            return@intercept
        }

        // Rate limit pairing or login attempts
        if ((path == "/pair" || path == "/login") && !authManager.checkRateLimit(remoteHost)) {
            call.respond(HttpStatusCode.TooManyRequests, mapOf("error" to "Too many attempts. Locked out temporarily."))
            finish()
            return@intercept
        }

        // Allow public paths
        val isPublic = path == "/" ||
                path == "/login" ||
                path == "/pair" ||
                path == "/privacy" ||
                path.startsWith("/api/privacy") ||
                path.startsWith("/assets/") ||
                path.endsWith(".css") ||
                path.endsWith(".js") ||
                path.endsWith(".ico") ||
                path.endsWith(".png")

        if (isPublic) {
            return@intercept
        }

        // Check authentication
        val authHeader = call.request.header("Authorization")
        val bearerToken = if (authHeader?.startsWith("Bearer ") == true) authHeader.substring(7) else null
        val sessionCookie = call.request.cookies["session_id"]
        val queryTicket = call.request.queryParameters["ticket"]

        val isAuthenticated = authManager.validateBearerToken(bearerToken) ||
                authManager.validateSession(sessionCookie) ||
                authManager.validateSession(queryTicket)

        if (!isAuthenticated) {
            val isJson = path.startsWith("/api/") || call.request.headers["Accept"]?.contains("application/json") == true
            if (isJson) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unauthorized", "message" to "Valid bearer token or session cookie required"))
            } else {
                call.respondText(
                    """
                    <!DOCTYPE html>
                    <html lang="en">
                    <head>
                        <title>Authentication Required</title>
                        <meta name="viewport" content="width=device-width, initial-scale=1">
                        <style>
                            body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: #0f172a; color: #f8fafc; padding: 40px; text-align: center; }
                            .card { background: #1e293b; border: 2px solid #38bdf8; border-radius: 12px; padding: 30px; max-width: 500px; margin: 40px auto; box-shadow: 0 4px 20px rgba(56,189,248,0.2); }
                            h1 { color: #38bdf8; margin-top: 0; font-size: 1.5rem; }
                            p { color: #94a3b8; line-height: 1.5; margin: 15px 0; }
                            a { display: inline-block; margin-top: 15px; color: #38bdf8; text-decoration: none; font-weight: bold; border: 1px solid #38bdf8; padding: 8px 16px; border-radius: 20px; }
                            a:hover { background: rgba(56,189,248,0.1); }
                        </style>
                    </head>
                    <body>
                        <div class="card">
                            <h1>🔒 Authentication Required</h1>
                            <p>This CommandHub endpoint requires authentication. Please pair or provide your bearer token.</p>
                            <a href="/login">&larr; Go to Login / Pairing</a>
                        </div>
                    </body>
                    </html>
                    """.trimIndent(),
                    ContentType.Text.Html,
                    HttpStatusCode.Unauthorized
                )
            }
            finish()
            return@intercept
        }

        // Defense-in-depth: State-changing requests should require JSON or custom header
        val method = call.request.httpMethod.value
        if (method == "POST" || method == "PUT" || method == "DELETE") {
            val contentType = call.request.header("Content-Type")
            val customHeader = call.request.header("X-CommandHub-Client")
            val isFormPost = contentType?.contains("application/x-www-form-urlencoded") == true || contentType?.contains("multipart/form-data") == true
            if (isFormPost || (contentType == null && customHeader == null)) {
                call.respond(HttpStatusCode.Forbidden, mapOf("error" to "CSRF Defense: State-changing requests must be JSON or include custom client header"))
                finish()
                return@intercept
            }
        }
    }
}
