/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi

import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.tree.IElementType
import org.rust.lang.core.psi.ext.RsElement

/** Contains constants from [RsElementTypes] with correct types */
object RsStubElementTypes {
    val INCLUDE_MACRO_ARGUMENT: StubElementType<RsIncludeMacroArgument> = cast(RsElementTypes.INCLUDE_MACRO_ARGUMENT)
    val USE_GROUP: StubElementType<RsUseGroup> = cast(RsElementTypes.USE_GROUP)
    val ENUM_BODY: StubElementType<RsEnumBody> = cast(RsElementTypes.ENUM_BODY)
    val BLOCK_FIELDS: StubElementType<RsBlockFields> = cast(RsElementTypes.BLOCK_FIELDS)
    val VIS_RESTRICTION: StubElementType<RsVisRestriction> = cast(RsElementTypes.VIS_RESTRICTION)
}

/** Is needed to avoid very long type names */
private typealias StubElementType<T> = IStubElementType<StubElement<T>, T>

@Suppress("UNCHECKED_CAST")
private fun <T : RsElement?> cast(type: IElementType): IStubElementType<StubElement<T>, T> {
    return type as IStubElementType<StubElement<T>, T>
}
