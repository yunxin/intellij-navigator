package com.claudecode.navigator.navigation

import com.claudecode.navigator.model.NavigationTarget
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager
import java.awt.Frame
import javax.swing.SwingUtilities

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
            val pos = LogicalPosition(target.line, target.column)
            moveAndScroll(editor, pos, 0)
        } else {
            logger.warn("Cannot navigate to ${target.file.path}")
        }
    }

    /**
     * Moves the caret and scrolls the viewport to center the target line.
     *
     * On a freshly opened editor, ScrollingModelImpl has an internal deferred
     * state that silently swallows scrollTo calls. We detect this by checking
     * whether the target line is actually visible after scrolling. If not, we
     * retry on the next EDT cycle until the editor finishes initialization and
     * the scroll takes effect.
     */
    private fun moveAndScroll(editor: Editor, pos: LogicalPosition, attempt: Int) {
        if (attempt > 20) {
            logger.warn("Gave up scrolling to $pos after $attempt attempts")
            return
        }

        editor.caretModel.moveToLogicalPosition(pos)
        editor.scrollingModel.disableAnimation()
        editor.scrollingModel.scrollTo(pos, ScrollType.CENTER)
        editor.scrollingModel.enableAnimation()

        // Verify the scroll actually took effect
        val vOffset = editor.scrollingModel.verticalScrollOffset
        val viewportH = editor.scrollingModel.visibleArea.height
        val targetY = pos.line * editor.lineHeight
        val needsRetry = viewportH == 0 || (targetY < vOffset || targetY > vOffset + viewportH)
        if (needsRetry) {
            // Either viewport isn't ready (height=0) or target line is not
            // visible (scroll was swallowed by deferred state). Retry.
            SwingUtilities.invokeLater {
                moveAndScroll(editor, pos, attempt + 1)
            }
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
