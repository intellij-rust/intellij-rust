/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.template.TemplateBuilderImpl
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.codeInsight.template.impl.ConstantNode
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapiext.Testmark
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.lang.core.psi.ext.descendantsOfType
import org.rust.lang.core.psi.ext.startOffset

class ImplTraitToTypeParamIntention : RsElementBaseIntentionAction<ImplTraitToTypeParamIntention.Context>() {
    override fun getText(): String = "Convert `impl Trait` to type parameter"
    override fun getFamilyName(): String = text

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val traitType = element.ancestorStrict<RsTraitType>() ?: return null
        if (traitType.impl == null) return null
        val fnParent = element.ancestorStrict<RsValueParameterList>()?.parent as? RsFunction ?: return null
        return Context(traitType, fnParent)
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val psiFactory = RsPsiFactory(project)
        val (argType, fnSignature) = ctx

        // can't convert outer `impl Trait` because inner one
        // will appear in type parameter constrains which is invalid
        if (argType.descendantsOfType<RsTraitType>().any { it.impl != null }) {
            outerImplTestMark.hit()
            HintManager.getInstance().showErrorHint(
                editor,
                "Please convert innermost `impl Trait` first",
                HintManager.UNDER)
            return
        }

        val typeParameterList = fnSignature
            .typeParameterList
            ?.apply {
                addBefore(psiFactory.createComma(), gt)
            }
            ?: fnSignature
                .addAfter(psiFactory.createTypeParameterList(""), fnSignature.identifier)
                as RsTypeParameterList

        val typeParameterName = "T"
        var typeParameter = psiFactory
            .createTypeParameterList("$typeParameterName:${argType.polyboundList.joinToString("+") { it.text }}")
            .typeParameterList
            .first()

        typeParameter = typeParameterList.addBefore(
            typeParameter,
            typeParameterList.gt)
            as RsTypeParameter
        val newArgType = argType.replace(psiFactory.createType(typeParameterName))

        PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.document)

        val tb = TemplateBuilderImpl(fnSignature)
        tb.replaceElement(typeParameter.identifier, "typeVar", ConstantNode(typeParameterName), true)
        tb.replaceElement(newArgType, "typeVar", null as String?, true)
        val template = tb.buildInlineTemplate()

        editor.caretModel.moveToOffset(fnSignature.startOffset)
        TemplateManager.getInstance(project).startTemplate(editor, template)
    }

    data class Context(
        val argType: RsTraitType,
        val fnSignature: RsFunction
    )

    companion object {
        val outerImplTestMark = Testmark("called on outer impl Trait")
    }

}
