/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.lineMarkers

import com.intellij.execution.lineMarker.ExecutorAction
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiElement
import org.rust.cargo.runconfig.command.CargoExecutableRunConfigurationProducer
import org.rust.lang.core.psi.RsElementTypes.IDENTIFIER
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.ext.elementType
import org.rust.openapiext.isUnitTestMode

class CargoExecutableRunLineMarkerContributor : RunLineMarkerContributor() {
    override fun getInfo(element: PsiElement): Info? {
        if (element.elementType != IDENTIFIER) return null
        val fn = element.parent as? RsFunction ?: return null
        if (!CargoExecutableRunConfigurationProducer.isMainFunction(fn)) return null

        val actions = ExecutorAction.getActions(0)
        return Info(
            AllIcons.RunConfigurations.TestState.Run,
            { psiElement ->
                val texts = actions.mapNotNull { getText(it, psiElement) }
                if (isUnitTestMode) {
                    texts.firstOrNull().orEmpty()
                } else {
                    texts.joinToString("\n")
                }
            },
            *actions
        )
    }
}
