/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiElement
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.TestOnly
import org.rust.openapiext.isUnitTestMode

abstract class RsAnnotatorBase : Annotator {

    final override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (!isUnitTestMode || javaClass in enabledAnnotators) {
            annotateInternal(element, holder)
        }
    }

    protected abstract fun annotateInternal(element: PsiElement, holder: AnnotationHolder)

    companion object {
        private val enabledAnnotators: MutableSet<Class<out RsAnnotatorBase>> = ContainerUtil.newConcurrentSet()

        @TestOnly
        fun enableAnnotator(annotatorClass: Class<out RsAnnotatorBase>, parentDisposable: Disposable) {
            enabledAnnotators += annotatorClass
            Disposer.register(parentDisposable, Disposable { enabledAnnotators -= annotatorClass })
        }
    }
}
