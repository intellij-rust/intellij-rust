/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.hints.codeVision

import com.intellij.psi.PsiElement
import org.rust.ide.statistics.RsCodeVisionUsageCollector.Companion.logUsagesClicked

class RsReferenceCodeVisionProvider : RsReferenceCodeVisionProviderBase() {
    @Suppress("UnstableApiUsage")
    // Overridden method 'logClickToFUS(com.intellij.psi.PsiElement)' is scheduled for removal in version 231.4840.387
    override fun logClickToFUS(element: PsiElement) {
        logUsagesClicked(element)
    }
}
