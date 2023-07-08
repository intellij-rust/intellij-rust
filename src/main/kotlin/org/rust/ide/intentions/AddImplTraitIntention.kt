/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.codeInsight.CodeInsightUtilCore
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInsight.template.impl.MacroCallNode
import com.intellij.codeInsight.template.macro.CompleteMacro
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import org.rust.RsBundle
import org.rust.ide.fixes.insertGenericArgumentsIfNeeded
import org.rust.ide.refactoring.implementMembers.generateMissingTraitMembers
import org.rust.ide.utils.PsiInsertionPlace
import org.rust.ide.utils.template.newTemplateBuilder
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.openapiext.createSmartPointer

class AddImplTraitIntention : RsElementBaseIntentionAction<AddImplTraitIntention.Context>() {
    override fun getText() = RsBundle.message("intention.name.implement.trait")
    override fun getFamilyName() = text

    class Context(
        val type: RsStructOrEnumItemElement,
        val typeName: String,
        val placeForImpl: PsiInsertionPlace,
    )

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val struct = element.ancestorStrict<RsStructOrEnumItemElement>() ?: return null
        val typeName = struct.name ?: return null
        val placeForImpl = PsiInsertionPlace.forItemInTheScopeOf(struct) ?: return null
        return Context(struct, typeName, placeForImpl)
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val newImpl = RsPsiFactory(project).createTraitImplItem(
            ctx.typeName,
            "T",
            ctx.type.typeParameterList,
            ctx.type.whereClause
        )

        val insertedImpl = ctx.placeForImpl.insert(newImpl)
        val traitName = insertedImpl.traitRef?.path ?: return

        val implPtr = insertedImpl.createSmartPointer()
        editor.newTemplateBuilder(insertedImpl)
            .replaceElement(traitName, MacroCallNode(CompleteMacro()))
            .withDisabledDaemonHighlighting()
            .runInline {
                val implCurrent = implPtr.element
                if (implCurrent != null) {
                    runWriteAction {
                        afterTraitNameEntered(implCurrent, editor)
                    }
                }
            }
    }

    private fun afterTraitNameEntered(impl: RsImplItem, editor: Editor) {
        val traitRef = impl.traitRef ?: return
        val trait = traitRef.resolveToBoundTrait() ?: return

        val insertedGenericArgumentsPtr = if (trait.element.requiredGenericParameters.isNotEmpty()) {
            insertGenericArgumentsIfNeeded(traitRef.path)?.map { it.createSmartPointer() }
        } else {
            null
        }

        generateMissingTraitMembers(impl, traitRef, editor)

        showGenericArgumentsTemplate(
            editor,
            CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(impl) ?: return,
            insertedGenericArgumentsPtr
        )
    }

    private fun showGenericArgumentsTemplate(
        editor: Editor,
        impl: RsImplItem,
        insertedGenericArgumentsPtr: List<SmartPsiElementPointer<RsElement>>?
    ) {
        val insertedGenericArguments = insertedGenericArgumentsPtr?.mapNotNull { it.element }?.filterIsInstance<RsPathType>()
        if (!insertedGenericArguments.isNullOrEmpty()) {
            val members = impl.members ?: return
            val pathTypes = members.descendantsOfType<RsPath>()
                .filter { (it.parent is RsPathType || it.parent is RsPathExpr) && !it.hasColonColon && it.path == null && it.typeQual == null }
                .groupBy { it.referenceName }
            val typeToUsage = insertedGenericArguments.associateWith { ty ->
                ty.path.referenceName?.let { pathTypes[it] } ?: emptyList()
            }
            val tpl = editor.newTemplateBuilder(impl)
            for ((type, usages) in typeToUsage) {
                tpl.introduceVariable(type).apply {
                    for (usage in usages) {
                        replaceElementWithVariable(usage)
                    }
                }
            }
            tpl.withExpressionsHighlighting()
            tpl.withDisabledDaemonHighlighting()
            tpl.runInline()
        }
    }

    // No intention preview because user has to choose trait manually
    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo =
        IntentionPreviewInfo.EMPTY
}
