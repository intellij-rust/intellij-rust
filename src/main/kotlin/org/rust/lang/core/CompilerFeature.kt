/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.psi.PsiElement
import com.intellij.util.text.SemVer
import org.rust.cargo.toolchain.RustChannel
import org.rust.ide.annotator.RsAnnotationHolder
import org.rust.ide.annotator.fixes.AddFeatureAttributeFix
import org.rust.lang.core.FeatureAvailability.*
import org.rust.lang.core.FeatureState.ACCEPTED
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.ancestorOrSelf
import org.rust.lang.core.psi.ext.cargoProject
import org.rust.lang.core.stubs.index.RsFeatureIndex
import org.rust.lang.utils.RsDiagnostic
import org.rust.lang.utils.addToHolder

data class CompilerFeature(val name: String, val state: FeatureState, val since: SemVer) {
    constructor(name: String, state: FeatureState, since: String) : this(name, state, SemVer.parseFromText(since)!!)

    fun availability(element: PsiElement): FeatureAvailability {
        val rsElement = element.ancestorOrSelf<RsElement>() ?: return UNKNOWN
        val version = rsElement.cargoProject?.rustcInfo?.version ?: return UNKNOWN

        if (state == ACCEPTED && version.semver >= since) return AVAILABLE
        if (version.channel != RustChannel.NIGHTLY) return NOT_AVAILABLE

        val crateRoot = rsElement.crateRoot ?: return UNKNOWN
        val attrs = RsFeatureIndex.getFeatureAttributes(element.project, name)
        return if (attrs.any { it.crateRoot == crateRoot }) AVAILABLE else CAN_BE_ADDED
    }

    fun check(
        holder: RsAnnotationHolder,
        element: PsiElement,
        presentableFeatureName: String,
        vararg fixes: LocalQuickFix
    ) = check(holder, element, null, "$presentableFeatureName is experimental", *fixes)

    fun check(
        holder: AnnotationHolder,
        element: PsiElement,
        presentableFeatureName: String,
        vararg fixes: LocalQuickFix
    ) = check(holder, element, null, "$presentableFeatureName is experimental", *fixes)

    fun check(
        holder: AnnotationHolder,
        startElement: PsiElement,
        endElement: PsiElement?,
        message: String,
        vararg fixes: LocalQuickFix
    ) {
        getDiagnostic(startElement, endElement, message, *fixes)?.addToHolder(holder)
    }

    fun check(
        holder: RsAnnotationHolder,
        startElement: PsiElement,
        endElement: PsiElement?,
        message: String,
        vararg fixes: LocalQuickFix
    ) {
        getDiagnostic(startElement, endElement, message, *fixes)?.addToHolder(holder)
    }

    fun addFeatureFix(element: PsiElement) =
        AddFeatureAttributeFix(name, element)

    private fun getDiagnostic(
        startElement: PsiElement,
        endElement: PsiElement?,
        message: String,
        vararg fixes: LocalQuickFix
    ) = when (availability(startElement)) {
        NOT_AVAILABLE -> RsDiagnostic.ExperimentalFeature(startElement, endElement, message, fixes.toList())
        CAN_BE_ADDED -> {
            val fix = addFeatureFix(startElement)
            RsDiagnostic.ExperimentalFeature(startElement, endElement, message, listOf(*fixes, fix))
        }
        else -> null
    }
}

enum class FeatureState {
    /**
     * Represents active features that are currently being implemented or
     * currently being considered for addition/removal.
     * Such features can be used only with nightly compiler with the corresponding feature attribute
     */
    ACTIVE,
    /**
     * Those language feature has since been Accepted (it was once Active)
     * so such language features can be used with stable/beta compiler since some version
     * without any additional attributes
     */
    ACCEPTED
}

enum class FeatureAvailability {
    AVAILABLE,
    CAN_BE_ADDED,
    NOT_AVAILABLE,
    UNKNOWN
}
