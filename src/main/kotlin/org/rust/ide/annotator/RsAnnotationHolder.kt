/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import com.intellij.lang.annotation.Annotation
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.AnnotationSession
import com.intellij.psi.PsiElement
import org.rust.ide.utils.isEnabledByCfg

class RsAnnotationHolder(val holder: AnnotationHolder) {
    fun createErrorAnnotation(element: PsiElement, message: String?): Annotation? =
        if (element.isEnabledByCfg) holder.createErrorAnnotation(element, message) else null

    fun createWeakWarningAnnotation(element: PsiElement, message: String?): Annotation? =
        if (element.isEnabledByCfg) holder.createWeakWarningAnnotation(element, message) else null

    val currentAnnotationSession: AnnotationSession = holder.currentAnnotationSession
}
