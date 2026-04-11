package com.claudecode.navigator.server

import com.claudecode.navigator.model.*
import com.claudecode.navigator.navigation.NavigationResult
import com.claudecode.navigator.navigation.Navigator
import com.claudecode.navigator.resolver.FileResolver
import com.claudecode.navigator.resolver.SymbolResolver
import com.claudecode.navigator.resolver.TextResolver
import com.intellij.diff.editor.DiffEditorViewerFileEditor
import com.intellij.diff.impl.DiffRequestProcessor
import com.intellij.diff.tools.fragmented.UnifiedDiffViewer
import com.intellij.diff.util.Side
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
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
                is CaretRequest -> handleCaretRequest()
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

        if (request.line == null && request.matchText == null) {
            return activate(targets)
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
                            val foundLine = findUniqueLineInDoc(doc, request.matchText)
                            if (foundLine != null && foundLine == -1) {
                                return NavigationResponse(status = "error", message = "ambiguous: matchText appears multiple times in file")
                            }
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
                    val foundLine = findUniqueLineInDoc(doc, request.matchText)
                    if (foundLine != null && foundLine == -1) {
                        return NavigationResponse(status = "error", message = "ambiguous: matchText appears multiple times in file")
                    }
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

    private fun handleCaretRequest(): NavigationResponse {
        var response: NavigationResponse? = null
        ApplicationManager.getApplication().invokeAndWait {
            response = readCaretPosition()
        }
        return response ?: NavigationResponse(status = "error", message = "no active editor")
    }

    private fun readCaretPosition(): NavigationResponse? {
        val fileEditorManager = FileEditorManager.getInstance(project)

        // Check if the active editor is a unified diff viewer
        val diffResult = tryReadUnifiedDiffCaret(fileEditorManager)
        if (diffResult != null) return diffResult

        // Regular editor
        val editor = fileEditorManager.selectedTextEditor ?: return null
        val file = FileDocumentManager.getInstance().getFile(editor.document) ?: return null
        val caret = editor.caretModel.logicalPosition
        return NavigationResponse(
            status = "ok",
            file = file.path,
            line = caret.line + 1
        )
    }

    /**
     * If the active editor is a unified diff viewer, read the caret position and
     * map it to the corresponding line in the current (right-side) version of the file.
     * If the caret is on a deleted line, finds the nearest line present in the current version.
     */
    private fun tryReadUnifiedDiffCaret(fileEditorManager: FileEditorManager): NavigationResponse? {
        val selectedEditor = fileEditorManager.selectedEditor
        if (selectedEditor !is DiffEditorViewerFileEditor) return null

        val processor = selectedEditor.editorViewer as? DiffRequestProcessor ?: return null
        val viewer = processor.activeViewer as? UnifiedDiffViewer ?: return null

        val editor = viewer.editor
        val unifiedLine = editor.caretModel.logicalPosition.line

        val rightDoc = viewer.getContent(Side.RIGHT).document
        val file = FileDocumentManager.getInstance().getFile(rightDoc)
        val path = file?.path ?: return null

        // Read the line text at the caret in the unified editor
        val unifiedDoc = editor.document
        if (unifiedLine >= unifiedDoc.lineCount) return null
        val lineStart = unifiedDoc.getLineStartOffset(unifiedLine)
        val lineEnd = unifiedDoc.getLineEndOffset(unifiedLine)
        val lineText = unifiedDoc.getText(TextRange(lineStart, lineEnd)).trim()

        // Find this line in the right-side (current) document
        val matchedLine = findUniqueLineInDoc(rightDoc, lineText)
        if (matchedLine != null) {
            return if (matchedLine >= 0) {
                NavigationResponse(status = "ok", file = path, line = matchedLine + 1)
            } else {
                NavigationResponse(status = "error", message = "ambiguous: line appears multiple times in file")
            }
        }

        // Line is deleted (only in old version) — search downward then upward in the
        // unified document for the first line whose text exists in the current file.
        for (delta in 1..unifiedDoc.lineCount) {
            for (candidate in listOf(unifiedLine + delta, unifiedLine - delta)) {
                if (candidate in 0 until unifiedDoc.lineCount) {
                    val s = unifiedDoc.getLineStartOffset(candidate)
                    val e = unifiedDoc.getLineEndOffset(candidate)
                    val candidateText = unifiedDoc.getText(TextRange(s, e)).trim()
                    if (candidateText.isNotEmpty()) {
                        val found = findUniqueLineInDoc(rightDoc, candidateText)
                        if (found != null && found >= 0) {
                            return NavigationResponse(status = "ok", file = path, line = found + 1)
                        }
                        // Skip ambiguous neighbor lines, keep searching
                    }
                }
            }
        }

        return NavigationResponse(status = "error", message = "ambiguous: could not determine unique line")
    }

    /**
     * Find a line in [doc] whose trimmed content matches [text].
     * Returns: 0-indexed line number if unique match, -1 if ambiguous (multiple matches),
     * null if not found.
     */
    private fun findUniqueLineInDoc(doc: Document, text: String): Int? {
        var count = 0
        var firstMatch: Int? = null
        for (i in 0 until doc.lineCount) {
            val s = doc.getLineStartOffset(i)
            val e = doc.getLineEndOffset(i)
            if (doc.getText(TextRange(s, e)).trim() == text) {
                count++
                if (firstMatch == null) firstMatch = i
                if (count > 1) return -1
            }
        }
        return firstMatch
    }



    private fun navigate(targets: List<NavigationTarget>): NavigationResponse {
        return runOnEdt(targets.size, includeCoordinates = true) { navigator.navigate(targets) }
    }

    private fun activate(targets: List<NavigationTarget>): NavigationResponse {
        return runOnEdt(targets.size, includeCoordinates = false) { navigator.activate(targets) }
    }

    private fun runOnEdt(
        targetCount: Int,
        includeCoordinates: Boolean,
        openTargets: () -> NavigationResult?
    ): NavigationResponse {
        var result: NavigationResult? = null

        // Run navigation synchronously on EDT so it completes before the TCP
        // response returns. This prevents agent-term from stealing focus before
        // navigation executes, and lets us return diagnostic info.
        ApplicationManager.getApplication().invokeAndWait {
            result = openTargets()
        }

        val navigationResult = result
        return if (navigationResult != null) {
            NavigationResponse(
                status = "ok",
                file = navigationResult.filePath,
                line = if (includeCoordinates) navigationResult.line else null,
                column = if (includeCoordinates) navigationResult.column else null
            )
        } else if (targetCount > 1) {
            NavigationResponse(status = "multiple", count = targetCount)
        } else {
            NavigationResponse(status = "ok")
        }
    }
}
