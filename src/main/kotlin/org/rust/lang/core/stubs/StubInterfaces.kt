/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.stubs

import com.intellij.util.BitUtil
import org.rust.lang.core.psi.ext.QueryAttributes
import org.rust.lang.core.psi.ext.RsDocAndAttributeOwner
import org.rust.lang.core.psi.ext.name
import org.rust.lang.core.psi.ext.queryAttributes
import org.rust.stdext.makeBitMask

interface RsNamedStub {
    val name: String?
}

/**
 * These properties are stored in stubs for performance reasons: it's much cheaper to check
 * a flag in a stub then traverse a PSI
 */
interface RsAttributeOwnerStub {
    val hasAttrs: Boolean

    // #[cfg()]
    val hasCfg: Boolean

    // #[macro_use]
    val hasMacroUse: Boolean

    companion object {
        val ATTRS_MASK: Int = makeBitMask(0)
        val CFG_MASK: Int = makeBitMask(1)
        val HAS_MACRO_USE_MASK: Int = makeBitMask(2)
        const val USED_BITS: Int = 3

        fun extractFlags(element: RsDocAndAttributeOwner): Int =
            extractFlags(element.queryAttributes)

        fun extractFlags(attrs: QueryAttributes): Int {
            var hasAttrs = false
            var hasCfg = false
            var hasMacroUse = false
            for (meta in attrs.metaItems) {
                hasAttrs = true
                when (meta.name) {
                    "cfg" -> hasCfg = true
                    "macro_use" -> hasMacroUse = true
                    // TODO cfg_attr
                }
                if (hasCfg && hasMacroUse) break
            }
            var flags = 0
            flags = BitUtil.set(flags, ATTRS_MASK, hasAttrs)
            flags = BitUtil.set(flags, CFG_MASK, hasCfg)
            flags = BitUtil.set(flags, HAS_MACRO_USE_MASK, hasMacroUse)
            return flags
        }
    }
}
