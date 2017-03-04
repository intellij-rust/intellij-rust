package org.rust.cargo.runconfig.test

import com.intellij.execution.lineMarker.ExecutorAction
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiElement
import com.intellij.util.Function
import org.rust.lang.core.psi.RsElementTypes.IDENTIFIER
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.ext.isTest
import org.rust.lang.core.psi.ext.elementType

class CargoTestRunLineMarkerContributor : RunLineMarkerContributor() {
    override fun getInfo(element: PsiElement): Info? {
        if (element.elementType != IDENTIFIER) return null
        val fn = element.parent as? RsFunction ?: return null
        return when {
            fn.isTest -> Info(
                AllIcons.RunConfigurations.TestState.Green2,
                Function<PsiElement, String> { "Run Test" },
                // `1` here will prefer test configuration over application configuration,
                // when both a applicable. Usually configurations are ordered by their target
                // PSI elements (smaller element means more specific), but this is not the case here.
                *ExecutorAction.getActions(1)
            )

            else -> null
        }
    }
}
