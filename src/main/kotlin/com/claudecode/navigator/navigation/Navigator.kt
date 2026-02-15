package com.claudecode.navigator.navigation

import com.claudecode.navigator.model.NavigationTarget
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager
import java.awt.Frame
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent

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
            val component = editor.contentComponent

            if (component.height > 0) {
                // Editor already laid out (file was already open) — scroll immediately.
                // openTextEditor doesn't re-apply the descriptor's line/column for
                // already-open files, so we must explicitly move caret and scroll.
                moveAndScroll(editor, pos)
            } else {
                // Editor not yet laid out (file just opened) — viewport is 0x0 so
                // scrollTo would compute offset 0 (top of file). Wait for Swing to
                // assign the real size, then scroll.
                component.addComponentListener(object : ComponentAdapter() {
                    override fun componentResized(e: ComponentEvent) {
                        component.removeComponentListener(this)
                        moveAndScroll(editor, pos)
                    }
                })
            }
        } else {
            logger.warn("Cannot navigate to ${target.file.path}")
        }
    }

    private fun moveAndScroll(editor: Editor, pos: LogicalPosition) {
        editor.caretModel.moveToLogicalPosition(pos)
        editor.scrollingModel.disableAnimation()
        editor.scrollingModel.scrollTo(pos, ScrollType.CENTER)
        editor.scrollingModel.enableAnimation()
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
