/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.template.postfix

import com.intellij.codeInsight.template.TemplateResultListener
import com.intellij.codeInsight.template.impl.MacroCallNode
import com.intellij.codeInsight.template.macro.CompleteMacro
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateWithExpressionSelector
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import org.rust.ide.utils.template.newTemplateBuilder
import org.rust.lang.core.parser.RustParserUtil
import org.rust.lang.core.psi.RsPathType
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.RsTypeReference
import org.rust.lang.core.psi.ext.startOffset
import org.rust.openapiext.createSmartPointer

class WrapTypePathPostfixTemplate(provider: RsPostfixTemplateProvider)
    : PostfixTemplateWithExpressionSelector(null, "wrap", "\$wrapper$<path>", RsTypeParentsSelector(), provider) {
    override fun expandForChooseExpression(element: PsiElement, editor: Editor) {
        val typeRef = element as? RsTypeReference ?: return

        val factory = RsPsiFactory(typeRef.project)
        val path = factory.tryCreatePath("Wrapper<${typeRef.text}>", RustParserUtil.PathParsingMode.TYPE) ?: return
        val newTypeRef = factory.tryCreateType(path.text) ?: return
        val inserted = typeRef.replace(newTypeRef) as? RsPathType ?: return
        val ptr = inserted.createSmartPointer()

        val template = editor.newTemplateBuilder(inserted) ?: return
        val name = ptr.element?.path?.referenceNameElement ?: return
        template.replaceElement(name, MacroCallNode(CompleteMacro()))
        template.withResultListener {
            // For some reason, the result is set to BrokenOff sometimes, even on successful template confirmation
            if (it != TemplateResultListener.TemplateResult.Canceled) {
                // Move caret after the inserted wrapper type
                val end = ptr.element?.path?.typeArgumentList?.gt
                if (end != null) {
                    editor.caretModel.moveToOffset(end.startOffset + 1)
                }
            }
        }
        template.runInline()
    }
}
