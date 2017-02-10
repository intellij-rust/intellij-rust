package org.rust.debugger.lang

import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.testFramework.LightVirtualFile
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.evaluation.EvaluationMode
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import org.rust.lang.RsFileType

class RsDebuggerEditorsProvider : XDebuggerEditorsProvider() {
    override fun getFileType(): FileType = RsFileType

    override fun createDocument(project: Project, text: String, sourcePosition: XSourcePosition?, mode: EvaluationMode): Document {
        val plainTextFile = LightVirtualFile("rs-debug-editor", text)
        return FileDocumentManager.getInstance().getCachedDocument(plainTextFile)!!
    }
}
