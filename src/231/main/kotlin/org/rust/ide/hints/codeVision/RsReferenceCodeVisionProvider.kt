/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.hints.codeVision

import com.intellij.psi.PsiElement
import org.rust.ide.statistics.RsCodeVisionUsageCollector.Companion.logUsagesClicked

// BACKCOMPAT: 2022.3. Get rid of `RsReferenceCodeVisionProviderBase` and leave this implementation only
class RsReferenceCodeVisionProvider : RsReferenceCodeVisionProviderBase() {
    override fun logClickToFUS(element: PsiElement, hint: String) {
        logUsagesClicked(element)
    }
}
