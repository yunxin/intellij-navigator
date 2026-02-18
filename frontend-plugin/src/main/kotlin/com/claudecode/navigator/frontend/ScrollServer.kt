package com.claudecode.navigator.frontend

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.atomic.AtomicBoolean

@Serializable
data class ScrollRequest(
    val action: String,
    val file: String,
    val line: Int,
    val column: Int
)

@Serializable
data class ScrollResponse(val status: String, val message: String? = null)

class ScrollServer(
    private val project: Project,
    private val port: Int = DEFAULT_PORT
) {
    private val logger = Logger.getInstance(ScrollServer::class.java)
    private val json = Json { ignoreUnknownKeys = true }
    private val isRunning = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun start() {
        if (isRunning.getAndSet(true)) {
            logger.warn("Scroll server already running on port $port")
            return
        }

        serverJob = scope.launch {
            try {
                serverSocket = ServerSocket(port)
                logger.info("Navigator Frontend scroll server started on port $port")

                while (isActive && isRunning.get()) {
                    try {
                        val clientSocket = serverSocket?.accept() ?: break
                        launch { handleClient(clientSocket) }
                    } catch (e: SocketException) {
                        if (isRunning.get()) {
                            logger.warn("Socket exception while accepting connection", e)
                        }
                    }
                }
            } catch (e: Exception) {
                logger.error("Failed to start scroll server on port $port", e)
                isRunning.set(false)
            }
        }
    }

    private suspend fun handleClient(socket: Socket) {
        try {
            socket.use { client ->
                val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                val writer = PrintWriter(client.getOutputStream(), true)

                val line = reader.readLine()
                if (line != null) {
                    logger.debug("Received scroll request: $line")
                    val response = handleRequest(line)
                    writer.println(json.encodeToString(ScrollResponse.serializer(), response))
                }
            }
        } catch (e: Exception) {
            logger.warn("Error handling scroll client", e)
        }
    }

    private suspend fun handleRequest(rawJson: String): ScrollResponse {
        return try {
            val request = json.decodeFromString(ScrollRequest.serializer(), rawJson)

            when (request.action) {
                "scroll" -> scrollToCaret(request.file, request.line, request.column)
                else -> ScrollResponse("error", "Unknown action: ${request.action}")
            }
        } catch (e: Exception) {
            logger.error("Failed to handle scroll request: $rawJson", e)
            ScrollResponse("error", e.message)
        }
    }

    /** Reads the current editor file path on the EDT, or null if no editor is open. */
    private data class EditorState(val filePath: String, val editor: Editor)

    private fun readEditorState(): EditorState? {
        var state: EditorState? = null
        ApplicationManager.getApplication().invokeAndWait {
            val editor = FileEditorManager.getInstance(project).selectedTextEditor
            if (editor != null) {
                val vFile = FileDocumentManager.getInstance().getFile(editor.document)
                if (vFile != null) {
                    state = EditorState(vFile.path, editor)
                }
            }
        }
        return state
    }

    /**
     * Suffix-based path matching: checks if either path ends with the other
     * when compared segment by segment from the end.
     */
    private fun pathMatches(candidate: String, request: String): Boolean {
        val candidateSegments = candidate.replace('\\', '/').trimEnd('/').split('/').filter { it.isNotEmpty() }
        val requestSegments = request.replace('\\', '/').trimEnd('/').split('/').filter { it.isNotEmpty() }
        if (candidateSegments.isEmpty() || requestSegments.isEmpty()) return false
        val minLen = minOf(candidateSegments.size, requestSegments.size)
        for (i in 1..minLen) {
            if (!candidateSegments[candidateSegments.size - i].equals(requestSegments[requestSegments.size - i], ignoreCase = true)) {
                return false
            }
        }
        return true
    }

    private suspend fun scrollToCaret(expectedFile: String, expectedLine: Int, expectedColumn: Int): ScrollResponse {
        val pollIntervalMs = 50L
        val maxWaitMs = 3000L
        var waited = 0L

        // Poll until the editor has the expected file open, then force-move
        // the caret.  We don't wait for the caret to propagate via Rd — it's
        // too slow / unreliable on WSL remote dev.
        while (waited < maxWaitMs) {
            val state = readEditorState()
            if (state != null && pathMatches(state.filePath, expectedFile)) {
                return forceScrollTo(state.editor, expectedLine, expectedColumn)
            }
            delay(pollIntervalMs)
            waited += pollIntervalMs
        }

        // Timeout — file never opened; scroll whatever is open
        logger.warn("SCROLL: timed out waiting for file=$expectedFile (waited ${waited}ms)")
        val state = readEditorState()
        return if (state != null) {
            forceScrollTo(state.editor, expectedLine, expectedColumn)
        } else {
            ScrollResponse("no_editor")
        }
    }

    private fun forceScrollTo(editor: Editor, line: Int, column: Int): ScrollResponse {
        val result = StringBuilder()
        ApplicationManager.getApplication().invokeAndWait {
            val targetLine = (line - 1).coerceIn(0, editor.document.lineCount - 1)
            editor.caretModel.moveToLogicalPosition(LogicalPosition(targetLine, column))
            val caret = editor.caretModel.logicalPosition
            result.append("scrolled to ${caret.line + 1}:${caret.column}")
            logger.info("SCROLL: forced caret to $line:$column, scrollToCaret at ${caret.line + 1}:${caret.column}")

            if (editor.scrollingModel.visibleArea.height > 0) {
                editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
            } else {
                logger.info("SCROLL: editor not laid out yet, waiting for resize")
                scheduleScrollOnResize(editor)
            }
        }
        return ScrollResponse("ok", result.toString())
    }

    private fun scheduleScrollOnResize(editor: com.intellij.openapi.editor.Editor) {
        val component = editor.contentComponent
        component.addComponentListener(object : java.awt.event.ComponentAdapter() {
            override fun componentResized(e: java.awt.event.ComponentEvent?) {
                if (editor.isDisposed) {
                    component.removeComponentListener(this)
                    return
                }
                if (editor.scrollingModel.visibleArea.height > 0) {
                    editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
                    component.removeComponentListener(this)
                    logger.info("SCROLL: scrolled via componentResized")
                }
            }
        })
    }

    fun stop() {
        if (!isRunning.getAndSet(false)) return

        logger.info("Stopping scroll server on port $port")
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            logger.warn("Error closing scroll server socket", e)
        }

        serverJob?.cancel()
        scope.cancel()
        serverSocket = null
        serverJob = null
    }

    companion object {
        const val DEFAULT_PORT = 8766
    }
}
