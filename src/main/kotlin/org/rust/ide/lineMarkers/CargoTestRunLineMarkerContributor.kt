/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.lineMarkers

import com.intellij.execution.lineMarker.ExecutorAction
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiElement
import com.intellij.util.Function
import org.rust.cargo.runconfig.test.CargoTestRunConfigurationProducer
import org.rust.lang.core.psi.RsElementTypes.IDENTIFIER
import org.rust.lang.core.psi.ext.elementType

class CargoTestRunLineMarkerContributor : RunLineMarkerContributor() {
    override fun getInfo(element: PsiElement): Info? {
        if (element.elementType != IDENTIFIER) return null
        val parent = element.parent
        val state = CargoTestRunConfigurationProducer.findTest(parent, climbUp = false) ?: return null
        return Info(
            AllIcons.RunConfigurations.TestState.Run,
            Function<PsiElement, String> { state.configurationName },
            // `1` here will prefer test configuration over application configuration,
            // when both a applicable. Usually configurations are ordered by their target
            // PSI elements (smaller element means more specific), but this is not the case here.
            *ExecutorAction.getActions(1)
        )
    }
}
