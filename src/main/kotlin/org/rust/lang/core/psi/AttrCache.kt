/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi

import org.rust.lang.core.crate.Crate
import org.rust.lang.core.psi.ext.RsAttrProcMacroOwner
import java.util.*

/**
 * A simple single-thread cache used for caching of attribute macros
 * during per-file operations like highlighting
 */
sealed class AttrCache {
    abstract fun cachedGetProcMacroAttribute(owner: RsAttrProcMacroOwner): RsMetaItem?

    object NoCache : AttrCache() {
        override fun cachedGetProcMacroAttribute(owner: RsAttrProcMacroOwner): RsMetaItem? =
            owner.procMacroAttribute?.attr
    }

    class HashMapCache(
        private val crate: Crate?
    ): AttrCache() {
        private val cache: MutableMap<RsAttrProcMacroOwner, Optional<RsMetaItem>> = hashMapOf()

        override fun cachedGetProcMacroAttribute(owner: RsAttrProcMacroOwner): RsMetaItem? {
            return cache.getOrPut(owner) {
                Optional.ofNullable(ProcMacroAttribute.getProcMacroAttribute(owner, explicitCrate = crate)?.attr)
            }.orElse(null)
        }
    }
}
