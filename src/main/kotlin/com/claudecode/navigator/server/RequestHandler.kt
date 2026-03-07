package com.claudecode.navigator.server

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
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange

class RequestHandler(private val project: Project) {
    private val logger = Logger.getInstance(RequestHandler::class.java)
    private val fileResolver = FileResolver(project)
    private val symbolResolver = SymbolResolver(project)
    private val textResolver = TextResolver(project)
    private val navigator = Navigator(project)

    private companion object

    fun handle(jsonRequest: String): NavigationResponse {
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
            NavigationResponse(status = "error", message = e.message)
        }
    }

    private fun handleFileRequest(request: FileRequest): NavigationResponse {
        val targets = fileResolver.resolve(request.path)

        if (targets.isEmpty()) {
            return NavigationResponse(status = "not_found", message = "file not found")
        }

        val targetsWithLine = if (request.line != null) {
            targets.map { it.copy(line = request.line - 1) } // Convert to 0-indexed
        } else {
            targets
        }

        if (request.matchText != null) {
            val target = targetsWithLine.first()
            val doc = FileDocumentManager.getInstance().getDocument(target.file)
            if (doc != null) {
                if (request.line != null) {
                    // Validate matchText against the given line; spiral if mismatch
                    val lineIndex = target.line
                    if (lineIndex in 0 until doc.lineCount) {
                        val lineStart = doc.getLineStartOffset(lineIndex)
                        val lineEnd = doc.getLineEndOffset(lineIndex)
                        val lineContent = doc.getText(TextRange(lineStart, lineEnd)).trim()
                        if (lineContent != request.matchText) {
                            val foundLine = searchFileForText(doc, request.matchText, lineIndex)
                            if (foundLine != null) {
                                val correctedTargets = targetsWithLine.map { it.copy(line = foundLine) }
                                val result = navigate(correctedTargets)
                                return result.copy(status = "text_moved")
                            }
                            return NavigationResponse(status = "not_found", message = "matchText not in file")
                        }
                    }
                } else {
                    // No line given — search entire file for matchText
                    val foundLine = searchFileForText(doc, request.matchText)
                    if (foundLine != null) {
                        val matchedTargets = targets.map { it.copy(line = foundLine) }
                        return navigate(matchedTargets)
                    }
                    return NavigationResponse(status = "not_found", message = "matchText not in file")
                }
            }
        }

        return navigate(targetsWithLine)
    }

    private fun handleSymbolRequest(request: SymbolRequest): NavigationResponse {
        val targets = symbolResolver.resolve(request.name, request.fileHint)

        if (targets.isEmpty()) {
            return NavigationResponse(status = "not_found", message = "symbol: ${request.name}")
        }

        return navigate(targets)
    }

    private fun handleTextRequest(request: TextRequest): NavigationResponse {
        val targets = textResolver.resolve(request.text)

        if (targets.isEmpty()) {
            return NavigationResponse(status = "not_found", message = "text: ${request.text.take(50)}")
        }

        return navigate(targets)
    }

    /**
     * Search a document for a line matching [text]. When [hint] is given, spiral
     * outward from that 0-indexed line (±1, ±2, …±200). Otherwise scan top-to-bottom.
     * Returns the 0-indexed line number or null.
     */
    private fun searchFileForText(doc: Document, text: String, hint: Int? = null): Int? {
        if (hint != null) {
            for (delta in 1..200) {
                for (candidate in listOf(hint - delta, hint + delta)) {
                    if (candidate in 0 until doc.lineCount) {
                        val s = doc.getLineStartOffset(candidate)
                        val e = doc.getLineEndOffset(candidate)
                        if (doc.getText(TextRange(s, e)).trim() == text) return candidate
                    }
                }
            }
        } else {
            for (i in 0 until doc.lineCount) {
                val s = doc.getLineStartOffset(i)
                val e = doc.getLineEndOffset(i)
                if (doc.getText(TextRange(s, e)).trim() == text) return i
            }
        }
        return null
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
                file = navResult.filePath,
                line = navResult.line,
                column = navResult.column
            )
        } else if (targets.size > 1) {
            NavigationResponse(status = "multiple", count = targets.size)
        } else {
            NavigationResponse(status = "ok")
        }
    }
}
