package com.claudecode.navigator.frontend

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import java.util.function.Function
import javax.swing.JComponent

class ReadOnlyEditorNotificationProvider : EditorNotificationProvider {
    override fun collectNotificationData(
        project: Project,
        file: VirtualFile
    ): Function<in FileEditor, out JComponent?>? {
        val service = ReadOnlyEditorService.getInstance(project)
        if (!service.shouldShowNotification(file)) return null

        return Function {
            EditorNotificationPanel().apply {
                text = "Agent read-only editors are on. Direct typing is disabled; use Tools > Agent Read-Only Editors or Find Action (Ctrl/Cmd+Shift+A) to disable."
                createActionLabel("How to Disable") {
                    Messages.showInfoMessage(
                        project,
                        "To allow direct editing, disable Agent Read-Only Editors from Tools > Agent Read-Only Editors, or open Find Action with Ctrl+Shift+A on Windows/Linux or Cmd+Shift+A on macOS and search for Agent Read-Only Editors.",
                        "Agent Read-Only Editors",
                    )
                }
                createActionLabel("Disable Agent Read-Only Editors") {
                    service.setEnabled(false)
                }
                createActionLabel("Dismiss") {
                    service.dismissNotification()
                }
            }
        }
    }
}
