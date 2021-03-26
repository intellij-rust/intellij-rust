/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.lineMarkers

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import javax.swing.Icon

object RsLineMarkerInfoUtils {
    fun create(
        element: PsiElement,
        range: TextRange,
        icon: Icon,
        navHandler: GutterIconNavigationHandler<PsiElement>?,
        alignment: GutterIconRenderer.Alignment,
        messageProvider: () -> String
    ): LineMarkerInfo<PsiElement> {
        return LineMarkerInfo(element, range, icon, { messageProvider() }, navHandler, alignment, messageProvider)
    }
}
