/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.psi.PsiElement
import com.intellij.util.ThreeState
import com.intellij.util.text.SemVer
import org.jetbrains.annotations.Nls
import org.rust.RsBundle
import org.rust.cargo.util.parseSemVer
import org.rust.ide.annotator.RsAnnotationHolder
import org.rust.ide.fixes.AddFeatureAttributeFix
import org.rust.lang.core.FeatureAvailability.*
import org.rust.lang.core.FeatureState.ACCEPTED
import org.rust.lang.core.crate.asNotFake
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.stubs.index.RsFeatureIndex
import org.rust.lang.utils.RsDiagnostic
import org.rust.lang.utils.addToHolder
import org.rust.lang.utils.areUnstableFeaturesAvailable
import org.rust.lang.utils.evaluation.CfgEvaluator
import java.io.IOException

class CompilerFeature(
    val name: String,
    val state: FeatureState,
    @JsonSerialize(using = ToStringSerializer::class)
    val since: SemVer?
) {

    @Suppress("unused")
    @JsonCreator
    constructor(
        name: String,
        state: FeatureState,
        since: String
    ) : this(name, state, since.parseSemVer())

    fun availability(element: PsiElement): FeatureAvailability {
        val rsElement = element.ancestorOrSelf<RsElement>() ?: return UNKNOWN
        val version = rsElement.cargoProject?.rustcInfo?.version ?: return UNKNOWN

        if (since == null || version.semver.isGreaterOrEqualThan(since.major, since.minor, since.patch)) {
            when (state) {
                ACCEPTED -> return AVAILABLE
                FeatureState.REMOVED -> return REMOVED
                else -> {}
            }
        }

        when (rsElement.areUnstableFeaturesAvailable(version)) {
            ThreeState.NO -> return NOT_AVAILABLE
            ThreeState.UNSURE -> return UNKNOWN
            ThreeState.YES -> Unit
        }

        val crate = rsElement.containingCrate.asNotFake ?: return UNKNOWN
        val cfgEvaluator = CfgEvaluator.forCrate(crate)
        val attrs = RsFeatureIndex.getFeatureAttributes(element.project, name)
        val possibleFeatureAttrs = attrs.asSequence()
            .filter { it.containingCrate == crate }
            .flatMap { cfgEvaluator.expandCfgAttrs(sequenceOf(it.metaItem)) }

        for (featureAttr in possibleFeatureAttrs) {
            if (featureAttr.name != "feature") continue
            val metaItems = featureAttr.metaItemArgs?.metaItemList.orEmpty()
            if (metaItems.any { feature -> feature.name == name }) return AVAILABLE
        }
        return CAN_BE_ADDED
    }

    fun check(
        holder: RsAnnotationHolder,
        element: PsiElement,
        presentableFeatureName: @Nls String,
        experimentalFixes: List<LocalQuickFix> = emptyList(),
        removedFixes: List<LocalQuickFix> = emptyList()
    ) = check(
        holder,
        element,
        null,
        RsBundle.message("inspection.message.experimental", presentableFeatureName),
        RsBundle.message("inspection.message.has.been.removed2", presentableFeatureName),
        experimentalFixes,
        removedFixes
    )

    fun check(
        holder: AnnotationHolder,
        element: PsiElement,
        presentableFeatureName: String,
        experimentalFixes: List<LocalQuickFix> = emptyList(),
        removedFixes: List<LocalQuickFix> = emptyList()
    ) = check(
        holder,
        element,
        null,
        RsBundle.message("inspection.message.experimental", presentableFeatureName),
        RsBundle.message("inspection.message.has.been.removed2", presentableFeatureName),
        experimentalFixes,
        removedFixes
    )

    fun check(
        holder: AnnotationHolder,
        startElement: PsiElement,
        endElement: PsiElement?,
        @InspectionMessage experimentalMessage: String,
        @InspectionMessage removedMessage: String = RsBundle.message("inspection.message.has.been.removed", name),
        experimentalFixes: List<LocalQuickFix> = emptyList(),
        removedFixes: List<LocalQuickFix> = emptyList()
    ) = getDiagnostic(
        startElement,
        endElement,
        experimentalMessage,
        removedMessage,
        experimentalFixes,
        removedFixes
    )?.addToHolder(holder)

    fun check(
        holder: RsAnnotationHolder,
        startElement: PsiElement,
        endElement: PsiElement?,
        @InspectionMessage experimentalMessage: String,
        @InspectionMessage removedMessage: String = RsBundle.message("inspection.message.has.been.removed", name),
        experimentalFixes: List<LocalQuickFix> = emptyList(),
        removedFixes: List<LocalQuickFix> = emptyList()
    ) = getDiagnostic(
        startElement,
        endElement,
        experimentalMessage,
        removedMessage,
        experimentalFixes,
        removedFixes
    )?.addToHolder(holder)

    fun addFeatureFix(element: PsiElement) = AddFeatureAttributeFix(name, element)

    private fun getDiagnostic(
        startElement: PsiElement,
        endElement: PsiElement?,
        @InspectionMessage experimentalMessage: String,
        @InspectionMessage removedMessage: String,
        experimentalFixes: List<LocalQuickFix>,
        removedFixes: List<LocalQuickFix>
    ) = when (availability(startElement)) {
        NOT_AVAILABLE -> RsDiagnostic.ExperimentalFeature(startElement, endElement, experimentalMessage, experimentalFixes)
        CAN_BE_ADDED -> {
            val fix = addFeatureFix(startElement)
            RsDiagnostic.ExperimentalFeature(startElement, endElement, experimentalMessage, experimentalFixes + fix)
        }

        REMOVED -> RsDiagnostic.RemovedFeature(startElement, endElement, removedMessage, removedFixes)
        else -> null
    }

    companion object {
        private const val COMPILER_FEATURES_PATH = "compiler-info/compiler-features.json"

        private val LOG: Logger = logger<CompilerFeature>()
        private val MAPPER: ObjectMapper by lazy { jacksonObjectMapper() }

        private val knownFeatures: Map<String, CompilerFeature> by lazy {
            readFeaturesFromResources()
        }

        private fun readFeaturesFromResources(): Map<String, CompilerFeature> {
            val features: List<CompilerFeature> = try {
                val stream = CompilerFeature::class.java.classLoader
                    .getResourceAsStream(COMPILER_FEATURES_PATH)
                if (stream == null) {
                    LOG.error("Can't find `$COMPILER_FEATURES_PATH` file in resources")
                    return emptyMap()
                }
                stream.buffered().use {
                    MAPPER.readValue(it)
                }
            } catch (e: IOException) {
                LOG.error(e)
                emptyList()
            }

            return features.associateByTo(hashMapOf(), CompilerFeature::name)
        }

        fun find(featureName: String): CompilerFeature? = knownFeatures[featureName]

        private fun get(name: String): CompilerFeature = knownFeatures.getValue(name)

        val ABI_AMDGPU_KERNEL: CompilerFeature get() = get("abi_amdgpu_kernel")
        val ABI_AVR_INTERRUPT: CompilerFeature get() = get("abi_avr_interrupt")
        val ABI_C_CMSE_NONSECURE_CALL: CompilerFeature get() = get("abi_c_cmse_nonsecure_call")
        val ABI_EFIAPI: CompilerFeature get() = get("abi_efiapi")
        val ABI_MSP430_INTERRUPT: CompilerFeature get() = get("abi_msp430_interrupt")
        val ABI_PTX: CompilerFeature get() = get("abi_ptx")
        val ABI_THISCALL: CompilerFeature get() = get("abi_thiscall")
        val ABI_UNADJUSTED: CompilerFeature get() = get("abi_unadjusted")
        val ABI_VECTORCALL: CompilerFeature get() = get("abi_vectorcall")
        val ABI_X86_INTERRUPT: CompilerFeature get() = get("abi_x86_interrupt")
        val ADT_CONST_PARAMS: CompilerFeature get() = get("adt_const_params")
        val ARBITRARY_ENUM_DISCRIMINANT: CompilerFeature get() = get("arbitrary_enum_discriminant")
        val ASSOCIATED_TYPE_DEFAULTS: CompilerFeature get() = get("associated_type_defaults")
        val BOX_PATTERNS: CompilerFeature get() = get("box_patterns")
        val BOX_SYNTAX: CompilerFeature get() = get("box_syntax")
        val CONST_FN_TRAIT_BOUND: CompilerFeature get() = get("const_fn_trait_bound")
        val CONST_GENERICS_DEFAULTS: CompilerFeature get() = get("const_generics_defaults")
        val CONST_TRAIT_IMPL: CompilerFeature get() = get("const_trait_impl")
        val CRATE_IN_PATHS: CompilerFeature get() = get("crate_in_paths")
        val C_UNWIND: CompilerFeature get() = get("c_unwind")
        val C_VARIADIC: CompilerFeature get() = get("c_variadic")
        val DECL_MACRO: CompilerFeature get() = get("decl_macro")
        val EXCLUSIVE_RANGE_PATTERN: CompilerFeature get() = get("exclusive_range_pattern")
        val EXTERN_CRATE_SELF: CompilerFeature get() = get("extern_crate_self")
        val EXTERN_TYPES: CompilerFeature get() = get("extern_types")
        val FORMAT_ARGS_CAPTURE: CompilerFeature get() = get("format_args_capture")
        val GENERATORS: CompilerFeature get() = get("generators")
        val GENERIC_ASSOCIATED_TYPES: CompilerFeature get() = get("generic_associated_types")
        val IF_LET_GUARD: CompilerFeature get() = get("if_let_guard")
        val IF_WHILE_OR_PATTERNS: CompilerFeature get() = get("if_while_or_patterns")
        val INHERENT_ASSOCIATED_TYPES: CompilerFeature get() = get("inherent_associated_types")
        val INLINE_CONST: CompilerFeature get() = get("inline_const")
        val INLINE_CONST_PAT: CompilerFeature get() = get("inline_const_pat")
        val INTRINSICS: CompilerFeature get() = get("intrinsics")
        val IRREFUTABLE_LET_PATTERNS: CompilerFeature get() = get("irrefutable_let_patterns")
        val LABEL_BREAK_VALUE: CompilerFeature get() = get("label_break_value")
        val LET_CHAINS: CompilerFeature get() = get("let_chains")
        val LET_ELSE: CompilerFeature get() = get("let_else")
        val MIN_CONST_GENERICS: CompilerFeature get() = get("min_const_generics")
        val NON_MODRS_MODS: CompilerFeature get() = get("non_modrs_mods")
        val OR_PATTERNS: CompilerFeature get() = get("or_patterns")
        val PARAM_ATTRS: CompilerFeature get() = get("param_attrs")
        val PLATFORM_INTRINSICS: CompilerFeature get() = get("platform_intrinsics")
        val RAW_REF_OP: CompilerFeature get() = get("raw_ref_op")
        val RETURN_POSITION_IMPL_TRAIT_IN_TRAIT: CompilerFeature get() = get("return_position_impl_trait_in_trait")
        val SLICE_PATTERNS: CompilerFeature get() = get("slice_patterns")
        val START: CompilerFeature get() = get("start")
        val UNBOXED_CLOSURES: CompilerFeature get() = get("unboxed_closures")
        val WASM_ABI: CompilerFeature get() = get("wasm_abi")
        val HALF_OPEN_RANGE_PATTERNS: CompilerFeature get() = get("half_open_range_patterns")
        val CONST_CLOSURES: CompilerFeature get() = get("const_closures")
        val C_STR_LITERAL: CompilerFeature get() = get("c_str_literals")
    }
}

// All variants can be used by deserialization
@Suppress("unused")
enum class FeatureState {
    /**
     * Represents active features that are currently being implemented or
     * currently being considered for addition/removal.
     * Such features can be used only with nightly compiler with the corresponding feature attribute
     */
    ACTIVE,

    /**
     * Represents incomplete features that may not be safe to use and/or cause compiler crashes.
     * Such features can be used only with nightly compiler with the corresponding feature attribute
     */
    INCOMPLETE,

    /**
     * Those language feature has since been Accepted (it was once Active)
     * so such language features can be used with stable/beta compiler since some version
     * without any additional attributes
     */
    ACCEPTED,

    /**
     * Represents unstable features which have since been removed (it was once Active)
     */
    REMOVED,

    /**
     * Represents stable features which have since been removed (it was once Accepted)
     */
    STABILIZED;

    @JsonValue
    override fun toString(): String {
        return name.lowercase()
    }
}

enum class FeatureAvailability {
    AVAILABLE,
    CAN_BE_ADDED,
    NOT_AVAILABLE,
    REMOVED,
    UNKNOWN
}
