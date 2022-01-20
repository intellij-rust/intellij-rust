/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.hints.parameter

import com.intellij.codeInsight.hints.HintInfo
import com.intellij.codeInsight.hints.InlayInfo
import com.intellij.codeInsight.hints.InlayParameterHintsProvider
import com.intellij.codeInsight.hints.Option
import com.intellij.psi.PsiElement
import org.rust.lang.RsLanguage
import org.rust.lang.core.psi.RsCallExpr
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsMethodCall
import org.rust.lang.core.psi.RsPathExpr
import org.rust.lang.core.psi.ext.patText
import org.rust.lang.core.psi.ext.qualifiedName
import org.rust.lang.core.psi.ext.selfParameter
import org.rust.lang.core.psi.ext.valueParameters

@Suppress("UnstableApiUsage")
class RsInlayParameterHintsProvider : InlayParameterHintsProvider {
    override fun getSupportedOptions(): List<Option> = listOf(RsInlayParameterHints.smartOption)

    override fun getDefaultBlackList(): Set<String> = emptySet()

    override fun getHintInfo(element: PsiElement): HintInfo? {
        return when (element) {
            is RsCallExpr -> resolve(element)
            is RsMethodCall -> resolve(element)
            else -> null
        }
    }

    override fun getParameterHints(element: PsiElement): List<InlayInfo> = RsInlayParameterHints.provideHints(element)

    override fun getInlayPresentation(inlayText: String): String = inlayText

    override fun getBlacklistExplanationHTML(): String {
        return """
            To disable hints for a function use the appropriate pattern:<br />
            <b>std::*</b> - functions from the standard library<br />
            <b>std::fs::*(*, *)</b> - functions from the <i>std::fs</i> module with two parameters<br />
            <b>(*_)</b> - single parameter function where the parameter name ends with <i>_</i><br />
            <b>(key, value)</b> - functions with parameters <i>key</i> and <i>value</i><br />
            <b>*.put(key, value)</b> - <i>put</i> functions with <i>key</i> and <i>value</i> parameters
        """.trimIndent()
    }

    companion object {
        private fun resolve(call: RsCallExpr): HintInfo.MethodInfo? {
            val fn = (call.expr as? RsPathExpr)?.path?.reference?.resolve() as? RsFunction ?: return null
            val parameters = fn.valueParameters.map { it.patText ?: "_" }
            return createMethodInfo(fn, parameters)
        }

        private fun resolve(methodCall: RsMethodCall): HintInfo.MethodInfo? {
            val fn = methodCall.reference.resolve() as? RsFunction? ?: return null
            val parameters = listOfNotNull(fn.selfParameter?.name) + fn.valueParameters.map {
                it.patText ?: "_"
            }
            return createMethodInfo(fn, parameters)
        }

        private fun createMethodInfo(function: RsFunction, parameters: List<String>): HintInfo.MethodInfo? {
            val path = function.qualifiedName ?: return null
            return HintInfo.MethodInfo(path, parameters, RsLanguage)
        }
    }
}
