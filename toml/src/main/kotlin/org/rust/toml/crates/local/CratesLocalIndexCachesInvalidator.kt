/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.crates.local

import com.intellij.ide.caches.CachesInvalidator
import com.intellij.openapi.components.service

class CratesLocalIndexCachesInvalidator : CachesInvalidator() {
    override fun invalidateCaches() {
        service<CratesLocalIndexService>().invalidateCaches()
    }
}
