package com.claudecode.navigator.server

import com.claudecode.navigator.BuildInfo
import com.claudecode.navigator.model.*
import com.claudecode.navigator.navigation.NavigationResult
import com.claudecode.navigator.navigation.Navigator
import com.claudecode.navigator.resolver.FileResolver
import com.claudecode.navigator.resolver.SymbolResolver
import com.claudecode.navigator.resolver.TextResolver
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange

class RequestHandler(private val project: Project) {
    private val logger = Logger.getInstance(RequestHandler::class.java)
    private val fileResolver = FileResolver(project)
    private val symbolResolver = SymbolResolver(project)
    private val textResolver = TextResolver(project)
    private val navigator = Navigator(project)

    private companion object {
        const val TRIAL_DAYS = 90
    }

    fun handle(jsonRequest: String): NavigationResponse {
        val daysSinceBuild = (System.currentTimeMillis() - BuildInfo.BUILD_EPOCH_MILLIS) /
            (1000 * 60 * 60 * 24)
        if (daysSinceBuild >= TRIAL_DAYS) {
            return NavigationResponse.error(
                "Plugin trial expired. Please download a newer version from the releases page."
            )
        }

        return try {
            val request = NavigationRequest.parse(jsonRequest)
            logger.info("Parsed request: $request")

            when (request) {
                is FileRequest -> handleFileRequest(request)
                is SymbolRequest -> handleSymbolRequest(request)
                is TextRequest -> handleTextRequest(request)
            }
        } catch (e: Exception) {
            logger.error("Failed to handle request: $jsonRequest", e)
            NavigationResponse.error(e.message ?: "Unknown error")
        }
    }

    private fun handleFileRequest(request: FileRequest): NavigationResponse {
        val targets = fileResolver.resolve(request.path)

        if (targets.isEmpty()) {
            return NavigationResponse.error("File not found: ${request.path}")
        }

        val targetsWithLine = if (request.line != null) {
            targets.map { it.copy(line = request.line - 1) } // Convert to 0-indexed
        } else {
            targets
        }

        // Validate matchText against actual line content before navigating
        if (request.matchText != null && request.line != null) {
            val target = targetsWithLine.first()
            val doc = FileDocumentManager.getInstance().getDocument(target.file)
            if (doc != null) {
                val lineIndex = target.line
                if (lineIndex in 0 until doc.lineCount) {
                    val lineStart = doc.getLineStartOffset(lineIndex)
                    val lineEnd = doc.getLineEndOffset(lineIndex)
                    val lineContent = doc.getText(TextRange(lineStart, lineEnd)).trim()
                    if (lineContent != request.matchText) {
                        return NavigationResponse(
                            status = "text_mismatch",
                            message = "Line ${request.line} does not contain expected text",
                            file = target.file.path,
                            line = request.line
                        )
                    }
                }
            }
        }

        return navigate(targetsWithLine)
    }

    private fun handleSymbolRequest(request: SymbolRequest): NavigationResponse {
        val targets = symbolResolver.resolve(request.name, request.fileHint)

        if (targets.isEmpty()) {
            return NavigationResponse.error("Symbol not found: ${request.name}")
        }

        return navigate(targets)
    }

    private fun handleTextRequest(request: TextRequest): NavigationResponse {
        val targets = textResolver.resolve(request.text)

        if (targets.isEmpty()) {
            return NavigationResponse.error("Text not found: ${request.text.take(50)}")
        }

        return navigate(targets)
    }

    private fun navigate(targets: List<NavigationTarget>): NavigationResponse {
        var result: NavigationResult? = null

        // Run navigation synchronously on EDT so it completes before the TCP
        // response returns. This prevents agent-term from stealing focus before
        // navigation executes, and lets us return diagnostic info.
        ApplicationManager.getApplication().invokeAndWait {
            result = navigator.navigate(targets)
        }

        val navResult = result
        return if (targets.size == 1 && navResult != null) {
            NavigationResponse(
                status = "ok",
                message = navResult.diagMessage,
                file = navResult.filePath,
                line = navResult.line,
                column = navResult.column
            )
        } else if (targets.size > 1) {
            NavigationResponse(status = "multiple", count = targets.size, message = navResult?.diagMessage)
        } else {
            NavigationResponse(status = "ok", message = navResult?.diagMessage)
        }
    }
}
