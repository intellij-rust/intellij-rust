/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi

import com.intellij.lang.ASTNode
import com.intellij.psi.tree.ICompositeElementType
import com.intellij.psi.tree.IElementType
import org.rust.lang.RsLanguage

class RsElementType(s: String) : IElementType(s, RsLanguage), ICompositeElementType {
    override fun createCompositeNode(): ASTNode {
        return RsElementTypes.Factory.createElement(this)
    }
}
