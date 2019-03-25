/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.clion.profiler.perf

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.profiler.api.BaseCallStackElement
import com.intellij.profiler.model.NativeCall
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.DocumentUtil
import org.rust.lang.core.psi.RsFunction
import java.nio.file.Paths

data class RsPerfNavigatableNativeCall(
    private val library: String,
    private val methodWithClassOrFunction: String
) : BaseCallStackElement() {

    constructor(library: String, methodWithClassOrFunction: String, filePath: String, line: Int)
        : this(library, methodWithClassOrFunction) {
        this.filePath = filePath
        this.line = line
    }

    private val nativeCall = NativeCall(library, methodWithClassOrFunction)
    private var filePath: String = ""
    private var line: Int = -1

    override fun fullName(): String = nativeCall.fullName()

    override val isNavigatable: Boolean
        get() = filePath.isNotBlank() && line >= 0

    override fun calcNavigatables(project: Project): Array<out NavigatablePsiElement> {
        val element = getNavigatablePsiElement(project)
        if (element == null) {
            LOG.warn("Failed to navigate: $filePath:$line")
            return emptyArray()
        }
        return arrayOf(element)
    }

    private fun getNavigatablePsiElement(project: Project): NavigatablePsiElement? {
        if (!isNavigatable) return null

        val virtualFile = VfsUtil.findFile(Paths.get(filePath), true) ?: return null
        return runReadAction {
            val document = FileDocumentManager.getInstance().getDocument(virtualFile) ?: return@runReadAction null
            val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document) ?: return@runReadAction null
            val offset = document.getLineStartOffset(line)
            if (!DocumentUtil.isValidOffset(offset, document)) return@runReadAction null
            val element = psiFile.findElementAt(offset)
            return@runReadAction PsiTreeUtil.getNonStrictParentOfType(element, RsFunction::class.java)
        }
    }

    companion object {
        private val LOG = Logger.getInstance(RsPerfNavigatableNativeCall::class.java)
    }
}
