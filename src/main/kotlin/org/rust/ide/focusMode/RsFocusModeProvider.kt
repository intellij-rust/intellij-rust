/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.focusMode

import com.intellij.codeInsight.daemon.impl.focusMode.FocusModeProvider
import com.intellij.openapi.util.Segment
import com.intellij.psi.PsiFile
import com.intellij.psi.SyntaxTraverser
import org.rust.lang.core.psi.RsExternCrateItem
import org.rust.lang.core.psi.RsMacro
import org.rust.lang.core.psi.RsModDeclItem
import org.rust.lang.core.psi.RsUseItem
import org.rust.lang.core.psi.ext.RsItemElement

@Suppress("UnstableApiUsage")
class RsFocusModeProvider : FocusModeProvider {

    override fun calcFocusZones(file: PsiFile): List<Segment> = SyntaxTraverser.psiTraverser(file)
        .postOrderDfsTraversal()
        .filter { it is RsItemElement || it is RsMacro }
        // These items are usually too small to be focused themselves
        .filter { it !is RsExternCrateItem && it !is RsUseItem && it !is RsModDeclItem }
        .map { it.textRange }
        .toList()
}
