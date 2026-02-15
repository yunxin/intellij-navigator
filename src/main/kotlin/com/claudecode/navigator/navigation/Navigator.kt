package com.claudecode.navigator.navigation

import com.claudecode.navigator.model.NavigationTarget
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.WindowManager
import java.awt.Frame

class Navigator(private val project: Project) {
    private val logger = Logger.getInstance(Navigator::class.java)

    /**
     * Navigates to the given targets.
     *
     * If there's a single target, navigates directly.
     * If there are multiple targets, shows a selector popup.
     *
     * @param targets The navigation targets to navigate to
     */
    fun navigate(targets: List<NavigationTarget>) {
        if (targets.isEmpty()) {
            logger.warn("No targets to navigate to")
            return
        }

        // Bring IDE to front
        bringToFront()

        if (targets.size == 1) {
            // Defer navigation so bringToFront() focus operations settle first.
            // Without this, OpenFileDescriptor.navigate() fires before the editor
            // is fully focused/laid out, causing it to open the file but not scroll
            // to the target line.
            ApplicationManager.getApplication().invokeLater {
                navigateToTarget(targets.first())
            }
        } else {
            showSelectorPopup(targets)
        }
    }

    /**
     * Navigates directly to a single target.
     */
    fun navigateToTarget(target: NavigationTarget) {
        logger.info("Navigating to ${target.file.path}:${target.line + 1}")

        val descriptor = OpenFileDescriptor(
            project,
            target.file,
            target.line,
            target.column
        )

        if (descriptor.canNavigate()) {
            descriptor.navigate(true)
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
            IdeFocusManager.getInstance(project).requestFocus(frame, true)
        }
    }
}
