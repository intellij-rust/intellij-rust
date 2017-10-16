/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.openapiext

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.RecursionManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.stubs.StubIndexKey
import com.intellij.util.io.systemIndependentPath
import org.jdom.Element
import java.nio.file.Path
import java.nio.file.Paths


val Project.modules: Collection<Module>
    get() = ModuleManager.getInstance(this).modules.toList()


fun <T> recursionGuard(key: Any, block: Computable<T>, memoize: Boolean = true): T? =
    RecursionManager.doPreventingRecursion(key, memoize, block)


fun checkWriteAccessAllowed() {
    check(ApplicationManager.getApplication().isWriteAccessAllowed) {
        "Needs write action"
    }
}

fun checkReadAccessAllowed() {
    check(ApplicationManager.getApplication().isReadAccessAllowed) {
        "Needs read action"
    }
}


fun fullyRefreshDirectory(directory: VirtualFile) {
    VfsUtil.markDirtyAndRefresh(/* async = */ false, /* recursive = */ true, /* reloadChildren = */ true, directory)
}

fun VirtualFile.findFileByMaybeRelativePath(path: String): VirtualFile? =
    if (FileUtil.isAbsolute(path))
        fileSystem.findFileByPath(path)
    else
        findFileByRelativePath(path)

val VirtualFile.pathAsPath: Path get() = Paths.get(path)

fun VirtualFile.toPsiFile(project: Project): PsiFile? =
    PsiManager.getInstance(project).findFile(this)


@Suppress("FunctionName")
fun GeneralCommandLine(path: Path, vararg args: String) = GeneralCommandLine(path.systemIndependentPath, *args)

fun GeneralCommandLine.withWorkDirectory(path: Path?) = withWorkDirectory(path?.systemIndependentPath)


inline fun <Key, reified Psi : PsiElement> getElements(
    indexKey: StubIndexKey<Key, Psi>,
    key: Key, project: Project,
    scope: GlobalSearchScope?
): Collection<Psi> =
    StubIndex.getElements(indexKey, key, project, scope, Psi::class.java)


fun Element.toXmlString() = JDOMUtil.writeElement(this)
