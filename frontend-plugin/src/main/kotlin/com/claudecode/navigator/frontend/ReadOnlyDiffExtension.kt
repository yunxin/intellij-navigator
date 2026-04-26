package com.claudecode.navigator.frontend

import com.intellij.diff.DiffContext
import com.intellij.diff.DiffExtension
import com.intellij.diff.EditorDiffViewer
import com.intellij.diff.FrameDiffTool
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.tools.util.base.DiffViewerBase
import com.intellij.diff.tools.util.base.DiffViewerListener
import com.intellij.diff.util.DiffNotificationProvider
import com.intellij.diff.util.DiffUserDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project

class ReadOnlyDiffExtension : DiffExtension() {
    override fun onViewerCreated(
        viewer: FrameDiffTool.DiffViewer,
        context: DiffContext,
        request: DiffRequest,
    ) {
        val project = context.project ?: return
        val service = ReadOnlyEditorService.getInstance(project)
        if (!service.isEnabled()) return

        addReadOnlyNotification(project, service, request)
        lockDiffEditors(service, viewer)

        val listener = object : DiffViewerListener() {
            override fun onInit() {
                lockDiffEditors(service, viewer)
            }

            override fun onAfterRediff() {
                lockDiffEditors(service, viewer)
            }
        }
        (viewer as? DiffViewerBase)?.addListener(listener)

        ApplicationManager.getApplication().invokeLater {
            lockDiffEditors(service, viewer)
        }
    }

    private fun lockDiffEditors(
        service: ReadOnlyEditorService,
        viewer: FrameDiffTool.DiffViewer,
    ) {
        val editors = (viewer as? EditorDiffViewer)?.editors ?: return
        service.applyToDiffEditors(editors.filterIsInstance<Editor>())
    }

    private fun addReadOnlyNotification(
        project: Project,
        service: ReadOnlyEditorService,
        request: DiffRequest,
    ) {
        val existing = request.getUserData(DiffUserDataKeys.NOTIFICATION_PROVIDERS).orEmpty()
        val provider = DiffNotificationProvider {
            ReadOnlyEditorNotificationPanelFactory.create(project, service)
        }
        request.putUserData(DiffUserDataKeys.NOTIFICATION_PROVIDERS, existing + provider)
    }
}
