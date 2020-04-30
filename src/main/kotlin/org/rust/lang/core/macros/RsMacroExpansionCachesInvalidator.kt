/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import com.intellij.ide.caches.CachesInvalidator

class RsMacroExpansionCachesInvalidator : CachesInvalidator() {
    override fun invalidateCaches() {
        try {
            MacroExpansionManager.invalidateCaches()
        } catch (e: Exception) {
            MACRO_LOG.warn(e)
        }
    }

}
