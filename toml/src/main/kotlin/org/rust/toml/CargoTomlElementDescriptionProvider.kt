/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml

import com.intellij.codeInsight.highlighting.HighlightUsagesDescriptionLocation
import com.intellij.psi.ElementDescriptionLocation
import com.intellij.psi.ElementDescriptionProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.usageView.UsageViewLongNameLocation
import com.intellij.usageView.UsageViewNodeTextLocation
import com.intellij.usageView.UsageViewShortNameLocation
import com.intellij.usageView.UsageViewTypeLocation
import org.toml.lang.psi.TomlKey

class CargoTomlElementDescriptionProvider : ElementDescriptionProvider {
    override fun getElementDescription(element: PsiElement, location: ElementDescriptionLocation): String? {
        @Suppress("USELESS_IS_CHECK") // The check is needed for older TOML plugin versions
        return if (element is TomlKey && element is PsiNamedElement) {
            if (element.isFeatureDef) {
                when (location) {
                    is UsageViewShortNameLocation -> element.name

                    is UsageViewNodeTextLocation,
                    is UsageViewLongNameLocation,
                    is HighlightUsagesDescriptionLocation -> {
                        "Cargo feature \"${element.name}\""
                    }

                    is UsageViewTypeLocation -> "Cargo feature"
                    else -> null
                }
            } else {
                if (location is UsageViewTypeLocation) {
                    "Toml key"
                } else {
                    null
                }
            }
        } else {
            null
        }
    }
}
