/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import org.rust.lang.core.psi.RsUseSpeck
import org.rust.lang.core.stubs.RsUseSpeckStub

val RsUseSpeck.isStarImport: Boolean get() = stub?.isStarImport ?: (mul != null) // I hate operator precedence

abstract class RsUseSpeckImplMixin : RsStubbedElementImpl<RsUseSpeckStub>, RsUseSpeck {
    constructor (node: ASTNode) : super(node)
    constructor (stub: RsUseSpeckStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)
}
