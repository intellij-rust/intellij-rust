/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.rust.RsBundle
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*

class ElideLifetimesFix(element: RsFunction) : RsQuickFixBase<RsFunction>(element) {
    override fun getText() = RsBundle.message("intention.name.elide.lifetimes")
    override fun getFamilyName() = text

    override fun invoke(project: Project, editor: Editor?, element: RsFunction) {
        LifetimeRemover().visitFunction(element)
    }
}

private class LifetimeRemover : RsVisitor() {
    private val boundsLifetimes = mutableListOf<RsLifetimeParameter>()

    override fun visitFunction(fn: RsFunction) {
        fn.typeParameterList?.let { visitTypeParameterList(it) }
        fn.valueParameterList?.let { visitValueParameterList(it) }
        fn.retType?.let { visitRetType(it) }
    }

    override fun visitTypeParameterList(typeParameters: RsTypeParameterList) {
        boundsLifetimes.addAll(typeParameters.lifetimeParameterList)
        val typeNames = typeParameters.getGenericParameters(includeLifetimes = false).map { it.text }
        if (typeNames.isEmpty()) {
            typeParameters.delete()
        } else {
            val types = RsPsiFactory(typeParameters.project).createTypeParameterList(typeNames)
            typeParameters.replace(types)
        }
    }

    override fun visitTypeArgumentList(typeArguments: RsTypeArgumentList) {
        super.visitTypeArgumentList(typeArguments)
        val restNames = typeArguments.getGenericArguments(includeLifetimes = false).map { it.text }
        if (restNames.isEmpty()) {
            typeArguments.delete()
        } else {
            val newTypeArguments = RsPsiFactory(typeArguments.project).createTypeArgumentList(restNames)
            typeArguments.replace(newTypeArguments)
        }
    }

    override fun visitValueParameterList(valueParameters: RsValueParameterList) {
        valueParameters.selfParameter?.let { visitSelfParameter(it) }
        valueParameters.valueParameterList.forEach { visitValueParameter(it) }
    }

    override fun visitSelfParameter(selfParameter: RsSelfParameter) {
        if (selfParameter.lifetime != null) {
            val newSelfParameter = RsPsiFactory(selfParameter.project).createSelfReference(selfParameter.mutability.isMut)
            selfParameter.replace(newSelfParameter)
        }
        selfParameter.typeReference?.accept(this)
    }

    override fun visitRefLikeType(refLike: RsRefLikeType) {
        refLike.typeReference?.let { visitTypeReference(it) }
        if (refLike.isRef && refLike.lifetime != null) {
            val ref = RsPsiFactory(refLike.project)
                .createReferenceType(refLike.typeReference?.text ?: "", refLike.mutability)
            refLike.replace(ref)
        }
    }

    override fun visitElement(element: RsElement) {
        element.childrenOfType<RsElement>().forEach { it.accept(this) }
    }
}
