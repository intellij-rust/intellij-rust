/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.psi.PsiNameIdentifierOwner
import org.rust.lang.core.psi.RsConstant
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsImplItem
import org.rust.lang.core.psi.RsTypeAlias

interface RsAbstractable : RsNamedElement, PsiNameIdentifierOwner {
    val isAbstract: Boolean
}

// Resolve a const, fn or type in a impl block to the corresponding item in the trait block
val RsAbstractable.superItem: RsAbstractable?
    get() {
        val rustImplItem = ancestorStrict<RsImplItem>() ?: return null
        val superTrait = rustImplItem.traitRef?.resolveToTrait ?: return null
        return when (this) {
            is RsConstant -> superTrait.members?.constantList?.find { it.name == this.name }
            is RsFunction -> superTrait.members?.functionList?.find { it.name == this.name }
            is RsTypeAlias -> superTrait.members?.typeAliasList?.find { it.name == this.name }
            else -> error("unreachable")
        }
    }
