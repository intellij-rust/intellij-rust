package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.impl.mixin.selfParameter
import org.rust.lang.core.psi.impl.mixin.valueParameters
import org.rust.lang.core.psi.util.contains
import org.rust.lang.core.psi.util.parentOfType

class UnElideLifetimesIntention : RustElementBaseIntentionAction<RsFunction>() {
    override fun getText() = "Un-elide lifetimes"
    override fun getFamilyName(): String = text

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): RsFunction? {
        val fn = element.parentOfType<RsFunction>() ?: return null
        val scope = element.parentOfType<RsBlock>()
        if (fn.contains(scope)) return null

        if ((fn.retType?.type as? RsRefLikeType)?.lifetime != null) return null

        val args = fn.allRefArgs

        if (args.isEmpty() || args.any { it.lifetime != null }) return null
        return fn
    }

    override fun invoke(project: Project, editor: Editor, ctx: RsFunction) {
        ctx.allRefArgs.asSequence().zip(nameGenerator).forEach {
            it.first.replace(createParam(project, it.first, it.second))
        }

        // generic params
        val genericParams = RustPsiFactory(project).createTypeParameterList(
            ctx.allRefArgs.mapNotNull { it.lifetime?.text } + ctx.typeParameters.map { it.text }
        )
        ctx.typeParameterList?.replace(genericParams) ?: ctx.addAfter(genericParams, ctx.identifier)

        // return type
        val retType = ctx.retType?.type as? RsRefLikeType ?: return

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
        RustPsiFactory(project).createType(origin.text.replaceFirst("&", "&$lifeTimeName ")) as RsRefLikeType

    private fun createParam(project: Project, origin: PsiElement, lifeTimeName: String): PsiElement =
        RustPsiFactory(project).createMethodParam(origin.text.replaceFirst("&", "&$lifeTimeName "))

    private val RsFunction.allRefArgs: List<PsiElement> get() {
        val selfAfg: List<PsiElement> = listOfNotNull(selfParameter)
        val params: List<PsiElement> = valueParameters
            .filter { param ->
                val type = param.type
                type is RsRefLikeType && type.and != null
            }
        return (selfAfg + params).filterNotNull()
    }

    private val PsiElement.lifetime: PsiElement? get() =
    when (this) {
        is RsSelfParameter -> lifetime
        is RsValueParameter -> (type as? RsRefLikeType)?.lifetime
        else -> null
    }
}
