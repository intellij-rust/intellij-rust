/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.suggested

import com.intellij.refactoring.changeSignature.ParameterInfo.NEW_PARAMETER
import com.intellij.refactoring.suggested.SuggestedChangeSignatureData
import com.intellij.refactoring.suggested.SuggestedRefactoringExecution
import org.rust.ide.refactoring.changeSignature.Parameter
import org.rust.ide.refactoring.changeSignature.ParameterProperty
import org.rust.ide.refactoring.changeSignature.RsChangeFunctionSignatureConfig
import org.rust.ide.refactoring.changeSignature.RsChangeSignatureProcessor
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.RsFunction

class RsSuggestedRefactoringExecution(support: RsSuggestedRefactoringSupport) : SuggestedRefactoringExecution(support) {
    override fun prepareChangeSignature(data: SuggestedChangeSignatureData): Any? {
        val function = data.declaration as? RsFunction ?: return null
        return RsChangeFunctionSignatureConfig.create(function)
    }

    override fun performChangeSignature(
        data: SuggestedChangeSignatureData,
        newParameterValues: List<NewParameterValue>,
        preparedData: Any?
    ) {
        // config holds the modified configuration changed by the user
        val config = preparedData as? RsChangeFunctionSignatureConfig ?: return
        val function = data.declaration as? RsFunction ?: return
        val project = function.project

        // At this point, function is restored to its old state
        // We need to create a new config which contains the original function,
        // but which has other attributes set to the modified configuration.
        val originalConfig = RsChangeFunctionSignatureConfig.create(function)

        // We only care about attributes which change triggers the suggested refactoring dialog.
        // Currently it is name and parameters.
        originalConfig.name = config.name

        val oldSignature = data.oldSignature
        val newSignature = data.newSignature

        // We need to mark "new" parameters with the new parameter index and find parameters swaps.
        var newParameterIndex = 0
        val parameters = newSignature.parameters.zip(config.parameters).map { (signatureParameter, parameter) ->
            val oldParameter = oldSignature.parameterById(signatureParameter.id)
            val isNewParameter = oldParameter == null

            val index = when (oldParameter) {
                null -> NEW_PARAMETER
                else -> oldSignature.parameterIndex(oldParameter)
            }

            val defaultValue: ParameterProperty<RsExpr> = when (isNewParameter) {
                true -> {
                    val newParameter = newParameterValues.getOrNull(newParameterIndex)
                    newParameterIndex++
                    when (newParameter) {
                        is NewParameterValue.Expression -> ParameterProperty.fromItem(newParameter.expression as? RsExpr)
                        else -> null
                    }
                }
                false -> null
            } ?: ParameterProperty.Empty()

            Parameter(parameter.factory, parameter.patText, parameter.type, index, defaultValue)
        }
        originalConfig.parameters.clear()
        originalConfig.parameters.addAll(parameters)

        RsChangeSignatureProcessor(project, originalConfig.createChangeInfo(changeSignature = false)).run()
    }
}
