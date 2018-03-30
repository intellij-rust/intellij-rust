/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsMacroBinding

abstract class RsMacroBindingImplMixin(node: ASTNode) : RsNamedElementImpl(node), RsMacroBinding {

    override fun getNameIdentifier(): PsiElement? = metaVarIdentifier
}

val RsMacroBinding.fragmentSpecifier: String?
    get() = identifier?.text
