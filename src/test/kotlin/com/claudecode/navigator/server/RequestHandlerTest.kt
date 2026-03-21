package com.claudecode.navigator.server

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class RequestHandlerTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/testData"

    private lateinit var requestHandler: RequestHandler

    override fun setUp() {
        super.setUp()
        myFixture.copyDirectoryToProject("project", "")
        requestHandler = RequestHandler(project)
    }

    fun `test file request without line activates file and omits coordinates`() {
        val response = requestHandler.handle("""{"type":"file","path":"utils.py"}""")

        assertEquals("ok", response.status)
        assertTrue(response.file!!.endsWith("/utils.py"))
        assertNull(response.line)
        assertNull(response.column)
        assertTrue(selectedFilePath().endsWith("/utils.py"))
    }

    fun `test file request without line preserves caret for already open file`() {
        val file = myFixture.findFileInTempDir("models/user.py")!!
        myFixture.openFileInEditor(file)

        val editor = selectedEditor()
        ApplicationManager.getApplication().invokeAndWait {
            editor.caretModel.moveToLogicalPosition(LogicalPosition(7, 4))
        }

        val response = requestHandler.handle("""{"type":"file","path":"models/user.py"}""")
        val caret = selectedEditor().caretModel.logicalPosition

        assertEquals("ok", response.status)
        assertTrue(response.file!!.endsWith("/models/user.py"))
        assertNull(response.line)
        assertNull(response.column)
        assertEquals(7, caret.line)
        assertEquals(4, caret.column)
    }

    fun `test file request with line still navigates and returns coordinates`() {
        val response = requestHandler.handle("""{"type":"file","path":"models/user.py","line":8}""")
        val caret = selectedEditor().caretModel.logicalPosition

        assertEquals("ok", response.status)
        assertTrue(response.file!!.endsWith("/models/user.py"))
        assertEquals(8, response.line)
        assertEquals(0, response.column)
        assertEquals(7, caret.line)
        assertEquals(0, caret.column)
    }

    fun `test file request with matchText only still navigates and returns coordinates`() {
        val response = requestHandler.handle(
            """{"type":"file","path":"models/user.py","matchText":"def delete(self):"}"""
        )
        val caret = selectedEditor().caretModel.logicalPosition

        assertEquals("ok", response.status)
        assertTrue(response.file!!.endsWith("/models/user.py"))
        assertEquals(11, response.line)
        assertEquals(0, response.column)
        assertEquals(10, caret.line)
        assertEquals(0, caret.column)
    }

    private fun selectedEditor() =
        FileEditorManager.getInstance(project).selectedTextEditor
            ?: error("Expected a selected text editor")

    private fun selectedFilePath(): String =
        FileDocumentManager.getInstance().getFile(selectedEditor().document)?.path
            ?: error("Expected selected editor to have a backing file")
}
