/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.lineMarkers

import com.intellij.execution.TestStateStorage
import com.intellij.execution.lineMarker.ExecutorAction
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.execution.testframework.TestIconMapper
import com.intellij.execution.testframework.sm.runner.states.TestStateInfo
import com.intellij.psi.PsiElement
import com.intellij.util.Function
import org.rust.cargo.icons.CargoIcons
import org.rust.cargo.runconfig.test.CargoTestLocator
import org.rust.cargo.runconfig.test.CargoTestRunConfigurationProducer
import org.rust.lang.core.psi.RsElementTypes.IDENTIFIER
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.ext.RsMod
import org.rust.lang.core.psi.ext.elementType
import javax.swing.Icon

class CargoTestRunLineMarkerContributor : RunLineMarkerContributor() {
    override fun getInfo(element: PsiElement): Info? {
        if (element.elementType != IDENTIFIER) return null
        val parent = element.parent
        val state = CargoTestRunConfigurationProducer.findTest(arrayOf(parent), climbUp = false) ?: return null
        return Info(
            getTestStateIcon(state.sourceElement),
            Function<PsiElement, String> { state.configurationName },
            // `1` here will prefer test configuration over application configuration,
            // when both a applicable. Usually configurations are ordered by their target
            // PSI elements (smaller element means more specific), but this is not the case here.
            *ExecutorAction.getActions(1)
        )
    }

    companion object {
        fun getTestStateIcon(sourceElement: PsiElement): Icon? {
            val url = when (sourceElement) {
                is RsFunction -> CargoTestLocator.getTestFnUrl(sourceElement)
                is RsMod -> CargoTestLocator.getTestModUrl(sourceElement)
                else -> return null
            }

            val project = sourceElement.project

            val magnitude = TestStateStorage.getInstance(project).getState(url)
                ?.let { TestIconMapper.getMagnitude(it.magnitude) }

            return when (magnitude) {
                TestStateInfo.Magnitude.PASSED_INDEX,
                TestStateInfo.Magnitude.COMPLETE_INDEX ->
                    CargoIcons.TEST_GREEN

                TestStateInfo.Magnitude.ERROR_INDEX,
                TestStateInfo.Magnitude.FAILED_INDEX ->
                    CargoIcons.TEST_RED

                else -> CargoIcons.TEST
            }
        }
    }
}
