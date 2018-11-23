/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.test

import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.RsMod
import org.rust.lang.core.psi.ext.isBench
import org.rust.lang.core.psi.ext.processExpandedItemsExceptImpls

class CargoBenchRunConfigurationProducer : CargoTestRunConfigurationProducerBase() {
    override val commandName: String = "bench"

    override fun isSuitable(element: RsElement): Boolean =
        when (element) {
            is RsMod -> hasBenchFunction(element)
            is RsFunction -> element.isBench
            else -> error("expected RsMod or RsFunction")
        }

    companion object {
        private fun hasBenchFunction(mod: RsMod): Boolean =
            mod.processExpandedItemsExceptImpls { it is RsFunction && it.isBench || it is RsMod && hasBenchFunction(it) }
    }
}
