/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.codeInsight.intention.FileModifier
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.rust.RsBundle
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*

class AddSelfFix(
    function: RsFunction,
    @FileModifier.SafeFieldForPreview
    private val selfType: SelfType = SelfType.Reference(false)
) : RsQuickFixBase<RsFunction>(function) {
    sealed class SelfType {
        class Pure(private val mutable: Boolean): SelfType() {
            override fun createSelfPsiElement(psiFactory: RsPsiFactory) = psiFactory.createSelf(mutable)
        }

        class Reference(private val mutable: Boolean): SelfType() {
            override fun createSelfPsiElement(psiFactory: RsPsiFactory) = psiFactory.createSelfReference(mutable)
        }

        class Adt(private val typeText: String): SelfType() {
            override fun createSelfPsiElement(psiFactory: RsPsiFactory) = psiFactory.createSelfWithType(typeText)
        }

        abstract fun createSelfPsiElement(psiFactory: RsPsiFactory): RsSelfParameter

        companion object {
            fun fromSelf(self: RsSelfParameter): SelfType {
                val selfType = self.typeReference
                return if (selfType != null) {
                    Adt(selfType.text)
                } else if (self.isRef) {
                    Reference(self.mutability.isMut)
                } else {
                    Pure(self.mutability.isMut)
                }
            }
        }
    }

    private val elementName: String = if (function.owner is RsAbstractableOwner.Impl) {
        "function"
    } else {
        "trait"
    }

    override fun getFamilyName() = RsBundle.message("intention.family.name.add.self.to", elementName)

    override fun getText() = RsBundle.message("intention.family.name.add.self.to", elementName)

    override fun invoke(project: Project, editor: Editor?, element: RsFunction) {
        val hasParameters = element.rawValueParameters.isNotEmpty()
        val psiFactory = RsPsiFactory(project)

        val valueParameterList = element.valueParameterList
        val lparen = valueParameterList?.firstChild

        val self = selfType.createSelfPsiElement(psiFactory)
        valueParameterList?.addAfter(self, lparen)

        if (hasParameters) {
            val parent = lparen?.parent
            parent?.addAfter(psiFactory.createComma(), parent.firstChild.nextSibling)
        }
    }
}
