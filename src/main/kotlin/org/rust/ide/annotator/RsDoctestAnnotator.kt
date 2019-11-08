/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import com.intellij.ide.annotator.AnnotatorBase
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.injected.InjectionBackgroundSuppressor
import org.rust.cargo.project.settings.rustSettings
import org.rust.ide.injected.RsDoctestLanguageInjector
import org.rust.ide.injected.areDoctestsEnabled
import org.rust.ide.injected.findDoctestInjectableRanges
import org.rust.lang.core.psi.RsDocCommentImpl
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.lang.core.psi.ext.containingCargoTarget

/**
 * Adds missing background for injections from [RsDoctestLanguageInjector].
 * Background is disabled by [InjectionBackgroundSuppressor] marker implemented for [RsDocCommentImpl].
 *
 * We have to do it this way because we want to highlight fully range inside ```backticks```
 * but a real injections is shifted by 1 character and empty lines are skipped.
 */
class RsDoctestAnnotator : AnnotatorBase() {
    override fun annotateInternal(element: PsiElement, holder: AnnotationHolder) {
        if (element !is RsDocCommentImpl) return
        if (!element.project.rustSettings.doctestInjectionEnabled) return
        // only library targets can have doctests
        if (element.ancestorStrict<RsElement>()?.containingCargoTarget?.areDoctestsEnabled != true) return

        val startOffset = element.startOffset
        findDoctestInjectableRanges(element).flatten().forEach {
            holder.createAnnotation(HighlightSeverity.INFORMATION, it.shiftRight(startOffset), null)
                .textAttributes = EditorColors.INJECTED_LANGUAGE_FRAGMENT
        }
    }
}
