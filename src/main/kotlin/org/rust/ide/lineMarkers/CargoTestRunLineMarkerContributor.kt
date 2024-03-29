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
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiElement
import org.rust.cargo.icons.CargoIcons
import org.rust.cargo.runconfig.command.CargoExecutableRunConfigurationProducer
import org.rust.cargo.runconfig.test.CargoBenchRunConfigurationProducer
import org.rust.cargo.runconfig.test.CargoTestLocator
import org.rust.cargo.runconfig.test.CargoTestRunConfigurationProducer
import org.rust.cargo.runconfig.test.getDoctestCtx
import org.rust.lang.core.psi.RsElementTypes.IDENTIFIER
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.ext.RsNameIdentifierOwner
import org.rust.lang.core.psi.ext.RsQualifiedNamedElement
import org.rust.lang.core.psi.ext.elementType
import org.rust.lang.doc.psi.RsDocCodeFence
import org.rust.lang.doc.psi.RsDocCodeFenceStartEnd
import org.rust.lang.doc.psi.RsDocElementTypes.DOC_DATA
import javax.swing.Icon

class CargoTestRunLineMarkerContributor : RunLineMarkerContributor() {
    override fun getInfo(element: PsiElement): Info? {
        val target = when (element.elementType) {
            DOC_DATA -> {
                val parent = element.parent as? RsDocCodeFenceStartEnd ?: return null
                val fence = parent.parent as? RsDocCodeFence ?: return null
                if (fence.start != parent) return null
                fence
            }
            IDENTIFIER -> {
                val parent = element.parent
                if (parent !is RsNameIdentifierOwner || element != parent.nameIdentifier) return null
                if (parent is RsFunction && CargoExecutableRunConfigurationProducer.isMainFunction(parent)) return null
                parent
            }
            else -> return null
        }

        val state = CargoTestRunConfigurationProducer().findTestConfig(listOf(target), climbUp = false)
            ?: CargoBenchRunConfigurationProducer().findTestConfig(listOf(target), climbUp = false)
            ?: return null
        val icon = if (state.commandName == "test") {
            getTestStateIcon(state.sourceElement)
        } else {
            AllIcons.RunConfigurations.TestState.Run
        }
        return Info(
            icon,
            { state.configurationName },
            *ExecutorAction.getActions(1)
        )
    }

    companion object {
        fun getTestStateIcon(sourceElement: PsiElement): Icon? {
            val url = when (sourceElement) {
                is RsQualifiedNamedElement -> CargoTestLocator.getTestUrl(sourceElement)
                is RsDocCodeFence -> {
                    val ctx = sourceElement.getDoctestCtx() ?: return null
                    CargoTestLocator.getTestUrl(ctx)
                }
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
