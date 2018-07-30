/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.fixes

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.childrenOfType
import org.rust.lang.core.psi.ext.isRef
import org.rust.lang.core.psi.ext.mutability

class ElideLifetimesFix : LocalQuickFix {
    override fun getName() = "Elide lifetimes"
    override fun getFamilyName() = name

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val fn = descriptor.psiElement as? RsFunction ?: return
        LifetimeRemover().visitFunction(fn)
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
        val typeNames = typeParameters.typeParameterList.map { it.text }
        if (typeNames.isEmpty()) {
            typeParameters.delete()
        } else {
            val types = RsPsiFactory(typeParameters.project).createTypeParameterList(typeNames)
            typeParameters.replace(types)
        }
    }

    override fun visitTypeArgumentList(typeArguments: RsTypeArgumentList) {
        super.visitTypeArgumentList(typeArguments)
        val restNames = typeArguments.typeReferenceList.map { it.text } +
            typeArguments.assocTypeBindingList.map { it.text }
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
            val newSelfParameter = RsPsiFactory(selfParameter.project).createSelf(selfParameter.mutability.isMut)
            selfParameter.replace(newSelfParameter)
        }
        selfParameter.typeReference?.let { visitTypeReference(it) }
    }

    override fun visitRefLikeType(refLike: RsRefLikeType) {
        visitTypeReference(refLike.typeReference)
        if (refLike.isRef && refLike.lifetime != null) {
            val ref = RsPsiFactory(refLike.project)
                .createReferenceType(refLike.typeReference.text, refLike.mutability.isMut)
            refLike.replace(ref)
        }
    }

    override fun visitElement(element: RsElement) {
        element.childrenOfType<RsElement>().forEach { it.accept(this) }
    }
}
