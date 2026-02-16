package com.claudecode.navigator.frontend

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.ScrollType
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
data class ScrollRequest(val action: String)

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

    private fun handleRequest(rawJson: String): ScrollResponse {
        return try {
            val request = json.decodeFromString(ScrollRequest.serializer(), rawJson)

            when (request.action) {
                "scroll" -> scrollToCaret()
                else -> ScrollResponse("error", "Unknown action: ${request.action}")
            }
        } catch (e: Exception) {
            logger.error("Failed to handle scroll request: $rawJson", e)
            ScrollResponse("error", e.message ?: "Unknown error")
        }
    }

    private fun scrollToCaret(): ScrollResponse {
        val result = StringBuilder()

        ApplicationManager.getApplication().invokeAndWait {
            val editor = FileEditorManager.getInstance(project).selectedTextEditor
            if (editor != null) {
                val caret = editor.caretModel.logicalPosition
                editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
                result.append("scrolled to ${caret.line + 1}:${caret.column}")
                logger.info("SCROLL: scrollToCaret at ${caret.line + 1}:${caret.column}")

                // On first file open, the editor's AWT layout hasn't completed
                // (visibleArea.height == 0), so scrollToCaret is silently dropped.
                // Retry via invokeLater so each attempt runs after pending AWT
                // layout events on the EDT.
                if (editor.scrollingModel.visibleArea.height == 0) {
                    logger.info("SCROLL: editor not laid out yet, scheduling retries")
                    retryScroll(editor, 0)
                }
            } else {
                result.append("no-editor")
                logger.warn("SCROLL: no active text editor")
            }
        }

        val msg = result.toString()
        return if (msg == "no-editor") {
            ScrollResponse("error", msg)
        } else {
            ScrollResponse("ok", msg)
        }
    }

    private fun retryScroll(editor: com.intellij.openapi.editor.Editor, attempt: Int) {
        if (attempt > 20) {
            logger.warn("SCROLL: gave up after $attempt retries")
            return
        }
        ApplicationManager.getApplication().invokeLater {
            if (editor.isDisposed) return@invokeLater
            if (editor.scrollingModel.visibleArea.height == 0) {
                retryScroll(editor, attempt + 1)
            } else {
                editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
            }
        }
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
