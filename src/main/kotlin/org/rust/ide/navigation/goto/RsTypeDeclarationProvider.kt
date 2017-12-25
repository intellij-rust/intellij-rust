/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.navigation.goto

import com.intellij.codeInsight.navigation.actions.TypeDeclarationProvider
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsConstant
import org.rust.lang.core.psi.RsFieldDecl
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsPatBinding
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.types.ty.*
import org.rust.lang.core.types.type

class RsTypeDeclarationProvider : TypeDeclarationProvider {

    override fun getSymbolTypeDeclarations(element: PsiElement): Array<PsiElement>? {
        val type = when (element) {
            is RsFunction -> element.retType?.typeReference?.type
            is RsFieldDecl -> element.typeReference?.type
            is RsConstant -> element.typeReference?.type
            is RsPatBinding -> element.type
            else -> null
        } ?: return null

        val typeDeclaration = type.baseTypeDeclaration() ?: return null
        return arrayOf(typeDeclaration)
    }

    private tailrec fun Ty.baseTypeDeclaration(): RsElement? {
        return when (this) {
            is TyStructOrEnumBase -> item
            is TyTraitObject -> trait.element
            is TyTypeParameter -> {
                when (parameter) {
                    is TyTypeParameter.Named -> parameter.parameter
                    is TyTypeParameter.AssociatedType -> parameter.target
                    // TODO: support self type parameter
                    else -> null
                }
            }
            is TyReference -> referenced.baseTypeDeclaration()
            is TyPointer -> referenced.baseTypeDeclaration()
            is TyArray -> base.baseTypeDeclaration()
            is TySlice -> elementType.baseTypeDeclaration()
            else -> null
        }
    }
}
