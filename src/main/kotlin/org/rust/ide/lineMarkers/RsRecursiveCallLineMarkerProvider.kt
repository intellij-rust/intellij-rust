/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.lineMarkers

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import org.rust.RsBundle
import org.rust.ide.icons.RsIcons
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.lang.core.resolve.ref.RsReference
import org.rust.openapiext.document
import javax.swing.Icon

/**
 * Line marker provider that annotates recursive function and method calls with
 * an icon on the gutter.
 */
class RsRecursiveCallLineMarkerProvider : LineMarkerProviderDescriptor() {

    override fun getName(): String = RsBundle.message("gutter.rust.recursive.call.name")
    override fun getIcon(): Icon = RsIcons.RECURSIVE_CALL

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? = null

    override fun collectSlowLineMarkers(elements: List<PsiElement>, result: MutableCollection<in LineMarkerInfo<*>>) {
        val lines = HashSet<Int>()  // To prevent several markers on one line

        for (el in elements) {
            val parent = el.parent
            val isRecursive = when {
                parent is RsMethodCall && el == parent.identifier && parent.reference.isRecursive -> true
                parent is RsPath && el == parent.identifier -> {
                    val expr = parent.parent as? RsPathExpr
                    val call = expr?.parent as? RsCallExpr
                    expr != null && call != null && call.expr == expr && parent.reference?.isRecursive == true
                }
                else -> false
            }
            if (!isRecursive) continue
            val doc = el.containingFile.document ?: continue
            val lineNumber = doc.getLineNumber(el.textOffset)
            if (lineNumber !in lines) {
                lines.add(lineNumber)
                result.add(RsLineMarkerInfoUtils.create(
                    el,
                    el.textRange,
                    icon,
                    null,
                    GutterIconRenderer.Alignment.RIGHT
                ) { RsBundle.message("gutter.rust.recursive.call.name") })
            }
        }
    }

    private val RsReference.isRecursive: Boolean get() {
        val def = resolve()
        return def != null && element.ancestorStrict<RsFunction>() == def
    }
}
