/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.utils

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil.pluralize
import com.intellij.psi.PsiElement
import com.intellij.xml.util.XmlStringUtil.escapeString
import org.rust.ide.annotator.RsAnnotationHolder
import org.rust.ide.annotator.RsErrorAnnotator
import org.rust.ide.annotator.fixes.*
import org.rust.ide.inspections.RsExperimentalChecksInspection
import org.rust.ide.inspections.RsProblemsHolder
import org.rust.ide.inspections.RsTypeCheckInspection
import org.rust.ide.inspections.checkMatch.Pattern
import org.rust.ide.inspections.fixes.AddRemainingArmsFix
import org.rust.ide.inspections.fixes.AddWildcardArmFix
import org.rust.ide.refactoring.implementMembers.ImplementMembersFix
import org.rust.ide.utils.isEnabledByCfg
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.ImplLookup
import org.rust.lang.core.resolve.KnownItems
import org.rust.lang.core.types.*
import org.rust.lang.core.types.infer.TypeFoldable
import org.rust.lang.core.types.infer.TypeFolder
import org.rust.lang.core.types.infer.TypeVisitor
import org.rust.lang.core.types.infer.hasTyInfer
import org.rust.lang.core.types.ty.*
import org.rust.lang.utils.RsErrorCode.*
import org.rust.lang.utils.Severity.*
import org.rust.stdext.buildList
import org.rust.stdext.buildMap

private val REF_STR_TY = TyReference(TyStr, Mutability.IMMUTABLE)
private val MUT_REF_STR_TY = TyReference(TyStr, Mutability.MUTABLE)

sealed class RsDiagnostic(
    val element: PsiElement,
    val endElement: PsiElement? = null,
    val inspectionClass: Class<*> = RsErrorAnnotator::class.java
) {
    abstract fun prepare(): PreparedAnnotation

    class TypeError(
        element: PsiElement,
        private val expectedTy: Ty,
        private val actualTy: Ty
    ) : RsDiagnostic(element, inspectionClass = RsTypeCheckInspection::class.java), TypeFoldable<TypeError> {
        private val description: String? = if (expectedTy.hasTyInfer || actualTy.hasTyInfer) {
            // if types contain infer types, they will be replaced with more specific (e.g. `{integer}` with `i32`)
            // so we capture string representation of types right here
            expectedFound(expectedTy, actualTy)
        } else {
            null
        }

        override fun prepare(): PreparedAnnotation {
            return PreparedAnnotation(
                ERROR,
                E0308,
                "mismatched types",
                description ?: expectedFound(expectedTy, actualTy),
                fixes = buildList {
                    if (expectedTy is TyNumeric && isActualTyNumeric()) {
                        add(AddAsTyFix(element, expectedTy))
                    } else  if (element is RsElement) {
                        val (lookup, items) = element.implLookupAndKnownItems
                        if (isFromActualImplForExpected(items, lookup)) {
                            add(ConvertToTyUsingFromTraitFix(element, expectedTy))
                        } else { // only check TryFrom if From is not available
                            val resultErrTy = errTyOfTryFromActualImplForTy(expectedTy, items, lookup)
                            if (resultErrTy != null) {
                                add(ConvertToTyUsingTryFromTraitAndUnpackFix(element, expectedTy, resultErrTy))
                            }
                        }
                        // currently it's possible to have `impl FromStr` independently form `impl<'a> From<&'a str>` or
                        // `impl<'a> TryFrom<&'a str>`, so check for FromStr even if From or TryFrom is available
                        val resultFromStrErrTy = ifActualIsStrGetErrTyOfFromStrImplForTy(expectedTy, items, lookup)
                        if (resultFromStrErrTy != null) {
                            add(ConvertToTyUsingFromStrAndUnpackFix(element, expectedTy, resultFromStrErrTy))
                        }
                        if (isToOwnedImplWithExpectedForActual(items, lookup)) {
                            add(ConvertToOwnedTyFix(element, expectedTy))
                        }
                        val stringTy = items.String.asTy()
                        if (expectedTy == stringTy
                            && (isToStringImplForActual(items, lookup) || isActualTyNumeric())) {
                            add(ConvertToStringFix(element))
                        } else if (expectedTy is TyReference) {
                            if (expectedTy.mutability == Mutability.IMMUTABLE) {
                                if (isTraitWithTySubstImplForActual(lookup, items.Borrow, expectedTy)) {
                                    add(ConvertToBorrowedTyFix(element, expectedTy))
                                }
                                if (isTraitWithTySubstImplForActual(lookup, items.AsRef, expectedTy)) {
                                    add(ConvertToRefTyFix(element, expectedTy))
                                }
                            } else if (expectedTy.mutability == Mutability.MUTABLE && element is RsExpr && element.isMutable
                                && lookup.coercionSequence(actualTy).all { it !is TyReference || it.mutability.isMut }) {
                                if (isTraitWithTySubstImplForActual(lookup, items.BorrowMut, expectedTy)) {
                                    add(ConvertToBorrowedTyWithMutFix(element, expectedTy))
                                }
                                if (isTraitWithTySubstImplForActual(lookup, items.AsMut, expectedTy)) {
                                    add(ConvertToMutTyFix(element, expectedTy))
                                }
                            }
                        } else if (expectedTy is TyAdt && expectedTy.item == items.Result) {
                            val (expOkTy, expErrTy) = expectedTy.typeArguments
                            if (expErrTy == errTyOfTryFromActualImplForTy(expOkTy, items, lookup)) {
                                add(ConvertToTyUsingTryFromTraitFix(element, expOkTy))
                            }
                            if (expErrTy == ifActualIsStrGetErrTyOfFromStrImplForTy(expOkTy, items, lookup)) {
                                add(ConvertToTyUsingFromStrFix(element, expOkTy))
                            }
                        }
                        if (actualTy == stringTy) {
                            if (expectedTy == REF_STR_TY) {
                                add(ConvertToImmutableStrFix(element))
                            } else if (expectedTy == MUT_REF_STR_TY) {
                                add(ConvertToMutStrFix(element))
                            }
                        }

                        val retFix = ChangeReturnTypeFix.createIfCompatible(element, actualTy)
                        if (retFix != null) {
                            add(retFix)
                        }

                        val derefsRefsToExpected = derefRefPathFromActualToExpected(lookup, element)
                        if (derefsRefsToExpected != null) {
                            add(ConvertToTyWithDerefsRefsFix(element, expectedTy, derefsRefsToExpected))
                        }
                    }
                }
            )
        }

        private fun isActualTyNumeric() = actualTy is TyNumeric || actualTy is TyInfer.IntVar || actualTy is TyInfer.FloatVar

        private fun isFromActualImplForExpected(items: KnownItems, lookup: ImplLookup): Boolean {
            val fromTrait = items.From ?: return false
            return lookup.canSelect(TraitRef(expectedTy, fromTrait.withSubst(actualTy)))
        }

        private fun errTyOfTryFromActualImplForTy(ty: Ty, items: KnownItems, lookup: ImplLookup): Ty? {
            val fromTrait = items.TryFrom ?: return null
            val result = lookup.selectProjectionStrict(TraitRef(ty, fromTrait.withSubst(actualTy)),
                fromTrait.associatedTypesTransitively.find { it.name == "Error" } ?: return null)
            return result.ok()?.value
        }

        private fun ifActualIsStrGetErrTyOfFromStrImplForTy(ty: Ty, items: KnownItems, lookup: ImplLookup): Ty? {
            if (lookup.coercionSequence(actualTy).lastOrNull() != TyStr) return null
            val fromStr = items.FromStr ?: return null
            val result = lookup.selectProjectionStrict(TraitRef(ty, BoundElement(fromStr)),
                fromStr.findAssociatedType("Err") ?: return null)
            return result.ok()?.value
        }

        private fun isToOwnedImplWithExpectedForActual(items: KnownItems, lookup: ImplLookup): Boolean {
            val toOwnedTrait = items.ToOwned ?: return false
            val result = lookup.selectProjectionStrictWithDeref(TraitRef(actualTy, BoundElement(toOwnedTrait)),
                toOwnedTrait.findAssociatedType("Owned") ?: return false)
            return expectedTy == result.ok()?.value
        }

        private fun isToStringImplForActual(items: KnownItems, lookup: ImplLookup): Boolean {
            val toStringTrait = items.ToString ?: return false
            return lookup.canSelectWithDeref(TraitRef(actualTy, BoundElement(toStringTrait)))
        }

        private fun isTraitWithTySubstImplForActual(lookup: ImplLookup, trait: RsTraitItem?, ty: TyReference): Boolean =
            trait != null && lookup.canSelectWithDeref(TraitRef(actualTy, trait.withSubst(ty.referenced)))

        private fun expectedFound(expectedTy: Ty, actualTy: Ty): String {
            return "expected `${expectedTy.escaped}`, found `${actualTy.escaped}`"
        }

        /**
         * Try to find a "path" from [actualTy] to [expectedTy] through dereferences and references.
         *
         * The method works by getting coercion sequence of types for the [actualTy] and sequence of the types that can
         * lead to [expectedTy] by adding references. The "expected sequence" is represented by a list of references'
         * mutabilities, and a map from the type X to the index i in the list such that if we apply the references from
         * 0 (inclusive) to i (exclusive) we will get [expectedTy]. For example for [expectedTy] = `&mut&i32` we will
         * have: [mutable, immutable] and {&mut&i32 -> 0, &i32 -> 1, i32 -> 2}. Then, using those data structures, we
         * try to find a first type X of the "actual" sequence that is also in the "expected" sequence, and a number of
         * dereferences to get from [actualTy] to X and references to get from X to [expectedTy]. Finally we try to
         * check if applying the references would agree with the expression's mutability.
         */
        private fun derefRefPathFromActualToExpected(lookup: ImplLookup, element: RsElement): DerefRefPath? {
            // get all the types that can lead to `expectedTy` by adding references to them
            val expectedRefSeq: MutableList<Mutability> = mutableListOf()
            val tyToExpectedRefSeq: Map<Ty, Int> = buildMap {
                put(expectedTy, 0)
                var ty = expectedTy
                var i = 1
                while (ty is TyReference) {
                    expectedRefSeq.add(ty.mutability)
                    ty = ty.referenced
                    put(ty, i++)
                }
            }
            // get all the types we can get by dereferencing `actualTy`
            val actualCoercionSeq = lookup.coercionSequence(actualTy).toList()
            var refSeqEnd: Int? = null
            // for the first type X in the "actual sequence" that is also in the "expected sequence"; get the number of
            // dereferences we need to apply to get to X from `actualTy` and number of references to get to `expectedTy`
            val derefs = actualCoercionSeq.indexOfFirst { refSeqEnd = tyToExpectedRefSeq[it]; refSeqEnd != null }
            val refs = expectedRefSeq.subList(0, refSeqEnd ?: return null)
            // check that mutability of references would not contradict the `element`
            val isSuitableMutability = refs.isEmpty() ||
                !refs.last().isMut ||
                element is RsExpr &&
                element.isMutable &&
                // covers cases like `let mut x: &T = ...`
                actualCoercionSeq.subList(0, derefs + 1).all {
                    it !is TyReference || it.mutability.isMut
                }
            if (!isSuitableMutability) return null
            return DerefRefPath(derefs, refs)
        }

        override fun superFoldWith(folder: TypeFolder): TypeError =
            TypeError(element, expectedTy.foldWith(folder), actualTy.foldWith(folder))

        override fun superVisitWith(visitor: TypeVisitor): Boolean =
            expectedTy.visitWith(visitor) || actualTy.visitWith(visitor)
    }

    class DerefError(
        element: PsiElement,
        val ty: Ty
    ) : RsDiagnostic(element, inspectionClass = RsExperimentalChecksInspection::class.java) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0614,
            "type ${ty.escaped} cannot be dereferenced"
        )
    }

    class AccessError(
        element: PsiElement,
        private val errorCode: RsErrorCode,
        private val itemType: String,
        private val fix: MakePublicFix?
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            errorCode,
            "$itemType `${escapeString(element.text)}` is private",
            fixes = listOfNotNull(fix)
        )
    }

    class StructFieldAccessError(
        element: PsiElement,
        private val fieldName: String,
        private val structName: String,
        private val fix: MakePublicFix?
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0616,
            "Field `${escapeString(fieldName)}` of struct `${escapeString(structName)}` is private",
            fixes = listOfNotNull(fix)
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

    class ImplDropForNonAdtError(
        element: PsiElement
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0120,
            "Drop can be only implemented by structs and enums"
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

    class IncorrectlyPlacedInlineAttr(
        element: PsiElement,
        private val attr: RsAttr
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0518,
            "Attribute should be applied to function or closure",
            fixes = listOf(RemoveAttrFix(attr))
        )
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

    class ImplBothCopyAndDropError(
        element: PsiElement
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0184,
            "Cannot implement both Copy and Drop"
        )
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

    class ExplicitCallToDrop(
        element: PsiElement
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0040,
            errorText(),
            fixes = listOf(ReplaceWithStdMemDropFix(element.parent)) // e.parent: fn's name -> RsMethodCall or RsCallExpr
        )

        private fun errorText(): String {
            return "Explicit calls to `drop` are forbidden. Use `std::mem::drop` instead"
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

    class DuplicateEnumDiscriminant(
        variant: RsEnumVariant,
        private val id: Long
    ) : RsDiagnostic(variant.variantDiscriminant?.expr ?: variant) {
        override fun prepare(): PreparedAnnotation = PreparedAnnotation(
            ERROR,
            E0081,
            "Discriminant value `$id` already exists"
        )
    }

    class ReprForEmptyEnumError(
        val attr: RsAttr,
        element: PsiElement = attr.metaItem.identifier ?: attr.metaItem
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0084,
            "Enum with no variants can't have `repr` attribute",
            fixes = listOf(RemoveAttrFix(attr))
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

    sealed class InvalidStartAttrError(
        element: PsiElement
    ) : RsDiagnostic(element) {
        class ReturnMismatch(element: PsiElement) : RsDiagnostic(element) {
            override fun prepare() = PreparedAnnotation(
                ERROR,
                E0132,
                "Functions with a `start` attribute must return `isize`"
            )
        }

        class InvalidOwner(element: PsiElement) : RsDiagnostic(element) {
            override fun prepare() = PreparedAnnotation(
                ERROR,
                E0132,
                "Start attribute can be placed only on functions"
            )
        }

        class InvalidParam(
            element: PsiElement,
            private val num: Int = -1
        ) : RsDiagnostic(element) {
            override fun prepare() = PreparedAnnotation(
                ERROR,
                E0132,
                "Functions with a `start` attribute must have " + when (num) {
                    0 -> "`isize` as first parameter"
                    1 -> "`*const *const u8` as second parameter"
                    else -> "the following signature: `fn(isize, *const *const u8) -> isize`"
                }
            )
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

    class ReservedLifetimeNameError(
        element: PsiElement,
        private val lifetimeName: String
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0262,
            errorText()
        )

        private fun errorText(): String {
            val name = escapeString(lifetimeName)
            return "`$name` is a reserved lifetime name"
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

    class LoopOnlyKeywordUsedInClosureError(
        element: PsiElement
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0267,
            "`${element.text}` cannot be used in closures, only inside `loop` and `while` blocks"
        )
    }

    class LoopOnlyKeywordUsedOutsideOfLoopError(
        element: PsiElement
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0268,
            "`${element.text}` may only be used inside `loop` and `while` blocks"
        )
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

    class NotTraitError(
        element: PsiElement,
        private val found: RsItemElement
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0404,
            errorText()
        )

        private fun errorText(): String {
            val itemKind = found.itemKindName
            val name = escapeString(found.name)
            return "Expected trait, found $itemKind `$name`"
        }

        private val RsItemElement.itemKindName: String
            get() = when (this) {
                is RsStructItem -> when (kind) {
                    RsStructKind.STRUCT -> "struct"
                    RsStructKind.UNION -> "union"
                }
                is RsEnumItem -> "enum"
                is RsTypeAlias -> "type alias"
                is RsModItem -> "module"
                else -> error("unknown item")
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

    class DuplicateImportError(
        element: PsiElement
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0252,
            errorText()
        )

        private fun errorText(): String {
            return "A second item with name '${element.text}' imported. Try to use an alias."
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

    class ImplSizedError(
        element: PsiElement
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0322,
            "Explicit impls for the `Sized` trait are not permitted"
        )
    }

    class ImplUnsizeError(
        element: PsiElement
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0328,
            "Explicit impls for the `Unsize` trait are not permitted"
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
        val lifetime: RsLifetime
    ) : RsDiagnostic(lifetime) {
        override fun prepare(): PreparedAnnotation {
            return PreparedAnnotation(
                ERROR,
                E0261,
                errorText(),
                fixes = listOfNotNull(CreateLifetimeParameterFromUsageFix.tryCreate(lifetime))
            )
        }

        private fun errorText(): String {
            val lifetimeText = escapeString(element.text)
            return "Use of undeclared lifetime name `$lifetimeText`"
        }
    }

    class InBandAndExplicitLifetimesError(
        val lifetime: RsLifetime
    ) : RsDiagnostic(lifetime) {
        override fun prepare(): PreparedAnnotation {
            return PreparedAnnotation(
                ERROR,
                E0688,
                errorText(),
                fixes = listOfNotNull(CreateLifetimeParameterFromUsageFix.tryCreate(lifetime))
            )
        }

        private fun errorText(): String = "Cannot mix in-band and explicit lifetime definitions"
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

    class ExperimentalFeature(
        element: PsiElement,
        endElement: PsiElement?,
        private val message: String,
        private val fixes: List<LocalQuickFix>
    ) : RsDiagnostic(element, endElement) {
        override fun prepare(): PreparedAnnotation = PreparedAnnotation(
            ERROR,
            E0658,
            header = escapeString(message),
            fixes = fixes
        )
    }

    class UndeclaredTypeOrModule(
        element: PsiElement
    ) : RsDiagnostic(element) {
        override fun prepare(): PreparedAnnotation = PreparedAnnotation(
            ERROR,
            E0433,
            header = escapeString(errorText())
        )

        private fun errorText(): String {
            // TODO: support other cases
            return when (val elementType = element.elementType) {
                RsElementTypes.CRATE -> "`crate` in paths can only be used in start position"
                else -> error("Unexpected element type: `$elementType`")
            }
        }
    }

    class MissingLifetimeSpecifier(
        element: PsiElement
    ) : RsDiagnostic(element) {
        override fun prepare(): PreparedAnnotation = PreparedAnnotation(
            ERROR,
            E0106,
            "Missing lifetime specifier"
        )
    }

    class WrongNumberOfLifetimeArguments(
        element: PsiElement,
        private val expectedLifetimes: Int,
        private val actualLifetimes: Int
    ) : RsDiagnostic(element) {
        override fun prepare(): PreparedAnnotation = PreparedAnnotation(
            ERROR,
            E0107,
            errorText()
        )

        private fun errorText(): String {
            return "Wrong number of lifetime arguments: expected $expectedLifetimes, found $actualLifetimes"
        }
    }

    class ImplForNonAdtError(
        element: PsiElement
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0118,
            "Can impl only `struct`s, `enum`s, `union`s and trait objects"
        )
    }

    class InclusiveRangeWithNoEndError(
        element: PsiElement
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0586,
            "inclusive ranges must be bounded at the end (`..=b` or `a..=b`)"
        )
    }

    class CannotAssignToImmutable(
        element: PsiElement,
        private val message: String,
        private val fix: AddMutableFix? = null
    ) : RsDiagnostic(element) {
        override fun prepare(): PreparedAnnotation = PreparedAnnotation(
            ERROR,
            E0594,
            "Cannot assign to $message",
            fixes = listOfNotNull(fix)
        )
    }

    class CannotReassignToImmutable(
        element: PsiElement,
        private val fix: AddMutableFix?
    ) : RsDiagnostic(element) {
        override fun prepare(): PreparedAnnotation = PreparedAnnotation(
            ERROR,
            E0384,
            "Cannot assign twice to immutable variable",
            fixes = listOfNotNull(fix)
        )
    }

    class IncorrectVisibilityRestriction(
        private val visRestriction: RsVisRestriction
    ) : RsDiagnostic(visRestriction.path) {
        override fun prepare(): PreparedAnnotation = PreparedAnnotation(
            ERROR,
            E0704,
            "Incorrect visibility restriction",
            "Visibility restriction with module path should start with `in` keyword",
            fixes = listOf(FixVisRestriction(visRestriction))
        )
    }

    class ModuleNotFound(
        private val modDecl: RsModDeclItem
    ) : RsDiagnostic(modDecl.identifier) {
        override fun prepare(): PreparedAnnotation = PreparedAnnotation(
            UNKNOWN_SYMBOL,
            E0583,
            "File not found for module `${modDecl.name}`",
            fixes = AddModuleFileFix.createFixes(modDecl, expandModuleFirst = false)
        )
    }

    class NonExhaustiveMatch(
        private val matchExpr: RsMatchExpr,
        private val patterns: List<Pattern>
    ) : RsDiagnostic(matchExpr.match) {
        override fun prepare(): PreparedAnnotation = PreparedAnnotation(
            ERROR,
            E0004,
            "Match must be exhaustive",
            fixes = listOf(AddRemainingArmsFix(matchExpr, patterns), AddWildcardArmFix(matchExpr))
        )
    }

    class ExpectedFunction(callExpr: RsCallExpr) : RsDiagnostic(callExpr.expr) {
        override fun prepare(): PreparedAnnotation = PreparedAnnotation(
            ERROR,
            E0618,
            escapeString("Expected function, found `${element.text}`")
        )
    }

    class UnlabeledControlFlowExpr(element: PsiElement) : RsDiagnostic(element) {
        override fun prepare(): PreparedAnnotation {
            return PreparedAnnotation(
                ERROR,
                E0695,
                "Unlabeled `${element.text}` inside of a labeled block"
            )
        }
    }

    class ReprIntRequired(element: PsiElement) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0732,
            "`#[repr(inttype)]` must be specified"
        )
    }

    class ImplTraitNotAllowedHere(traitType: RsTraitType) : RsDiagnostic(traitType) {
        override fun prepare(): PreparedAnnotation =
            PreparedAnnotation(
                ERROR,
                E0562,
                "`impl Trait` not allowed outside of function and inherent method return types"
            )
    }

    class ImplTraitNotAllowedInPathParams(traitType: RsTraitType) : RsDiagnostic(traitType) {
        override fun prepare(): PreparedAnnotation =
            PreparedAnnotation(
                ERROR,
                E0667,
                "`impl Trait` is not allowed in path parameters"
            )
    }

    class NestedImplTraitNotAllowed(traitType: RsTraitType) : RsDiagnostic(traitType) {
        override fun prepare(): PreparedAnnotation =
            PreparedAnnotation(
                ERROR,
                E0666,
                "nested `impl Trait` is not allowed"
            )
    }

    class MissingFieldsInTuplePattern(
        pat: RsPat,
        private val declaration: RsFieldsOwner,
        private val expectedAmount: Int,
        private val actualAmount: Int
    ) : RsDiagnostic(pat) {
        override fun prepare(): PreparedAnnotation {
            val itemType = if (declaration is RsEnumVariant) "Enum variant" else "Tuple struct"
            return PreparedAnnotation(
                ERROR,
                E0023,
                "$itemType pattern does not correspond to its declaration: expected $expectedAmount ${pluralize("field", expectedAmount)}, found $actualAmount",
                fixes = listOf(AddStructFieldsPatFix(element))
            )
        }
    }

    class MissingFieldsInStructPattern(
        pat: RsPat,
        private val declaration: RsFieldsOwner,
        private val missingFields: List<RsFieldDecl>
    ) : RsDiagnostic(pat) {
        override fun prepare(): PreparedAnnotation {
            val itemType = if (declaration is RsEnumVariant) "Enum variant" else "Struct"
            val missingFieldNames = escapeString(missingFields.joinToString(", ") { "`${it.name!!}`" })
            return PreparedAnnotation(
                ERROR,
                E0027,
                "$itemType pattern does not mention ${pluralize("field", missingFields.size)} $missingFieldNames",
                fixes = listOf(AddStructFieldsPatFix(element))
            )
        }
    }

    class ExtraFieldInStructPattern(private val extraField: RsPatField) : RsDiagnostic(extraField) {
        override fun prepare(): PreparedAnnotation {
            return PreparedAnnotation(
                ERROR,
                E0026,
                "Extra field found in the struct pattern: `${extraField.kind.fieldName}`"
            )
        }
    }

    class ExtraFieldInTupleStructPattern(
        patTupleStruct: RsPatTupleStruct,
        private val extraFieldsAmount: Int,
        private val expectedAmount: Int
    ) : RsDiagnostic(patTupleStruct) {
        override fun prepare(): PreparedAnnotation {
            return PreparedAnnotation(
                ERROR,
                E0023,
                "Extra fields found in the tuple struct pattern: expected $expectedAmount, found $extraFieldsAmount"
            )
        }
    }
}

enum class RsErrorCode {
    E0004, E0023, E0026, E0027, E0040, E0046, E0050, E0060, E0061, E0069, E0081, E0084,
    E0106, E0107, E0118, E0120, E0121, E0124, E0132, E0133, E0184, E0185, E0186, E0198, E0199,
    E0200, E0201, E0202, E0252, E0261, E0262, E0263, E0267, E0268, E0277,
    E0308, E0322, E0328, E0379, E0384,
    E0403, E0404, E0407, E0415, E0424, E0426, E0428, E0433, E0449, E0463,
    E0518, E0562, E0569, E0583, E0586, E0594,
    E0603, E0614, E0616, E0618, E0624, E0658, E0666, E0667, E0688, E0695,
    E0704, E0732;

    val code: String
        get() = toString()
    val infoUrl: String
        get() = "https://doc.rust-lang.org/error-index.html#$code"
}

enum class Severity {
    INFO, WARN, ERROR, UNKNOWN_SYMBOL
}

class PreparedAnnotation(
    val severity: Severity,
    val errorCode: RsErrorCode,
    val header: String,
    val description: String = "",
    val fixes: List<LocalQuickFix> = emptyList()
)

fun RsDiagnostic.addToHolder(holder: RsAnnotationHolder) {
    if (element.isEnabledByCfg) {
        addToHolder(holder.holder)
    }
}

fun RsDiagnostic.addToHolder(holder: AnnotationHolder) {
    val prepared = prepare()

    val textRange = if (endElement != null) {
        TextRange.create(
            element.startOffset,
            endElement.endOffset
        )
    } else {
        element.textRange
    }

    // BACKCOMPAT: 2019.3
    @Suppress("DEPRECATION")
    val ann = holder.createAnnotation(
        prepared.severity.toHighlightSeverity(),
        textRange,
        simpleHeader(prepared.errorCode, prepared.header),
        "<html>${htmlHeader(prepared.errorCode, prepared.header)}<br>${prepared.description}</html>"
    )

    ann.highlightType = prepared.severity.toProblemHighlightType()

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

fun RsDiagnostic.addToHolder(holder: RsProblemsHolder) {
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
    UNKNOWN_SYMBOL -> ProblemHighlightType.LIKE_UNKNOWN_SYMBOL
}

private fun Severity.toHighlightSeverity(): HighlightSeverity = when (this) {
    INFO -> HighlightSeverity.INFORMATION
    WARN -> HighlightSeverity.WARNING
    ERROR, UNKNOWN_SYMBOL -> HighlightSeverity.ERROR
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

// BACKCOMPAT: ???
// Fix for IntelliJ platform bug: https://youtrack.jetbrains.com/issue/IDEA-186991
// replace it with `escapeString()` after the end of support IDEs with the bug
private fun escapeTy(str: String): String = str
    .replace("<", "&#60;")
    .replace(">", "&#62;")
    .replace("&", "&amp;")

private val Ty.escaped get() = escapeTy(toString())
