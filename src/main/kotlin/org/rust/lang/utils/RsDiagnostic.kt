/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.utils

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.xml.util.XmlStringUtil.escapeString
import org.rust.ide.annotator.fixes.*
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.ImplLookup
import org.rust.lang.core.resolve.StdKnownItems
import org.rust.lang.core.resolve.withSubst
import org.rust.lang.core.types.TraitRef
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyInfer
import org.rust.lang.core.types.ty.TyNumeric
import org.rust.lang.refactoring.implementMembers.ImplementMembersFix
import org.rust.lang.utils.RsErrorCode.*
import org.rust.lang.utils.Severity.*
import org.rust.stdext.buildList

sealed class RsDiagnostic(
    val element: PsiElement,
    val endElement: PsiElement? = null,
    val experimental: Boolean = false
) {
    abstract fun prepare(): PreparedAnnotation

    class TypeError(
        element: PsiElement,
        private val expectedTy: Ty,
        private val actualTy: Ty
    ) : RsDiagnostic(element, experimental = true) {
        override fun prepare(): PreparedAnnotation {
            return PreparedAnnotation(
                ERROR,
                E0308,
                "mismatched types",
                expectedFound(expectedTy, actualTy),
                fixes = buildList {
                    if (expectedTy is TyNumeric && isActualTyNumeric()) {
                        add(AddAsTyFix(element, expectedTy))
                    } else if (isFromActualImplForExpected(element)) {
                        add(ConvertToTyUsingFromTraitFix(element, expectedTy))
                    }
                }
            )
        }

        private fun isActualTyNumeric() = actualTy is TyNumeric || actualTy is TyInfer.IntVar || actualTy is TyInfer.FloatVar

        private fun isFromActualImplForExpected(element: PsiElement): Boolean {
            val items = StdKnownItems.relativeTo(element as? RsElement ?: return false)
            val lookup = ImplLookup(element.project, items)
            val fromTrait = items.findFromTrait() ?: return false
            return lookup.select(TraitRef(expectedTy, fromTrait.withSubst(actualTy))).ok() != null
        }

        private fun expectedFound(expectedTy: Ty, actualTy: Ty): String {
            val expectedTyS = escapeString(expectedTy.toString())
            val actualTyS = escapeString(actualTy.toString())
            return "expected `$expectedTyS`, found `$actualTyS`"
        }
    }

    class AccessError(
        element: PsiElement,
        private val errorCode: RsErrorCode,
        private val itemType: String
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            errorCode,
            "$itemType `${escapeString(element.text)}` is private"
        )
    }

    class StructFieldAccessError(
        element: PsiElement,
        private val fieldName: String,
        private val structName: String
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0616,
            "Field `${escapeString(fieldName)}` of struct `${escapeString(structName)}` is private"
        )
    }

    class UnsafeError(
        element: PsiElement,
        private val message: String
    ) : RsDiagnostic(element) {
        override fun prepare(): PreparedAnnotation {
            val block = element.ancestorStrict<RsBlock>()?.parent
            val fixes = mutableListOf<LocalQuickFix>(SurroundWithUnsafeFix(element as RsExpr))
            if (block != null) fixes.add(AddUnsafeFix(block))
            return PreparedAnnotation(
                ERROR,
                E0133,
                message,
                fixes = fixes
            )
        }
    }

    class TypePlaceholderForbiddenError(
        element: PsiElement
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0121,
            "The type placeholder `_` is not allowed within types on item signatures"
        )
    }

    class SelfInStaticMethodError(
        element: PsiElement,
        private val function: RsFunction
    ) : RsDiagnostic(element) {
        override fun prepare(): PreparedAnnotation {
            val fixes = mutableListOf<LocalQuickFix>()
            if (function.owner.isImplOrTrait) fixes.add(AddSelfFix(function))
            return PreparedAnnotation(
                ERROR,
                E0424,
                "The self keyword was used in a static method",
                fixes = fixes
            )
        }
    }

    class UnnecessaryVisibilityQualifierError(
        element: PsiElement
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0449,
            "Unnecessary visibility qualifier"
        )
    }

    class UnsafeNegativeImplementationError(
        element: PsiElement
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0198,
            "Negative implementations are not unsafe"
        )
    }

    class UnsafeTraitImplError(
        element: PsiElement,
        private val traitName: String
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0199,
            errorText()
        )

        private fun errorText(): String {
            val traitNameS = escapeString(traitName)
            return "Implementing the trait `$traitNameS` is not unsafe"
        }
    }

    class TraitMissingUnsafeImplError(
        element: PsiElement,
        private val traitName: String
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0200,
            errorText()
        )

        private fun errorText(): String {
            val traitNameS = escapeString(traitName)
            return "The trait `$traitNameS` requires an `unsafe impl` declaration"
        }
    }

    class TraitMissingUnsafeImplAttributeError(
        element: PsiElement,
        private val attrRequiringUnsafeImpl: String
    ) : RsDiagnostic(element) {
        override fun prepare(): PreparedAnnotation = PreparedAnnotation(
            ERROR,
            E0569,
            errorText()
        )

        private fun errorText(): String {
            return "Requires an `unsafe impl` declaration due to `#[$attrRequiringUnsafeImpl]` attribute"
        }
    }

    class UnknownMethodInTraitError(
        element: PsiElement,
        private val member: RsAbstractable,
        private val traitName: String
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0407,
            errorText()
        )

        private fun errorText(): String {
            val memberNameS = escapeString(member.name)
            val traitNameS = escapeString(traitName)
            return "Method `$memberNameS` is not a member of trait `$traitNameS`"
        }
    }

    class DeclMissingFromTraitError(
        element: PsiElement,
        private val fn: RsFunction,
        private val selfParameter: RsSelfParameter
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0185,
            errorText()
        )

        private fun errorText(): String {
            return "Method `${fn.name}` has a `${selfParameter.canonicalDecl}` declaration in the impl, but not in the trait"
        }
    }

    class DeclMissingFromImplError(
        element: PsiElement,
        private val fn: RsFunction,
        private val selfParameter: RsSelfParameter?
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0186,
            errorText()
        )

        private fun errorText(): String {
            return "Method `${fn.name}` has a `${selfParameter?.canonicalDecl}` declaration in the trait, but not in the impl"
        }
    }

    class TraitParamCountMismatchError(
        element: PsiElement,
        private val fn: RsFunction,
        private val traitName: String,
        private val paramsCount: Int,
        private val superParamsCount: Int
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0050,
            errorText()
        )

        private fun errorText(): String {
            val fnName = escapeString(fn.name)
            val traitNameS = escapeString(traitName)
            return "Method `$fnName` has $paramsCount ${pluralise(paramsCount, "parameter", "parameters")} but the declaration in trait `$traitNameS` has $superParamsCount"
        }
    }

    class TooFewParamsError(
        element: PsiElement,
        private val expectedCount: Int,
        private val realCount: Int
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0060,
            errorText()
        )

        private fun errorText(): String {
            return "This function takes at least $expectedCount ${pluralise(expectedCount, "parameter", "parameters")}" +
                " but $realCount ${pluralise(realCount, "parameter", "parameters")}" +
                " ${pluralise(realCount, "was", "were")} supplied"
        }
    }

    class TooManyParamsError(
        element: PsiElement,
        private val expectedCount: Int,
        private val realCount: Int
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0061,
            errorText()
        )

        private fun errorText(): String {
            return "This function takes $expectedCount ${pluralise(expectedCount, "parameter", "parameters")}" +
                " but $realCount ${pluralise(realCount, "parameter", "parameters")}" +
                " ${pluralise(realCount, "was", "were")} supplied"
        }
    }

    class ReturnMustHaveValueError(
        element: PsiElement
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0069,
            "`return;` in a function whose return type is not `()`"
        )
    }

    class DuplicateFieldError(
        element: PsiElement,
        private val fieldName: String
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0124,
            errorText()
        )

        private fun errorText(): String {
            return "Field `$fieldName` is already declared"
        }
    }

    class DuplicateEnumVariantError(
        element: PsiElement,
        private val fieldName: String
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0428,
            errorText()
        )

        private fun errorText(): String {
            val name = escapeString(fieldName)
            return "Enum variant `$name` is already declared"
        }
    }

    class DuplicateLifetimeError(
        element: PsiElement,
        private val fieldName: String
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0263,
            errorText()
        )

        private fun errorText(): String {
            val name = escapeString(fieldName)
            return "Lifetime name `$name` declared twice in the same scope"
        }
    }

    class DuplicateBindingError(
        element: PsiElement,
        private val fieldName: String
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0415,
            errorText()
        )

        private fun errorText(): String {
            val name = escapeString(fieldName)
            return "Identifier `$name` is bound more than once in this parameter list"
        }
    }

    class DuplicateTypeParameterError(
        element: PsiElement,
        private val fieldName: String
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0403,
            errorText()
        )

        private fun errorText(): String {
            val name = escapeString(fieldName)
            return "The name `$name` is already used for a type parameter in this type parameter list"
        }
    }

    class DuplicateDefinitionError(
        element: PsiElement,
        private val fieldName: String
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0201,
            errorText()
        )

        private fun errorText(): String {
            val name = escapeString(fieldName)
            return "Duplicate definitions with name `$name`"
        }
    }

    class DuplicateItemError(
        element: PsiElement,
        private val itemType: String,
        private val fieldName: String,
        private val scopeType: String
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0428,
            errorText()
        )

        private fun errorText(): String {
            val itemTypeS = escapeString(itemType)
            val fieldNameS = escapeString(fieldName)
            val scopeTypeS = escapeString(scopeType)
            return "A $itemTypeS named `$fieldNameS` has already been defined in this $scopeTypeS"
        }
    }

    class AssociatedTypeInInherentImplError(
        element: PsiElement
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0202,
            "Associated types are not allowed in inherent impls"
        )
    }

    class ConstTraitFnError(
        element: PsiElement
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0379,
            "Trait functions cannot be declared const"
        )
    }

    class UndeclaredLabelError(
        element: RsReferenceElement
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0426,
            errorText()
        )

        private fun errorText(): String {
            val labelText = escapeString(element.text)
            return "Use of undeclared label `$labelText`"
        }
    }

    class UndeclaredLifetimeError(
        element: RsReferenceElement
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0261,
            errorText()
        )

        private fun errorText(): String {
            val lifetimeText = escapeString(element.text)
            return "Use of undeclared lifetime name `$lifetimeText`"
        }
    }

    class TraitItemsMissingImplError(
        startElement: PsiElement,
        endElement: PsiElement,
        private val missing: String,
        private val impl: RsImplItem
    ) : RsDiagnostic(startElement, endElement) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0046,
            errorText(),
            fixes = listOf(ImplementMembersFix(impl))
        )

        private fun errorText(): String {
            val missingS = escapeString(missing)
            return "Not all trait items implemented, missing: $missingS"
        }
    }

    class CrateNotFoundError(
        startElement: PsiElement,
        private val crateName: String
    ) : RsDiagnostic(startElement) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0463,
            errorText()
        )

        private fun errorText(): String {
            val elTextS = escapeString(crateName)
            return "Can't find crate for `$elTextS`"
        }
    }

    class SizedTraitIsNotImplemented(
        element: RsTypeReference,
        private val ty: Ty
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0277,
            header = escapeString("the trait bound `$ty: std::marker::Sized` is not satisfied"),
            description = escapeString("`$ty` does not have a constant size known at compile-time"),
            fixes = listOf(ConvertToReferenceFix(element), ConvertToBoxFix(element))
        )
    }
}

enum class RsErrorCode {
    E0046, E0050, E0060, E0061, E0069,
    E0121, E0124, E0133, E0185, E0186, E0198, E0199,
    E0200, E0201, E0202, E0261, E0263, E0277,
    E0308, E0379,
    E0403, E0407, E0415, E0424, E0426, E0428, E0449, E0463,
    E0569,
    E0603, E0616, E0624;

    val code: String
        get() = toString()
    val infoUrl: String
        get() = "https://doc.rust-lang.org/error-index.html#$code"
}

enum class Severity {
    INFO, WARN, ERROR
}

class PreparedAnnotation(
    val severity: Severity,
    val errorCode: RsErrorCode,
    val header: String,
    val description: String = "",
    val fixes: List<LocalQuickFix> = emptyList()
)

fun RsDiagnostic.addToHolder(holder: AnnotationHolder) {
    val prepared = prepare()

    val textRange = if (endElement != null) {
        TextRange.create(
            element.textRange.startOffset,
            endElement.textRange.endOffset
        )
    } else {
        element.textRange
    }

    val ann = holder.createAnnotation(
        prepared.severity.toHighlightSeverity(),
        textRange,
        simpleHeader(prepared.errorCode, prepared.header),
        "<html>${htmlHeader(prepared.errorCode, prepared.header)}<br>${prepared.description}</html>"
    )

    for (fix in prepared.fixes) {
        if (fix is IntentionAction) {
            ann.registerFix(fix)
        } else {
            val descriptor = InspectionManager.getInstance(element.project)
                .createProblemDescriptor(
                    element,
                    endElement ?: element,
                    ann.message,
                    prepared.severity.toProblemHighlightType(),
                    true,
                    fix
                )

            ann.registerFix(fix, null, null, descriptor)
        }
    }
}

fun RsDiagnostic.addToHolder(holder: ProblemsHolder) {
    val prepared = prepare()
    val descriptor = holder.manager.createProblemDescriptor(
        element,
        endElement ?: element,
        "<html>${htmlHeader(prepared.errorCode, prepared.header)}<br>${prepared.description}</html>",
        prepared.severity.toProblemHighlightType(),
        holder.isOnTheFly,
        *prepared.fixes.toTypedArray()
    )
    holder.registerProblem(descriptor)
}

private fun Severity.toProblemHighlightType(): ProblemHighlightType = when (this) {
    INFO -> ProblemHighlightType.INFORMATION
    WARN -> ProblemHighlightType.WEAK_WARNING
    ERROR -> ProblemHighlightType.GENERIC_ERROR_OR_WARNING
}

private fun Severity.toHighlightSeverity(): HighlightSeverity = when (this) {
    INFO -> HighlightSeverity.INFORMATION
    WARN -> HighlightSeverity.WARNING
    ERROR -> HighlightSeverity.ERROR
}

private fun simpleHeader(error: RsErrorCode, description: String): String =
    "$description [${error.code}]"

private fun htmlHeader(error: RsErrorCode, description: String): String =
    "$description [<a href='${error.infoUrl}'>${error.code}</a>]"

private fun pluralise(count: Int, singular: String, plural: String): String =
    if (count == 1) singular else plural

private val RsSelfParameter.canonicalDecl: String
    get() = buildString {
        if (isRef) append('&')
        if (mutability.isMut) append("mut ")
        append("self")
    }
