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
