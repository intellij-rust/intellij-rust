/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import org.rust.lang.core.psi.RsLifetimeParameter

abstract class RsLifetimeParameterImplMixin(node: ASTNode) : RsNamedElementImpl(node), RsLifetimeParameter {

    override fun getNameIdentifier() = lifetimeDecl.quoteIdentifier

}
