/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.navigation.goto

import com.intellij.codeInsight.navigation.actions.TypeDeclarationProvider
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsAbstractableOwner
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.owner
import org.rust.lang.core.psi.ext.parentFunction
import org.rust.lang.core.types.normType
import org.rust.lang.core.types.ty.*
import org.rust.lang.core.types.type

class RsTypeDeclarationProvider : TypeDeclarationProvider {

    override fun getSymbolTypeDeclarations(element: PsiElement): Array<PsiElement>? {
        val type = when (element) {
            is RsFunction -> element.retType?.typeReference?.normType
            is RsNamedFieldDecl -> element.typeReference?.normType
            is RsConstant -> element.typeReference?.normType
            is RsPatBinding -> element.type
            is RsSelfParameter -> when (val owner = element.parentFunction.owner) {
                is RsAbstractableOwner.Trait -> owner.trait.declaredType
                is RsAbstractableOwner.Impl -> owner.impl.typeReference?.normType
                else -> null
            }
            else -> null
        } ?: return null

        val typeDeclaration = type.baseTypeDeclaration() ?: return null
        return arrayOf(typeDeclaration)
    }

    private tailrec fun Ty.baseTypeDeclaration(): RsElement? {
        return when (this) {
            is TyAdt -> item
            is TyTraitObject -> traits.firstOrNull()?.element
            is TyTypeParameter -> {
                when (parameter) {
                    is TyTypeParameter.Named -> parameter.parameter
                    // TODO: support self type parameter
                    else -> null
                }
            }
            is TyProjection -> target.element
            is TyReference -> referenced.baseTypeDeclaration()
            is TyPointer -> referenced.baseTypeDeclaration()
            is TyArray -> base.baseTypeDeclaration()
            is TySlice -> elementType.baseTypeDeclaration()
            is TyAnon -> traits.firstOrNull()?.element
            else -> null
        }
    }
}
