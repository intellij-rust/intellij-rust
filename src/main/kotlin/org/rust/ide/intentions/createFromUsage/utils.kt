/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions.createFromUsage

import com.intellij.openapiext.isUnitTestMode
import com.intellij.psi.PsiElement
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.lang.core.psi.RsModItem
import org.rust.lang.core.psi.RsPath
import org.rust.lang.core.psi.RsStructItem
import org.rust.lang.core.psi.ext.*

@Suppress("UNCHECKED_CAST")
fun <T : PsiElement> addToModule(targetModule: RsMod, element: T): T {
    return if (targetModule is RsModItem) {
        targetModule.addBefore(element, targetModule.rbrace)
    } else {
        if (targetModule.lastChild == null) {
            targetModule.add(element)
        } else {
            targetModule.addAfter(element, targetModule.lastChild)
        }
    } as T
}

fun getVisibility(target: RsMod, source: RsMod): String = when {
    source.containingCrate != target.containingCrate -> "pub "
    source != target -> "pub(crate) "
    else -> ""
}

fun getWritablePathTarget(path: RsPath): RsQualifiedNamedElement? {
    val item = path.qualifier?.reference?.resolve() as? RsQualifiedNamedElement
    if (item?.containingCargoPackage?.origin != PackageOrigin.WORKSPACE) return null
    if (!isUnitTestMode && !item.isWritable) return null
    return item
}

sealed class CallableInsertionTarget {
    abstract val module: RsMod

    class Module(val target: RsMod) : CallableInsertionTarget() {
        override val module: RsMod = target
    }

    class Item(val item: RsStructOrEnumItemElement) : CallableInsertionTarget() {
        override val module: RsMod = item.containingMod
    }
}

/**
 * Find either a module or an ADT which qualifies the passed path.
 */
fun getTargetItemForFunctionCall(path: RsPath): CallableInsertionTarget? {
    if (path.qualifier != null) {
        return when (val item = getWritablePathTarget(path)) {
            is RsMod -> CallableInsertionTarget.Module(item)
            is RsStructOrEnumItemElement -> CallableInsertionTarget.Item(item)
            else -> null
        }
    }
    return CallableInsertionTarget.Module(path.containingMod)
}

fun insertStruct(targetModule: RsMod, struct: RsStructItem, sourceFunction: RsElement): RsStructItem {
    if (targetModule == sourceFunction.containingMod) {
        val anchor = sourceFunction.ancestors.firstOrNull { it.parent == targetModule } ?: sourceFunction
        return anchor.parent.addBefore(struct, anchor) as RsStructItem
    }
    return addToModule(targetModule, struct)
}
