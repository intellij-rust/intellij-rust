/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.hierarchy

import com.intellij.ide.hierarchy.HierarchyNodeDescriptor
import com.intellij.ide.hierarchy.HierarchyTreeStructure
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*

class RsCalleeTreeStructure(
    element: RsQualifiedNamedElement
) : HierarchyTreeStructure(element.project, RsCallHierarchyNodeDescriptor(null, element, true)) {
    override fun buildChildren(descriptor: HierarchyNodeDescriptor): Array<Any> {
        return when (val element = descriptor.psiElement) {
            is RsFunction -> {
                val ctx = createCalleeContext(element)
                ctx.descriptors.toTypedArray()
            }
            else -> emptyArray()
        }
    }
}

private sealed class FunctionCall(val element: RsQualifiedNamedElement) {
    class Function(val function: RsFunction): FunctionCall(function)
    class Method(val function: RsFunction): FunctionCall(function)
    class TupleStruct(val struct: RsStructOrEnumItemElement): FunctionCall(struct)
    class TupleEnumVariant(val variant: RsEnumVariant) : FunctionCall(variant)
    class Macro(val target: RsMacroDefinitionBase) : FunctionCall(target)
}

private class CalleeContext {
    private val elementToDescriptorMap: MutableMap<RsElement, RsCallHierarchyNodeDescriptor> = mutableMapOf()

    val descriptors: List<RsCallHierarchyNodeDescriptor>
        get() = elementToDescriptorMap.values.toList()

    fun add(call: FunctionCall) {
        val element = call.element
        val entry = elementToDescriptorMap[element]
        if (entry != null) {
            entry.incrementUsageCount()
        } else {
            elementToDescriptorMap[element] = RsCallHierarchyNodeDescriptor(null, element, false)
        }
    }
}

private fun createCalleeContext(function: RsFunction): CalleeContext {
    val ctx = CalleeContext()
    val block = function.block ?: return ctx
    val elements = block.children

    val visitor = object: RsRecursiveVisitor() {
        override fun visitCallExpr(o: RsCallExpr) {
            when (val target = (o.expr as? RsPathExpr)?.path?.reference?.resolve()) {
                is RsFunction -> ctx.add(FunctionCall.Function(target))
                is RsStructItem -> ctx.add(FunctionCall.TupleStruct(target))
                is RsEnumVariant -> ctx.add(FunctionCall.TupleEnumVariant(target))
            }
            super.visitCallExpr(o)
        }

        override fun visitMethodCall(o: RsMethodCall) {
            val target = o.reference.resolve() as? RsFunction
            if (target != null) {
                ctx.add(FunctionCall.Method(target))
            }
            super.visitMethodCall(o)
        }

        override fun visitMacroCall(o: RsMacroCall) {
            val target = o.path.reference?.resolve() as? RsMacroDefinitionBase
            if (target != null) {
                ctx.add(FunctionCall.Macro(target))
            }
            super.visitMacroCall(o)
        }

        override fun visitFunction(o: RsFunction) {
            // Ignore functions
        }

        override fun visitTraitOrImpl(o: RsTraitOrImpl) {
            // Ignore impls and traits
        }

        override fun visitBlockExpr(o: RsBlockExpr) {
            // Ignore async blocks
            if (!o.isAsync) {
                super.visitBlockExpr(o)
            }
        }

        override fun visitLambdaExpr(o: RsLambdaExpr) {
            // Ignore lambdas
        }
    }
    for (element in elements) {
        element.accept(visitor)
    }

    return ctx
}
