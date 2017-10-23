/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import org.rust.ide.icons.RsIcons
import org.rust.lang.core.psi.RsElementTypes
import org.rust.lang.core.psi.RsMacroDefinition
import org.rust.lang.core.stubs.RsMacroDefinitionStub
import javax.swing.Icon

abstract class RsMacroDefinitionImplMixin : RsStubbedNamedElementImpl<RsMacroDefinitionStub>,
                                            RsMacroDefinition {

    constructor(node: ASTNode) : super(node)

    constructor(stub: RsMacroDefinitionStub, elementType: IStubElementType<*, *>) : super(stub, elementType)

    override fun getNameIdentifier(): PsiElement? =
        findChildrenByType<PsiElement>(RsElementTypes.IDENTIFIER)
            .getOrNull(1) // Zeroth is `macro_rules` itself

    override fun getIcon(flags: Int): Icon? = RsIcons.MACRO
}

val RsMacroDefinition.hasMacroExport: Boolean
    get() = queryAttributes.hasAttribute("macro_export")
