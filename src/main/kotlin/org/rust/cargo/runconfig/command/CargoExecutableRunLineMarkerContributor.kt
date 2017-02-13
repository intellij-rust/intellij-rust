package org.rust.cargo.runconfig.command

import com.intellij.execution.lineMarker.ExecutorAction
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiElement
import com.intellij.util.Function
import org.rust.lang.core.psi.RsElementTypes.IDENTIFIER
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.util.elementType

class CargoExecutableRunLineMarkerContributor : RunLineMarkerContributor() {
    override fun getInfo(element: PsiElement): Info? {
        if (element.elementType != IDENTIFIER) return null
        val fn = element.parent as? RsFunction ?: return null
        if (!CargoExecutableRunConfigurationProducer.isMainFunction(fn)) return null

        return Info(
            AllIcons.RunConfigurations.TestState.Run,
            Function<PsiElement, String> { "Run Application" },
            *ExecutorAction.getActions(0))
    }
}
