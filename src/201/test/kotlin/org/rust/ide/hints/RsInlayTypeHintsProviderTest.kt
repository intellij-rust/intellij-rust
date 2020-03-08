/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.hints

import com.intellij.codeInsight.hints.LinearOrderInlayRenderer

// BACKOMPAT: 2019.3. Merge it with RsInlayTypeHintsProviderTestBase
class RsInlayTypeHintsProviderTest : RsInlayTypeHintsProviderTestBase() {

    override fun checkInlays() {
        myFixture.testInlays(
            { (it.renderer as LinearOrderInlayRenderer<*>).toString() },
            { it.renderer is LinearOrderInlayRenderer<*> }
        )
    }
}
