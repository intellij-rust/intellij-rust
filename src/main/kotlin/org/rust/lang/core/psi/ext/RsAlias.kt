/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import org.rust.lang.core.psi.RsAlias
import org.rust.lang.core.stubs.RsAliasStub

abstract class RsAliasImplMixin : RsStubbedNamedElementImpl<RsAliasStub>, RsAlias {

    constructor(node: ASTNode) : super(node)

    constructor(stub: RsAliasStub, elementType: IStubElementType<*, *>) : super(stub, elementType)

    override fun getNameIdentifier(): PsiElement? {
        // `use Foo as _;` really should be unnamed, but "_" is not a valid name in rust, so I think it's ok
        return identifier ?: underscore
    }
}
