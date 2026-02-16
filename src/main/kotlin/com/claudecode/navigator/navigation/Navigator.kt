package com.claudecode.navigator.navigation

import com.claudecode.navigator.model.NavigationTarget
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.wm.WindowManager
import java.awt.Frame
import javax.swing.SwingUtilities

class Navigator(private val project: Project) {
    private val logger = Logger.getInstance(Navigator::class.java)
    private var activePopup: JBPopup? = null

    fun navigate(targets: List<NavigationTarget>) {
        if (targets.isEmpty()) {
            logger.warn("No targets to navigate to")
            return
        }

        cancelActivePopup()
        bringToFront()

        if (targets.size == 1) {
            navigateToTarget(targets.first())
        } else {
            showSelectorPopup(targets)
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

    fun navigateToTarget(target: NavigationTarget) {
        logger.info("Navigating to ${target.file.path}:${target.line + 1}")

        val descriptor = OpenFileDescriptor(project, target.file, target.line, target.column)
        val editor = FileEditorManager.getInstance(project).openTextEditor(descriptor, true)

        if (editor != null) {
            // openTextEditor handles caret + scroll through IntelliJ's navigation
            // infrastructure (which supports remote dev). However, on freshly opened
            // editors the scroll can be silently deferred. Retry using navigateIn()
            // (NOT raw editor.scrollTo) so the retry also goes through the proper
            // navigation layer that works across local and remote environments.
            retryNavigateIfNeeded(descriptor, editor, 0)
        } else {
            logger.warn("Cannot navigate to ${target.file.path}")
        }
    }

    /**
     * Verifies the target line is visible. If not, retries via
     * OpenFileDescriptor.navigateIn() which goes through IntelliJ's
     * navigation infrastructure (remote-dev aware), unlike raw
     * editor.scrollingModel.scrollTo() which only affects the backend.
     */
    private fun retryNavigateIfNeeded(descriptor: OpenFileDescriptor, editor: Editor, attempt: Int) {
        if (attempt > 20) {
            logger.warn("Gave up scrolling to line ${descriptor.line + 1} after $attempt attempts")
            return
        }

        val vOffset = editor.scrollingModel.verticalScrollOffset
        val viewportH = editor.scrollingModel.visibleArea.height
        val targetY = descriptor.line * editor.lineHeight
        val needsRetry = viewportH == 0 || (targetY < vOffset || targetY > vOffset + viewportH)

        if (needsRetry) {
            SwingUtilities.invokeLater {
                if (!descriptor.canNavigate()) return@invokeLater
                descriptor.navigateIn(editor)
                retryNavigateIfNeeded(descriptor, editor, attempt + 1)
            }
        }
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
