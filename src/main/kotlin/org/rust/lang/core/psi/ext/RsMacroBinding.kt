/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.search.SearchScope
import org.rust.lang.core.psi.RsMacroBinding
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.RsPsiImplUtil.localOrMacroSearchScope

abstract class RsMacroBindingImplMixin(node: ASTNode) : RsNamedElementImpl(node), RsMacroBinding {

    override fun getNameIdentifier(): PsiElement? = metaVarIdentifier

    override fun setName(name: String): PsiElement? {
        nameIdentifier?.replace(RsPsiFactory(project).createMetavarIdentifier(name))
        return this
    }

    override fun getUseScope(): SearchScope {
        val owner = contextStrict<RsMacroDefinitionBase>() ?: error("Macro binding outside of a macro")
        return localOrMacroSearchScope(owner)
    }
}

val RsMacroBinding.fragmentSpecifier: String?
    get() = identifier?.text
