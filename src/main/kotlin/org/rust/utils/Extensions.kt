/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.utils

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.CompositeElement
import com.intellij.util.io.systemIndependentPath
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Transforms seconds into milliseconds
 */
val Int.seconds: Int
    get() = this * 1000

fun GeneralCommandLine(path: Path, vararg args: String) = GeneralCommandLine(path.systemIndependentPath, *args)
fun GeneralCommandLine.withWorkDirectory(path: Path?) = withWorkDirectory(path?.systemIndependentPath)
val VirtualFile.pathAsPath: Path get() = Paths.get(path)

/**
 * Iterates all children of the `PsiElement` and invokes `action` for each one.
 */
inline fun PsiElement.forEachChild(action: (PsiElement) -> Unit) {
    var psiChild: PsiElement? = firstChild

    while (psiChild != null) {
        if (psiChild.node is CompositeElement) {
            action(psiChild)
        }
        psiChild = psiChild.nextSibling
    }
}
