/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.debugger.lang

import com.jetbrains.cidr.execution.debugger.CidrFrameTypeDecorator
import com.jetbrains.cidr.execution.debugger.CidrStackFrame
import com.jetbrains.cidr.execution.debugger.backend.DebuggerDriverConfiguration
import com.jetbrains.cidr.execution.debugger.backend.lldb.LLDBDriverConfiguration
import com.jetbrains.cidr.execution.debugger.evaluation.CidrPhysicalValue
import com.jetbrains.cidr.execution.debugger.evaluation.renderers.ValueRendererUtils
import com.jetbrains.cidr.toolchains.OSType
import org.jetbrains.annotations.VisibleForTesting
import org.rust.debugger.settings.RsDebuggerSettings


class RsFrameTypeDecorator(private val frame: CidrStackFrame) : CidrFrameTypeDecorator {
    override fun getValueDisplayType(value: CidrPhysicalValue, renderForUiLabel: Boolean): String {
        val shouldDecorate = RsDebuggerSettings.getInstance().decorateMsvcTypeNames
        val driverConfiguration = frame.process.runParameters.debuggerDriverConfiguration

        if (shouldDecorate && driverConfiguration.isMsvcLldb()) {
            return decorate(value.type)
        }
        return super.getValueDisplayType(value, renderForUiLabel)
    }

    companion object {
        @VisibleForTesting
        fun decorate(typeName: String): String {
            val typeNameParsed = RsTypeNameParserFacade.parse(typeName)
                ?: return ValueRendererUtils.shortenTemplateType(typeName)
            val visitor = RsMSVCTypeNameDecoratorVisitor()
            typeNameParsed.accept(visitor)
            return visitor.getDecoratedTypeName()
        }

        private fun DebuggerDriverConfiguration.isMsvcLldb(): Boolean =
            hostMachine.osType == OSType.WIN && this is LLDBDriverConfiguration
    }
}

