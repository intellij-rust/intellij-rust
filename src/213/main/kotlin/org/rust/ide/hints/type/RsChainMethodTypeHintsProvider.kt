/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.hints.type

import com.intellij.codeInsight.hints.InlayGroup

// BACKCOMPAT: 2021.2. Merge it with `RsChainMethodTypeHintsProviderBase`
@Suppress("UnstableApiUsage")
class RsChainMethodTypeHintsProvider : RsChainMethodTypeHintsProviderBase() {
    override val group: InlayGroup
        get() = InlayGroup.METHOD_CHAINS_GROUP
}
