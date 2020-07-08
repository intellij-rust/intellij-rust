/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.doc.psi

import com.intellij.psi.impl.source.tree.CompositeElement
import com.intellij.psi.tree.ICompositeElementType
import com.intellij.psi.tree.IElementType
import org.rust.lang.core.psi.RsTokenType

open class RsDocTokenType(debugName: String) : RsTokenType(debugName)

class RsDocCompositeTokenType(
    debugName: String,
    private val astFactory: (IElementType) -> CompositeElement
) : RsDocTokenType(debugName), ICompositeElementType {
    override fun createCompositeNode(): CompositeElement = astFactory(this)
}
