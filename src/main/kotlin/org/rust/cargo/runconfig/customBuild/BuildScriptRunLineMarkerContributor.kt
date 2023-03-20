/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.customBuild

import com.intellij.execution.lineMarker.ExecutorAction
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsElementTypes.IDENTIFIER
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.ext.elementType

class CustomBuildRunLineMarkerContributor : RunLineMarkerContributor() {
    override fun getInfo(element: PsiElement): Info? {
        if (element.elementType != IDENTIFIER) return null
        val fn = element.parent as? RsFunction ?: return null

        if (!CustomBuildRunConfigurationProducer.isBuildScriptMainFunction(fn)) return null

        val actions = ExecutorAction.getActions(0)
        return Info(
            AllIcons.RunConfigurations.TestState.Run,
            { psiElement -> actions.mapNotNull { getText(it, psiElement) }.joinToString("\n") },
            *actions
        )
    }
}
