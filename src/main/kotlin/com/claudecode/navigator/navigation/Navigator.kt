package com.claudecode.navigator.navigation

import com.claudecode.navigator.model.NavigationTarget
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.wm.WindowManager
import java.awt.Frame

class Navigator(private val project: Project) {
    private val logger = Logger.getInstance(Navigator::class.java)
    private var activePopup: JBPopup? = null

    fun navigate(targets: List<NavigationTarget>): String {
        if (targets.isEmpty()) {
            logger.warn("No targets to navigate to")
            return "no-targets"
        }

        cancelActivePopup()
        bringToFront()

        return if (targets.size == 1) {
            navigateToTarget(targets.first())
        } else {
            showSelectorPopup(targets)
            "popup:${targets.size}"
        }
    }

    private fun cancelActivePopup() {
        activePopup?.let { popup ->
            if (!popup.isDisposed) {
                popup.cancel()
            }
        }
        activePopup = null
    }

    fun navigateToTarget(target: NavigationTarget): String {
        logger.info("Navigating to ${target.file.path}:${target.line + 1}")

        val descriptor = OpenFileDescriptor(project, target.file, target.line, target.column)

        // Open file and move caret to the target position. On remote dev (WSL),
        // the caret position propagates to the thin client via Rd protocol.
        // Scrolling does NOT propagate (IJPL-168107) — the companion frontend
        // plugin on the thin client handles scrollToCaret separately.
        val editor = FileEditorManager.getInstance(project)
            .openTextEditor(descriptor, true)

        val diag = if (editor != null) {
            val caret = editor.caretModel.logicalPosition
            val info = "caret=${caret.line + 1}:${caret.column}"
            logger.info("NAV_DIAG: $info")
            info
        } else {
            logger.warn("Cannot navigate to ${target.file.path}: no editor returned")
            "no-editor"
        }

        return diag
    }

    private fun showSelectorPopup(targets: List<NavigationTarget>) {
        logger.info("Showing selector popup for ${targets.size} targets")
        activePopup = SelectorPopup(project, targets) { selected ->
            activePopup = null
            navigateToTarget(selected)
        }.show()
    }

    private fun bringToFront() {
        val frame = WindowManager.getInstance().getFrame(project)
        if (frame != null) {
            if (frame.state == Frame.ICONIFIED) {
                frame.state = Frame.NORMAL
            }
            frame.toFront()
            frame.requestFocus()
        }
    }
}
