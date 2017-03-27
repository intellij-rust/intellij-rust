package org.rust.cargo.runconfig.command

import com.intellij.execution.lineMarker.ExecutorAction
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.psi.PsiElement
import com.intellij.util.Function
import org.rust.lang.core.psi.RsElementTypes.IDENTIFIER
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.ext.elementType

class CargoExecutableRunLineMarkerContributor : RunLineMarkerContributor() {
    override fun getInfo(element: PsiElement): Info? {
        if (element.elementType != IDENTIFIER) return null
        val fn = element.parent as? RsFunction ?: return null
        if (!CargoExecutableRunConfigurationProducer.isMainFunction(fn)) return null

        // BACKCOMPAT: 2016.3
        // See https://youtrack.jetbrains.com/issue/KT-17090
        @Suppress("UNCHECKED_CAST")
        val actions: Array<AnAction> = ExecutorAction::class.java.getMethod("getActions", Integer.TYPE)
            .invoke(null, 0) as Array<AnAction>

        return Info(
            AllIcons.RunConfigurations.TestState.Run,
            Function<PsiElement, String> { psiElement ->
                actions.mapNotNull { getText(it, psiElement) }.joinToString("\n")
            },
            *actions)
    }
}
