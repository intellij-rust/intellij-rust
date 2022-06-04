/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.util.text.CharArrayUtil
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.psi.impl.RsFunctionImpl
import kotlin.math.max

class GenerateDocStubIntention : RsElementBaseIntentionAction<GenerateDocStubIntention.Context>() {
    override fun getText() = "Generate documentation stub"
    override fun getFamilyName() = text
    data class Context(
        val func: RsElement,
        val isUnsafe: Boolean,
    )

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val targetFunc = element.ancestorOrSelf<RsGenericDeclaration>() ?: return null
        if (targetFunc !is RsFunctionImpl) return null
        if (targetFunc.name != element.text) return null
        if (targetFunc.text.startsWith("///")) return null
        val unsafe = targetFunc.unsafe
        return Context(targetFunc, unsafe != null)
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val (targetFunc, isUnsafe) = ctx
        val document = editor.document
        var commentStartOffset: Int = targetFunc.textRange.startOffset
        val lineStartOffset = document.getLineStartOffset(document.getLineNumber(commentStartOffset))
        if (lineStartOffset in 1 until commentStartOffset) {
            val nonWhiteSpaceOffset = CharArrayUtil.shiftBackward(document.charsSequence, commentStartOffset - 1, " \t")
            commentStartOffset = max(nonWhiteSpaceOffset, lineStartOffset)
        }
        val buffer = StringBuilder()
        var commentBodyRelativeOffset = 0
        val indentOffset = targetFunc.startOffset - lineStartOffset
        val indentation = " ".repeat(indentOffset)
        buffer.append("$indentation/// \n")
        commentBodyRelativeOffset += buffer.length

        document.insertString(commentStartOffset, buffer)
        val docManager = PsiDocumentManager.getInstance(project)
        docManager.commitDocument(document)
        val stub = generateDocumentStub(indentation, isUnsafe)
        if (stub.isNotEmpty()) {
            val insertionOffset = commentStartOffset + commentBodyRelativeOffset
            document.insertString(insertionOffset, stub)
            docManager.commitDocument(document)
        }
        editor.caretModel.moveToOffset(targetFunc.startOffset + buffer.length - indentOffset - 1)
    }
}

private fun generateDocumentStub(
    indentation: String,
    isUnsafe: Boolean,
): String = buildString {
    append("$indentation///\n")
    if (isUnsafe) {
        append("$indentation/// # Safety\n")
        append("$indentation///\n")
        append("$indentation/// \n")
        append("$indentation///\n")
    }
    append("$indentation/// # Examples\n")
    append("$indentation/// \n")
    append("$indentation/// ```\n")
    append("$indentation/// \n")
    append("$indentation/// ```\n")
}
