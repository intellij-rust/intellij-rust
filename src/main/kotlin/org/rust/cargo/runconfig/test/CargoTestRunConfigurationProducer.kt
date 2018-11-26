/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.test

import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.RsMod
import org.rust.lang.core.psi.ext.isTest
import org.rust.lang.core.psi.ext.processExpandedItemsExceptImpls

class CargoTestRunConfigurationProducer : CargoTestRunConfigurationProducerBase() {
    override val commandName: String = "test"

    override fun isSuitable(element: RsElement): Boolean =
        when (element) {
            is RsMod -> hasTestFunction(element)
            is RsFunction -> element.isTest
            else -> error("expected RsMod or RsFunction")
        }

    companion object {
        private fun hasTestFunction(mod: RsMod): Boolean =
            mod.processExpandedItemsExceptImpls { it is RsFunction && it.isTest || it is RsMod && hasTestFunction(it) }
    }
}
