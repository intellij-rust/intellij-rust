package org.rust.cargo.runconfig.test

import com.intellij.execution.TestStateStorage
import com.intellij.execution.lineMarker.ExecutorAction
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.execution.testframework.TestIconMapper
import com.intellij.execution.testframework.sm.runner.states.TestStateInfo
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.util.Function
import org.rust.lang.core.psi.RsElementTypes.IDENTIFIER
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.ext.isTest
import org.rust.lang.core.psi.ext.elementType
import javax.swing.Icon

class CargoTestRunLineMarkerContributor : RunLineMarkerContributor() {
    override fun getInfo(element: PsiElement): Info? {
        if (element.elementType != IDENTIFIER) return null
        val fn = element.parent as? RsFunction ?: return null
        return when {
            fn.isTest -> Info(
                getTestStateIcon(CargoTestLocator.getTestFnUrl(fn), fn.project),
                Function<PsiElement, String> { "Run Test" },
                // `1` here will prefer test configuration over application configuration,
                // when both a applicable. Usually configurations are ordered by their target
                // PSI elements (smaller element means more specific), but this is not the case here.
                *ExecutorAction.getActions(1)
            )

            else -> null
        }
    }

    private fun getTestStateIcon(url: String, project: Project): Icon? {
        val magnitude = TestStateStorage.getInstance(project).getState(url)
            ?.let { TestIconMapper.getMagnitude(it.magnitude) }

        return when (magnitude) {
            TestStateInfo.Magnitude.PASSED_INDEX,
            TestStateInfo.Magnitude.COMPLETE_INDEX ->
                AllIcons.RunConfigurations.TestState.Green2

            TestStateInfo.Magnitude.ERROR_INDEX,
            TestStateInfo.Magnitude.FAILED_INDEX ->
                AllIcons.RunConfigurations.TestState.Red2

            else -> AllIcons.RunConfigurations.TestState.Run
        }
    }
}
