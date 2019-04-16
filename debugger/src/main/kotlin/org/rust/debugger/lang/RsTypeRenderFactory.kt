/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.debugger.lang

import com.jetbrains.cidr.execution.debugger.backend.LLValue
import com.jetbrains.cidr.execution.debugger.evaluation.CidrPhysicalValue
import com.jetbrains.cidr.execution.debugger.evaluation.ValueRendererFactory
import com.jetbrains.cidr.execution.debugger.evaluation.renderers.ValueRenderer

class RsTypeRenderFactory : ValueRendererFactory {
    override fun createRenderer(context: ValueRendererFactory.FactoryContext): ValueRenderer? {
        if (context.llValue.typeClass == LLValue.TypeClass.BUILTIN) {
            val rustType = when (context.llValue.type) {
                "char" -> "i8"
                "short" -> "i16"
                "int" -> "i32"
                "long" -> "i64"
                "__int128" -> "i128"
                "unsigned char" -> "u8"
                "unsigned short" -> "u16"
                "unsigned int" -> "u32"
                "unsigned long" -> "u64"
                "unsigned __int128" -> "u128"
                else -> return null
            }
            return RsValueRenderer(rustType, context.physicalValue)
        }
        return null
    }
}

class RsValueRenderer(private val type: String, value: CidrPhysicalValue) : ValueRenderer(value) {

    override fun getDisplayType(): String {
        return type
    }

}
