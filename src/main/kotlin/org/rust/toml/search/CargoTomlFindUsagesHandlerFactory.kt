/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.search

import com.intellij.find.findUsages.FindUsagesHandler
import com.intellij.find.findUsages.FindUsagesHandlerFactory
import com.intellij.psi.PsiElement
import org.rust.toml.isFeatureDef
import org.rust.toml.tomlPluginIsAbiCompatible
import org.toml.lang.psi.TomlKeySegment

class CargoTomlFindUsagesHandlerFactory : FindUsagesHandlerFactory() {
    override fun canFindUsages(element: PsiElement): Boolean {
        if (!tomlPluginIsAbiCompatible()) return false
        return element is TomlKeySegment && element.isFeatureDef
    }

    override fun createFindUsagesHandler(element: PsiElement, forHighlightUsages: Boolean): FindUsagesHandler =
        object : FindUsagesHandler(element) {}
}
