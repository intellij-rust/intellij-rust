package org.rust.ide.intentions

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.util.contains
import org.rust.lang.core.psi.util.parentOfType

class UnElideLifetimesIntention : PsiElementBaseIntentionAction() {
    override fun getText() = "Un-elide lifetimes"

    override fun getFamilyName(): String = text

    private val nameGenerator = generateSequence(0) { it + 1 }.map {
        val abcSize = 'z' - 'a' + 1
        val letter = 'a' + it % abcSize
        val index = it / abcSize
        return@map if (index == 0) "'$letter" else "'$letter$index"
    }

    override fun isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean {
        val fnDecl = findFnDecl(element) ?: return false

        if ((fnDecl.retType?.type as? RustRefTypeElement)?.lifetime != null)
            return false

        val types = fnDecl.allParamsRefTypes

        return types.any() && types.all { it.lifetime == null }
    }

    override fun invoke(project: Project, editor: Editor, element: PsiElement) {
        val fnDecl = checkNotNull(findFnDecl(element))

        // function params
        fnDecl.allParamsRefTypes.zip(nameGenerator).forEach {
            it.first.replace(createRefType(project, it.first, it.second))
        }

        // generic params
        val genericParams = RustPsiFactory(project).createGenericParams(
            (fnDecl.allParamsRefTypes.mapNotNull { it.lifetime?.text } +
                (fnDecl.genericParams?.typeParamList?.asSequence()?.map { it.text } ?: emptySequence()))
                .toList()
        )
        fnDecl.genericParams?.replace(genericParams) ?: fnDecl.addAfter(genericParams, fnDecl.identifier)

        // return type
        val retType = fnDecl.retType?.type as? RustRefTypeElement ?: return

        val parameters = checkNotNull(fnDecl.parameters)
        if ((parameters.selfArgument != null) || (fnDecl.allParamsRefTypes.drop(1).none())) {
            retType.replace(createRefType(project, retType, fnDecl.allParamsRefTypes.first().lifetime!!.text))
        } else {
            val lifeTime = (retType.replace(createRefType(project, retType, "'unknown"))
                as RustRefTypeElement).lifetime ?: return
            editor.selectionModel.setSelection(lifeTime.textRange.startOffset + 1, lifeTime.textRange.endOffset)
        }
    }

    fun findFnDecl(element: PsiElement): RustFnItemElement? {
        val fnItem = element.parentOfType<RustFnItemElement>() ?: return null
        val scope = element.parentOfType<RustBlockElement>()

        return if (fnItem.contains(scope)) null else fnItem
    }

    private fun createRefType(project: Project, origin: RustRefTypeElement, lifeTimeName: String): RustRefTypeElement =
        RustPsiFactory(project).createType(origin.text.replaceFirst("&", "&$lifeTimeName ")) as RustRefTypeElement

    private val RustFnItemElement.allParamsRefTypes: Sequence<RustRefTypeElement> get() {
        val selfAfgType = sequenceOf(parameters?.selfArgument?.type)
        val paramsTypes = parameters?.parameterList?.asSequence()?.mapNotNull { it.type } ?: emptySequence<RustTypeElement>()
        return (selfAfgType + paramsTypes).mapNotNull { it as? RustRefTypeElement }
    }
}
