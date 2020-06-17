/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import com.intellij.lang.annotation.AnnotationBuilder
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.AnnotationSession
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import org.rust.ide.utils.isEnabledByCfg

class RsAnnotationHolder(val holder: AnnotationHolder) {

    fun createErrorAnnotation(element: PsiElement, message: String?): Unit? =
        getErrorAnnotationBuilder(element, message)?.create()

    fun getErrorAnnotationBuilder(element: PsiElement, message: String?): AnnotationBuilder? =
        if (element.isEnabledByCfg) {
            if (message == null) {
                holder.newSilentAnnotation(HighlightSeverity.ERROR).range(element)
            } else {
                holder.newAnnotation(HighlightSeverity.ERROR, message).range(element)
            }
        } else null

    fun createWeakWarningAnnotation(element: PsiElement, message: String?): AnnotationBuilder? =
        if (element.isEnabledByCfg) {
            if (message == null)
                holder.newSilentAnnotation(HighlightSeverity.WEAK_WARNING).range(element)
            else
                holder.newAnnotation(HighlightSeverity.WEAK_WARNING, message).range(element)
        } else null

    val currentAnnotationSession: AnnotationSession = holder.currentAnnotationSession
}
