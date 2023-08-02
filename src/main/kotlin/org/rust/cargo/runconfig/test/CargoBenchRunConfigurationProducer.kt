/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.test

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsModDeclItem
import org.rust.lang.core.psi.ext.RsMod
import org.rust.lang.core.psi.ext.isBench
import org.rust.lang.core.psi.ext.processExpandedItemsExceptImplsAndUses

class CargoBenchRunConfigurationProducer : CargoTestRunConfigurationProducerBase() {
    override val commandName: String = "bench"

    init {
        registerConfigProvider { elements, climbUp -> createConfigFor<RsModDeclItem>(elements, climbUp) }
        registerConfigProvider { elements, climbUp -> createConfigFor<RsFunction>(elements, climbUp) }
        registerConfigProvider { elements, climbUp -> createConfigFor<RsMod>(elements, climbUp) }
        registerConfigProvider { elements, climbUp -> createConfigForMultipleFiles(elements, climbUp) }
        registerDirectoryConfigProvider { dir -> createConfigForDirectory(dir) }
    }

    override fun isSuitable(element: PsiElement): Boolean {
        if (!super.isSuitable(element)) return false
        return when (element) {
            is RsMod -> hasBenchFunction(element)
            is RsFunction -> element.isBench
            else -> false
        }
    }

    companion object {
        private fun hasBenchFunction(mod: RsMod): Boolean =
            mod.processExpandedItemsExceptImplsAndUses { it is RsFunction && it.isBench || it is RsMod && hasBenchFunction(it) }
    }
}
