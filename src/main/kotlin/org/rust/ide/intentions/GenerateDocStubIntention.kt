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
import org.rust.RsBundle
import org.rust.ide.intentions.util.macros.InvokeInside
import org.rust.lang.core.psi.RsValueParameter
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.psi.impl.RsFunctionImpl
import org.rust.lang.core.types.ty.Ty
import org.rust.openapiext.moveCaretToOffset
import kotlin.math.max

class GenerateDocStubIntention : RsElementBaseIntentionAction<GenerateDocStubIntention.Context>() {
    override fun getText() = RsBundle.message("intention.name.generate.documentation.stub")
    override fun getFamilyName() = text

    override val attributeMacroHandlingStrategy: InvokeInside get() = InvokeInside.MACRO_CALL

    data class Context(
        val func: RsElement,
        val params: List<RsValueParameter>,
        val returnType: Ty
    )

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val targetFunc = element.ancestorOrSelf<RsGenericDeclaration>() ?: return null
        if (targetFunc !is RsFunctionImpl) return null
        if (targetFunc.name != element.text) return null
        if (targetFunc.text.startsWith("///")) return null
        val params = targetFunc.valueParameters
        if (params.isEmpty()) {
            return null
        }
        val returnType = targetFunc.rawReturnType
        return Context(targetFunc, params, returnType)
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val (targetFunc, params, returnType) = ctx
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
        val stub = generateDocumentStub(indentation, params, returnType)
        if (stub.isNotEmpty()) {
            val insertionOffset = commentStartOffset + commentBodyRelativeOffset
            document.insertString(insertionOffset, stub)
            docManager.commitDocument(document)
        }
        editor.moveCaretToOffset(targetFunc, targetFunc.startOffset + buffer.length - indentOffset - 1)
    }
}

private fun generateDocumentStub(
    indentation: String,
    params: List<RsValueParameter>,
    returnType: Ty
): String = buildString {
    append("$indentation/// \n")
    append("$indentation/// # Arguments \n")
    append("$indentation/// \n")
    for (param in params) {
        append("$indentation/// * `${param.patText}`: \n")
    }
    append("$indentation/// \n")

    append("$indentation/// returns: $returnType \n")

    append("$indentation/// \n")
    append("$indentation/// # Examples \n")
    append("$indentation/// \n")
    append("$indentation/// ```\n")
    append("$indentation/// \n")
    append("$indentation/// ```\n")
}
