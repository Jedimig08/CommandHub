package com.commandhub.server.routes

import com.commandhub.data.PrivacyManager
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.response.respondText

fun Application.installPrivacyInterceptor(privacyManager: PrivacyManager) {
    intercept(ApplicationCallPipeline.Call) {
        val path = call.request.path()
        val remoteHost = call.request.local.remoteHost

        val errorReason = privacyManager.evaluateAccess(path, remoteHost)
        if (errorReason != null) {
            val isJson = path.startsWith("/api/") || call.request.headers["Accept"]?.contains("application/json") == true
            if (isJson) {
                call.respond(
                    HttpStatusCode.Forbidden,
                    mapOf("error" to "Privacy Access Denied", "reason" to errorReason, "path" to path)
                )
            } else {
                call.respondText(
                    """
                    <!DOCTYPE html>
                    <html lang="en">
                    <head>
                        <title>Privacy Access Blocked</title>
                        <meta name="viewport" content="width=device-width, initial-scale=1">
                        <style>
                            body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: #0f172a; color: #f8fafc; padding: 40px; text-align: center; }
                            .card { background: #1e293b; border: 2px solid #ef4444; border-radius: 12px; padding: 30px; max-width: 500px; margin: 40px auto; box-shadow: 0 4px 20px rgba(239,68,68,0.2); }
                            h1 { color: #ef4444; margin-top: 0; font-size: 1.5rem; }
                            p { color: #94a3b8; line-height: 1.5; margin: 15px 0; }
                            code { background: #0f172a; color: #38bdf8; padding: 4px 8px; border-radius: 4px; font-family: monospace; }
                            a { display: inline-block; margin-top: 15px; color: #38bdf8; text-decoration: none; font-weight: bold; border: 1px solid #38bdf8; padding: 8px 16px; border-radius: 20px; }
                            a:hover { background: rgba(56,189,248,0.1); }
                        </style>
                    </head>
                    <body>
                        <div class="card">
                            <h1>🛑 Privacy Access Blocked</h1>
                            <p><strong>Reason:</strong> $errorReason</p>
                            <p style="font-size: 0.85em; color: #64748b;">Endpoint: <code>$path</code></p>
                            <a href="/privacy">&larr; Go to Privacy Controls Page</a>
                        </div>
                    </body>
                    </html>
                    """.trimIndent(),
                    ContentType.Text.Html,
                    HttpStatusCode.Forbidden
                )
            }
            finish()
        }
    }
}
