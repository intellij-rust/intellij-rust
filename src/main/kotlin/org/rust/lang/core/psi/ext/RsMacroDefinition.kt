/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.util.PsiTreeUtil
import org.rust.lang.core.psi.RsMacroArg
import org.rust.lang.core.psi.RsMacroDefinition
import org.rust.lang.core.psi.RsMacroInvocation
import org.rust.lang.core.stubs.RsMacroDefinitionStub

abstract class RsMacroDefinitionImplMixin : RsStubbedNamedElementImpl<RsMacroDefinitionStub>, RsMacroDefinition {

    constructor(node: ASTNode) : super(node)

    constructor(stub: RsMacroDefinitionStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getMacroInvocation(): RsMacroInvocation =
        PsiTreeUtil.getChildOfType(this, RsMacroInvocation::class.java)!!

    override fun getMacroArg(): RsMacroArg? = PsiTreeUtil.getChildOfType(this, RsMacroArg::class.java)

}
