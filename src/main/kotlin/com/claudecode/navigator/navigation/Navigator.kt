package com.claudecode.navigator.navigation

import com.claudecode.navigator.model.NavigationTarget
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager
import java.awt.Frame

class Navigator(private val project: Project) {
    private val logger = Logger.getInstance(Navigator::class.java)

    fun navigate(targets: List<NavigationTarget>) {
        if (targets.isEmpty()) {
            logger.warn("No targets to navigate to")
            return
        }

        bringToFront()

        if (targets.size == 1) {
            navigateToTarget(targets.first())
        } else {
            showSelectorPopup(targets)
        }
    }

    fun navigateToTarget(target: NavigationTarget) {
        logger.info("Navigating to ${target.file.path}:${target.line + 1}")

        val descriptor = OpenFileDescriptor(project, target.file, target.line, target.column)
        val editor = FileEditorManager.getInstance(project).openTextEditor(descriptor, true)

        if (editor != null) {
            // Defer scroll — when a file is first opened the editor component
            // hasn't been through Swing layout yet (viewport is 0x0), so
            // scrollToCaret silently does nothing. Posting to the next EDT
            // cycle gives layout time to complete.
            ApplicationManager.getApplication().invokeLater {
                editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
            }
        } else {
            logger.warn("Cannot navigate to ${target.file.path}")
        }
    }

    private fun showSelectorPopup(targets: List<NavigationTarget>) {
        logger.info("Showing selector popup for ${targets.size} targets")
        SelectorPopup(project, targets) { selected ->
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
