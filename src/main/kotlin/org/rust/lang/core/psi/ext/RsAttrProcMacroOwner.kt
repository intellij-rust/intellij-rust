/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import org.rust.lang.core.psi.ProcMacroAttribute
import org.rust.lang.core.psi.RsMetaItem
import org.rust.lang.core.stubs.common.RsAttrProcMacroOwnerPsiOrStub

/**
 * A common interface for PSI elements that can hold attribute or derive procedural macro attributes.
 *
 * @see org.rust.lang.core.stubs.RsAttrProcMacroOwnerStub
 */
interface RsAttrProcMacroOwner : RsOuterAttributeOwner, RsAttrProcMacroOwnerPsiOrStub<RsMetaItem> {
    /** @see ProcMacroAttribute */
    val procMacroAttribute: ProcMacroAttribute<RsMetaItem>?
        get() = ProcMacroAttribute.getProcMacroAttribute(this)

    /** @see ProcMacroAttribute */
    val procMacroAttributeWithDerives: ProcMacroAttribute<RsMetaItem>?
        get() = ProcMacroAttribute.getProcMacroAttribute(this, withDerives = true)
}
