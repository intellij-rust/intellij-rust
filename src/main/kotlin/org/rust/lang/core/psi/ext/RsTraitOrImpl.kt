/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.*
import org.rust.lang.core.types.BoundElement

interface RsTraitOrImpl : RsItemElement, RsInnerAttributeOwner, RsGenericDeclaration {
    val constantList: List<RsConstant>
    val functionList: List<RsFunction>
    val implMacroMemberList: List<RsImplMacroMember>
    val typeAliasList: List<RsTypeAlias>
    val lbrace: PsiElement
    val rbrace: PsiElement?

    // For impls, this are `default` methods
    val inheritedFunctions: List<RsFunction>

    val implementedTrait: BoundElement<RsTraitItem>?
}

val RsTraitOrImpl.functionsWithInherited: List<RsFunction> get() = functionList + inheritedFunctions
