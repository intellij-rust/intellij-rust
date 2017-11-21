/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*

class UnElideLifetimesIntention : RsElementBaseIntentionAction<RsFunction>() {
    override fun getText() = "Un-elide lifetimes"
    override fun getFamilyName(): String = text

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): RsFunction? {
        val fn = element.ancestorOrSelf<RsFunction>(stopAt = RsBlock::class.java) ?: return null

        if ((fn.retType?.typeReference as? RsRefLikeType)?.lifetime != null) return null

        val args = fn.allRefArgs

        if (args.isEmpty() || args.any { it.lifetime != null }) return null
        return fn
    }

    override fun invoke(project: Project, editor: Editor, ctx: RsFunction) {
        ctx.allRefArgs.asSequence().zip(nameGenerator).forEach {
            it.first.replace(createParam(project, it.first, it.second))
        }

        // generic params
        val genericParams = RsPsiFactory(project).createTypeParameterList(
            ctx.allRefArgs.mapNotNull { it.lifetime?.text } + ctx.typeParameters.map { it.text }
        )
        ctx.typeParameterList?.replace(genericParams) ?: ctx.addAfter(genericParams, ctx.identifier)

        // return type
        val retType = ctx.retType?.typeReference?.typeElement as? RsRefLikeType ?: return

        if ((ctx.selfParameter != null) || (ctx.allRefArgs.drop(1).none())) {
            retType.replace(createRefType(project, retType, ctx.allRefArgs.first().lifetime!!.text))
        } else {
            val lifeTime = (retType.replace(createRefType(project, retType, "'unknown"))
                as RsRefLikeType).lifetime ?: return
            editor.selectionModel.setSelection(lifeTime.textRange.startOffset + 1, lifeTime.textRange.endOffset)
        }
    }

    private val nameGenerator = generateSequence(0) { it + 1 }.map {
        val abcSize = 'z' - 'a' + 1
        val letter = 'a' + it % abcSize
        val index = it / abcSize
        return@map if (index == 0) "'$letter" else "'$letter$index"
    }

    private fun createRefType(project: Project, origin: RsRefLikeType, lifeTimeName: String): RsRefLikeType =
        RsPsiFactory(project).createType(origin.text.replaceFirst("&", "&$lifeTimeName ")).typeElement as RsRefLikeType

    private fun createParam(project: Project, origin: PsiElement, lifeTimeName: String): PsiElement =
        RsPsiFactory(project).createMethodParam(origin.text.replaceFirst("&", "&$lifeTimeName "))

    private val RsFunction.allRefArgs: List<PsiElement> get() {
        val selfAfg: List<PsiElement> = listOfNotNull(selfParameter)
        val params: List<PsiElement> = valueParameters
            .filter { param ->
                val type = param.typeReference?.typeElement
                type is RsRefLikeType && type.isRef
            }
        return selfAfg + params
    }

    private val PsiElement.lifetime: RsLifetime? get() =
    when (this) {
        is RsSelfParameter -> lifetime
        is RsValueParameter -> (typeReference?.typeElement as? RsRefLikeType)?.lifetime
        else -> null
    }
}
