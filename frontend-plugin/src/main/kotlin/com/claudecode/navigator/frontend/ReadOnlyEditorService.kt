package com.claudecode.navigator.frontend

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotifications
import java.util.Collections
import java.util.WeakHashMap

@Service(Service.Level.PROJECT)
class ReadOnlyEditorService(private val project: Project) : Disposable {
    private val logger = Logger.getInstance(ReadOnlyEditorService::class.java)
    private val lockedEditors = Collections.newSetFromMap(WeakHashMap<Editor, Boolean>())

    @Volatile
    private var started = false

    @Volatile
    private var enabled = true

    @Volatile
    private var notificationDismissed = false

    private val listener = object : EditorFactoryListener {
        override fun editorCreated(event: EditorFactoryEvent) {
            val editor = event.editor
            runOnEdt {
                applyToEditor(editor)
            }
        }

        override fun editorReleased(event: EditorFactoryEvent) {
            lockedEditors.remove(event.editor)
        }
    }

    fun start() {
        if (started) return
        started = true

        EditorFactory.getInstance().addEditorFactoryListener(listener, this)
        applyToOpenEditors()
        updateEditorNotifications()
        logger.info("Agent read-only editors enabled for project: ${project.name}")
    }

    fun isEnabled(): Boolean = enabled

    fun setEnabled(value: Boolean) {
        if (enabled == value) return

        enabled = value
        if (value) {
            notificationDismissed = false
            applyToOpenEditors()
            logger.info("Agent read-only editors enabled for project: ${project.name}")
        } else {
            unlockManagedEditors()
            logger.info("Agent read-only editors disabled for project: ${project.name}")
        }
        updateEditorNotifications()
    }

    private fun applyToOpenEditors() {
        runOnEdt {
            EditorFactory.getInstance().allEditors.forEach(::applyToEditor)
        }
    }

    private fun applyToEditor(editor: Editor) {
        if (!enabled || !shouldGuard(editor)) return

        val editorEx = editor as? EditorEx ?: return
        if (editorEx.isViewer) return

        editorEx.setViewer(true)
        lockedEditors.add(editorEx)
        logger.debug("Set editor viewer mode for ${editorEx.virtualFile?.path ?: "unknown file"}")
    }

    private fun unlockManagedEditors() {
        runOnEdt {
            val iterator = lockedEditors.iterator()
            while (iterator.hasNext()) {
                val editor = iterator.next()
                iterator.remove()

                if (editor.isDisposed) continue
                val editorEx = editor as? EditorEx ?: continue
                if (editorEx.isViewer) {
                    editorEx.setViewer(false)
                }
            }
        }
    }

    private fun shouldGuard(editor: Editor): Boolean {
        if (project.isDisposed || editor.isDisposed) return false
        if (editor.project != project) return false
        if (editor.editorKind != EditorKind.MAIN_EDITOR) return false

        val file = FileDocumentManager.getInstance().getFile(editor.document)
            ?: editor.virtualFile
            ?: return false

        return isGuardedFile(file)
    }

    fun isGuardedFile(file: VirtualFile): Boolean {
        if (!enabled || project.isDisposed) return false
        if (!file.isValid || file.isDirectory) return false
        return isUnderProjectBase(file.path)
    }

    fun shouldShowNotification(file: VirtualFile): Boolean {
        return !notificationDismissed && isGuardedFile(file)
    }

    fun dismissNotification() {
        if (notificationDismissed) return
        notificationDismissed = true
        updateEditorNotifications()
    }

    private fun isUnderProjectBase(filePath: String): Boolean {
        val basePath = project.basePath
            ?.replace('\\', '/')
            ?.trimEnd('/')
            ?: return false
        val candidate = filePath.replace('\\', '/').trimEnd('/')

        return if (SystemInfo.isWindows) {
            candidate.equals(basePath, ignoreCase = true) ||
                candidate.startsWith("$basePath/", ignoreCase = true)
        } else {
            candidate == basePath || candidate.startsWith("$basePath/")
        }
    }

    private fun runOnEdt(action: () -> Unit) {
        val application = ApplicationManager.getApplication()
        if (application.isDispatchThread) {
            action()
        } else {
            application.invokeLater {
                if (!project.isDisposed) action()
            }
        }
    }

    private fun updateEditorNotifications() {
        runOnEdt {
            EditorNotifications.getInstance(project).updateAllNotifications()
        }
    }

    override fun dispose() {
        unlockManagedEditors()
    }

    companion object {
        fun getInstance(project: Project): ReadOnlyEditorService {
            return project.getService(ReadOnlyEditorService::class.java)
        }
    }
}
