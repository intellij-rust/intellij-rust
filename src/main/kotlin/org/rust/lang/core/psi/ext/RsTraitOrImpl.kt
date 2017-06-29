/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.*
import org.rust.lang.core.types.BoundElement
import org.rust.lang.core.types.ty.substituteInValues

interface RsTraitOrImpl : RsItemElement, RsInnerAttributeOwner, RsGenericDeclaration {
    val constantList: List<RsConstant>
    val functionList: List<RsFunction>
    val implMacroMemberList: List<RsImplMacroMember>
    val typeAliasList: List<RsTypeAlias>
    val lbrace: PsiElement
    val rbrace: PsiElement?

    // For impls, this are `default` methods
    val inheritedFunctions: List<BoundElement<RsFunction>>

    val implementedTrait: BoundElement<RsTraitItem>?
}

val BoundElement<RsTraitOrImpl>.functionsWithInherited: List<BoundElement<RsFunction>> get() {
    return element.functionList.map { BoundElement(it, typeArguments) } +
        element.inheritedFunctions.map { BoundElement(it.element, it.typeArguments.substituteInValues(typeArguments)) }
}
