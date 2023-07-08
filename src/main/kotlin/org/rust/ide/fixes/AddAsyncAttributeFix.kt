/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.rust.RsBundle
import org.rust.ide.utils.import.RsImportHelper
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.containingCrate
import org.rust.lang.core.resolve.knownItems

class AddAsyncRecursionAttributeFix(function: RsFunction): RsQuickFixBase<RsFunction>(function) {
    override fun getText(): String = RsBundle.message("intention.name.add.async.recursion.attribute")
    override fun getFamilyName(): String = text

    override fun invoke(project: Project, editor: Editor?, element: RsFunction) {
        val procMacro = element.knownItems
            .findItem<RsFunction>("async_recursion::async_recursion", isStd = false) ?: return
        RsImportHelper.importElement(element, procMacro)
        val attr = RsPsiFactory(project).createOuterAttr("async_recursion")
        element.addAfter(attr, null)
    }

    companion object {
        fun createIfCompatible(function: RsFunction): AddAsyncRecursionAttributeFix? {
            if (!hasAsyncRecursionDependency(function)) return null
            return AddAsyncRecursionAttributeFix(function)
        }

        private fun hasAsyncRecursionDependency(context: RsElement): Boolean {
            val crate = context.containingCrate
            return crate.dependencies.any { it.normName == "async_recursion" }
        }
    }
}
