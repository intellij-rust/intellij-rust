/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.refactoring

import com.intellij.psi.PsiElement
import com.intellij.psi.search.SearchScope
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import org.rust.lang.core.psi.RsLabel
import org.rust.lang.core.psi.RsLabelDecl
import org.rust.lang.core.psi.RsLifetime
import org.rust.lang.core.psi.RsLifetimeParameter

class RsRenameProcessor : RenamePsiElementProcessor() {

    override fun canProcessElement(element: PsiElement): Boolean = true

    override fun prepareRenaming(element: PsiElement, newName: String, allRenames: MutableMap<PsiElement, String>, scope: SearchScope) {
        if (element is RsLifetime || element is RsLifetimeParameter || element is RsLabel || element is RsLabelDecl) {
            allRenames.put(element, newName.ensureQuote())
        } else {
            allRenames.put(element, newName.trimStart('\''))
        }
    }

    private fun String.ensureQuote(): String = if (startsWith('\'')) { this } else { "'$this" }

}
