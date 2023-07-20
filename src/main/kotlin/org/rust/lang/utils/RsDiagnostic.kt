/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.utils

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.util.NlsContexts.Tooltip
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil.pluralize
import com.intellij.psi.PsiElement
import com.intellij.util.ThreeState
import com.intellij.xml.util.XmlStringUtil.escapeString
import org.jetbrains.annotations.Nls
import org.rust.RsBundle
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.cargo.toolchain.RustChannel
import org.rust.cargo.toolchain.impl.RustcVersion
import org.rust.ide.annotator.RsAnnotationHolder
import org.rust.ide.annotator.RsErrorAnnotator
import org.rust.ide.fixes.*
import org.rust.ide.inspections.RsExperimentalChecksInspection
import org.rust.ide.inspections.RsProblemsHolder
import org.rust.ide.inspections.RsTypeCheckInspection
import org.rust.ide.inspections.RsWrongAssocTypeArgumentsInspection
import org.rust.ide.presentation.render
import org.rust.ide.presentation.shortPresentableText
import org.rust.ide.refactoring.implementMembers.ImplementMembersFix
import org.rust.ide.utils.checkMatch.Pattern
import org.rust.ide.utils.import.RsImportHelper.getTypeReferencesInfoFromTys
import org.rust.lang.core.CompilerFeature.Companion.ABI_AMDGPU_KERNEL
import org.rust.lang.core.CompilerFeature.Companion.ABI_AVR_INTERRUPT
import org.rust.lang.core.CompilerFeature.Companion.ABI_C_CMSE_NONSECURE_CALL
import org.rust.lang.core.CompilerFeature.Companion.ABI_EFIAPI
import org.rust.lang.core.CompilerFeature.Companion.ABI_MSP430_INTERRUPT
import org.rust.lang.core.CompilerFeature.Companion.ABI_PTX
import org.rust.lang.core.CompilerFeature.Companion.ABI_THISCALL
import org.rust.lang.core.CompilerFeature.Companion.ABI_UNADJUSTED
import org.rust.lang.core.CompilerFeature.Companion.ABI_VECTORCALL
import org.rust.lang.core.CompilerFeature.Companion.ABI_X86_INTERRUPT
import org.rust.lang.core.CompilerFeature.Companion.C_UNWIND
import org.rust.lang.core.CompilerFeature.Companion.INTRINSICS
import org.rust.lang.core.CompilerFeature.Companion.PLATFORM_INTRINSICS
import org.rust.lang.core.CompilerFeature.Companion.UNBOXED_CLOSURES
import org.rust.lang.core.CompilerFeature.Companion.WASM_ABI
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.ImplLookup
import org.rust.lang.core.resolve.KnownItems
import org.rust.lang.core.resolve.Namespace
import org.rust.lang.core.types.*
import org.rust.lang.core.types.infer.*
import org.rust.lang.core.types.ty.*
import org.rust.lang.utils.RsErrorCode.*
import org.rust.lang.utils.Severity.*
import org.rust.stdext.buildList
import org.rust.stdext.buildMap

private val REF_STR_TY = TyReference(TyStr.INSTANCE, Mutability.IMMUTABLE)
private val MUT_REF_STR_TY = TyReference(TyStr.INSTANCE, Mutability.MUTABLE)

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
        @Nls
        private val description: String? = if (expectedTy.hasTyInfer || actualTy.hasTyInfer) {
            // if types contain infer types, they will be replaced with more specific (e.g. `{integer}` with `i32`)
            // so we capture string representation of types right here
            expectedFound(element, expectedTy, actualTy)
        } else {
            null
        }

        override fun prepare(): PreparedAnnotation {
            return PreparedAnnotation(
                ERROR,
                E0308,
                RsBundle.message("inspection.message.mismatched.types"),
                description ?: expectedFound(element, expectedTy, actualTy),
                fixes = buildList {
                    if (element is RsElement) {
                        if (element is RsExpr) {
                            addAll(createExprQuickFixes(element))
                        }

                        val retFix = ChangeReturnTypeFix.createIfCompatible(element, actualTy)
                        if (retFix != null) {
                            add(retFix)
                        }

                        val reprFix = ChangeReprAttributeFix.createIfCompatible(element, actualTy)
                        if (reprFix != null) {
                            add(reprFix)
                        }
                    }

                    val parent = element.parent
                    if (parent is RsLetDecl && parent.typeReference != null) {
                        val pat = parent.pat
                        if (pat is RsPatIdent &&
                            !actualTy.containsTyOfClass(TyUnknown::class.java, TyAnon::class.java)) {
                            parent.typeReference?.let {
                                add(ConvertTypeReferenceFix(it, pat.patBinding.identifier.text ?: "?", actualTy))
                            }
                        }
                    }
                }.toQuickFixInfo()
            )
        }

        private fun createExprQuickFixes(element: RsExpr): List<LocalQuickFix> {
            return buildList {
                if (expectedTy is TyNumeric && isActualTyNumeric()) {
                    add(AddAsTyFix(element, expectedTy))
                } else {
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
                    if (expectedTy.isEquivalentTo(stringTy)
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
                        } else if (expectedTy.mutability == Mutability.MUTABLE) {
                            if (actualTy is TyReference && actualTy.mutability == Mutability.IMMUTABLE &&
                                element is RsUnaryExpr && element.operatorType == UnaryOperator.REF
                            ) {
                                add(ChangeRefToMutableFix(element))
                            }

                            if (element.isMutable && lookup.coercionSequence(actualTy).all { it !is TyReference || it.mutability.isMut }) {
                                if (isTraitWithTySubstImplForActual(lookup, items.BorrowMut, expectedTy)) {
                                    add(ConvertToBorrowedTyWithMutFix(element, expectedTy))
                                }
                                if (isTraitWithTySubstImplForActual(lookup, items.AsMut, expectedTy)) {
                                    add(ConvertToMutTyFix(element, expectedTy))
                                }
                            }
                        }
                    } else if (expectedTy is TyAdt && expectedTy.item == items.Result) {
                        val (expOkTy, expErrTy) = expectedTy.typeArguments
                        if (expErrTy.isEquivalentTo(errTyOfTryFromActualImplForTy(expOkTy, items, lookup))) {
                            add(ConvertToTyUsingTryFromTraitFix(element, expOkTy))
                        }
                        if (expErrTy.isEquivalentTo(ifActualIsStrGetErrTyOfFromStrImplForTy(expOkTy, items, lookup))) {
                            add(ConvertToTyUsingFromStrFix(element, expOkTy))
                        }
                    }
                    if (actualTy.isEquivalentTo(stringTy)) {
                        if (expectedTy.isEquivalentTo(REF_STR_TY)) {
                            add(ConvertToImmutableStrFix(element))
                        } else if (expectedTy.isEquivalentTo(MUT_REF_STR_TY)) {
                            add(ConvertToMutStrFix(element))
                        }
                    }

                    val derefsRefsToExpected = derefRefPathFromActualToExpected(lookup, element)
                    if (derefsRefsToExpected != null) {
                        add(ConvertToTyWithDerefsRefsFix(element, expectedTy, derefsRefsToExpected))
                    }
                }
            }
        }

        private fun isActualTyNumeric() = actualTy is TyNumeric || actualTy is TyInfer.IntVar || actualTy is TyInfer.FloatVar

        private fun isFromActualImplForExpected(items: KnownItems, lookup: ImplLookup): Boolean {
            val fromTrait = items.From ?: return false
            return lookup.canSelect(TraitRef(expectedTy, fromTrait.withSubst(actualTy)))
        }

        private fun errTyOfTryFromActualImplForTy(ty: Ty, items: KnownItems, lookup: ImplLookup): Ty? {
            val fromTrait = items.TryFrom ?: return null
            val errorType = fromTrait.associatedTypesTransitively.find { it.name == "Error" } ?: return null
            val result = lookup.selectProjectionStrict(TraitRef(ty, fromTrait.withSubst(actualTy)), errorType.withSubst())
            return result.ok()?.value
        }

        private fun ifActualIsStrGetErrTyOfFromStrImplForTy(ty: Ty, items: KnownItems, lookup: ImplLookup): Ty? {
            if (lookup.coercionSequence(actualTy).lastOrNull() !is TyStr) return null
            val fromStr = items.FromStr ?: return null
            val errType = fromStr.findAssociatedType("Err") ?: return null
            val result = lookup.selectProjectionStrict(TraitRef(ty, fromStr.withSubst()), errType.withSubst())
            return result.ok()?.value
        }

        private fun isToOwnedImplWithExpectedForActual(items: KnownItems, lookup: ImplLookup): Boolean {
            val toOwnedTrait = items.ToOwned ?: return false
            val result = lookup.selectProjectionStrictWithDeref(
                TraitRef(actualTy, BoundElement(toOwnedTrait)),
                toOwnedTrait.findAssociatedType("Owned")?.withSubst() ?: return false
            )
            return expectedTy.isEquivalentTo(result.ok()?.value)
        }

        private fun isToStringImplForActual(items: KnownItems, lookup: ImplLookup): Boolean {
            val toStringTrait = items.ToString ?: return false
            return lookup.canSelectWithDeref(TraitRef(actualTy, BoundElement(toStringTrait)))
        }

        private fun isTraitWithTySubstImplForActual(lookup: ImplLookup, trait: RsTraitItem?, ty: TyReference): Boolean =
            trait != null && lookup.canSelectWithDeref(TraitRef(actualTy, trait.withSubst(ty.referenced)))

        @Nls
        private fun expectedFound(element: PsiElement, expectedTy: Ty, actualTy: Ty): String {
            val useQualifiedName = getConflictingNames(element, expectedTy, actualTy)
            return RsBundle.message("expected.0.found.1", expectedTy.rendered(useQualifiedName),actualTy.rendered(useQualifiedName))
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
            RsBundle.message("inspection.message.type.cannot.be.dereferenced", ty.rendered(getConflictingNames(element, ty)))
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
            RsBundle.message("inspection.message.private", itemType, element.text),
            fixes = listOfFixes(fix)
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
            if (element.parent is RsStructLiteralField) E0451 else E0616,
            RsBundle.message("inspection.message.field.struct.private", fieldName, structName),
            fixes = listOfFixes(fix)
        )
    }

    class UnsafeError(
        element: RsExpr,
        @InspectionMessage private val message: String
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0133,
            message,
            fixes = listOfFixes(SurroundWithUnsafeFix(element as RsExpr), AddUnsafeFix.create(element))
        )
    }

    class TypePlaceholderForbiddenError(
        element: PsiElement
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0121,
            RsBundle.message("inspection.message.type.placeholder.not.allowed.within.types.on.item.signatures")
        )
    }

    class ImplDropForNonAdtError(
        element: PsiElement
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0120,
            RsBundle.message("inspection.message.drop.can.be.only.implemented.by.structs.enums")
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
                RsBundle.message("inspection.message.self.keyword.was.used.in.static.method"),
                fixes = fixes.toQuickFixInfo()
            )
        }
    }

    class UnnecessaryVisibilityQualifierError(
        element: RsVis
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0449,
            RsBundle.message("inspection.message.unnecessary.visibility.qualifier"),
            fixes = listOfFixes(RemoveElementFix(element, "visibility qualifier"))
        )
    }

    class UnsafeNegativeImplementationError(
        element: PsiElement
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0198,
            RsBundle.message("inspection.message.negative.implementations.are.not.unsafe")
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

        @InspectionMessage
        private fun errorText(): String {
            return RsBundle.message("inspection.message.implementing.trait.not.unsafe", traitName)
        }
    }

    class TraitMissingUnsafeImplError(
        element: PsiElement,
        private val traitName: String
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0200,
            errorText(),
            fixes = listOfFixes(AddUnsafeFix.create(element))
        )

        @InspectionMessage
        private fun errorText(): String {
            return RsBundle.message("inspection.message.trait.requires.unsafe.impl.declaration", traitName)
        }
    }

    class IncorrectlyPlacedInlineAttr(
        element: PsiElement,
        private val attr: RsAttr
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0518,
            RsBundle.message("inspection.message.attribute.should.be.applied.to.function.or.closure"),
            fixes = listOfFixes(RemoveAttrFix(attr))
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

        @InspectionMessage
        private fun errorText(): String {
            return RsBundle.message("inspection.message.requires.unsafe.impl.declaration.due.to.attribute", attrRequiringUnsafeImpl)
        }
    }

    class UnknownMemberInTraitError(
        element: PsiElement,
        private val member: RsAbstractable,
        private val traitName: String
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            errorCode(),
            errorText(),
            fixes = listOfFixes(AddDefinitionToTraitFix.createIfCompatible(member))
        )

        private fun errorCode(): RsErrorCode =
            when (member) {
                is RsTypeAlias -> E0437
                is RsConstant -> E0438
                else -> E0407
            }

        @InspectionMessage
        private fun errorText(): String {
            val itemType = when (member) {
                is RsTypeAlias -> RsBundle.message("inspection.message.type2")
                is RsConstant -> RsBundle.message("inspection.message.const2")
                else -> RsBundle.message("inspection.message.method")
            }
            return RsBundle.message("inspection.message.not.member.trait", itemType, member.name?:"", traitName)
        }
    }

    class MismatchMemberInTraitImplError(
        element: PsiElement,
        private val member: RsAbstractable,
        private val traitName: String,
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            errorCode(),
            errorText(),
        )

        private fun errorCode(): RsErrorCode =
            when (member) {
                is RsTypeAlias -> E0325
                is RsConstant -> E0323
                else -> E0324
            }

        @InspectionMessage
        private fun errorText(): String {
            val itemType = when (member) {
                is RsTypeAlias -> RsBundle.message("inspection.message.type")
                is RsConstant -> RsBundle.message("inspection.message.const")
                else -> RsBundle.message("intention.name.method")
            }
            return RsBundle.message("inspection.message.item.associated.which.doesn.t.match.its.trait", member.name?:"", itemType, traitName)
        }
    }

    class ImplBothCopyAndDropError(
        element: PsiElement
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0184,
            RsBundle.message("inspection.message.cannot.implement.both.copy.drop")
        )
    }

    abstract class MissingSelfError(
        element: PsiElement,
        protected val selfParameter: RsSelfParameter
    ) : RsDiagnostic(element) {
        protected fun getFixes(removeElement: RsFunction, addElement: RsFunction): List<QuickFixWithRange> {
            val fixes = mutableListOf<LocalQuickFix>()
            if (removeElement.containingCrate.origin == PackageOrigin.WORKSPACE) {
                fixes.add(RemoveSelfFix(removeElement))
            }
            if (addElement.containingCrate.origin == PackageOrigin.WORKSPACE) {
                fixes.add(AddSelfFix(addElement, AddSelfFix.SelfType.fromSelf(selfParameter)))
            }
            return fixes.toQuickFixInfo()
        }
    }

    class DeclMissingFromTraitError(
        element: PsiElement,
        private val fn: RsFunction,
        private val superFn: RsFunction,
        selfParameter: RsSelfParameter
    ) : MissingSelfError(element, selfParameter) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0185,
            errorText(),
            fixes = getFixes(fn, superFn)
        )

        @InspectionMessage
        private fun errorText(): String {
            return RsBundle.message("inspection.message.method.has.declaration.in.impl.but.not.in.trait", fn.name?:"", selfParameter.canonicalDecl)
        }
    }

    class DeclMissingFromImplError(
        element: PsiElement,
        private val fn: RsFunction,
        private val superFn: RsFunction,
        selfParameter: RsSelfParameter
    ) : MissingSelfError(element, selfParameter) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0186,
            errorText(),
            fixes = getFixes(superFn, fn)
        )

        @InspectionMessage
        private fun errorText(): String {
            return RsBundle.message("inspection.message.method.has.declaration.in.trait.but.not.in.impl", fn.name?:"",selfParameter.canonicalDecl)
        }
    }

    class ExplicitCallToDrop(
        element: PsiElement
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0040,
            errorText(),
            fixes = listOfFixes(ReplaceWithStdMemDropFix(element.parent)) // e.parent: fn's name -> RsMethodCall or RsCallExpr
        )

        @InspectionMessage
        private fun errorText(): String {
            return RsBundle.message("inspection.message.explicit.calls.to.drop.are.forbidden.use.std.mem.drop.instead")
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

        @InspectionMessage
        private fun errorText(): String {
            return RsBundle.message("inspection.message.method.has.but.declaration.in.trait.has", fn.name?:"", paramsCount, pluralize("parameter", paramsCount), traitName, superParamsCount)
        }
    }

    class CastAsBoolError(private val castExpr: RsCastExpr) : RsDiagnostic(castExpr) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0054,
            RsBundle.message("inspection.message.it.not.allowed.to.cast.to.bool"),
            fixes = listOfFixes(CompareWithZeroFix.createIfCompatible(castExpr))
        )
    }

    class ConstItemReferToStaticError(element: RsElement, private val constContext: RsConstContextKind) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0013,
            when (constContext) {
                is RsConstContextKind.Constant -> RsBundle.message("inspection.message.const.cannot.refer.to.static", constContext.psi.name.orEmpty(), element.text)

                is RsConstContextKind.ConstFn -> RsBundle.message("inspection.message.constant.function.cannot.refer.to.static", constContext.psi.name.orEmpty(), element.text)

                is RsConstContextKind.EnumVariantDiscriminant -> RsBundle.message("inspection.message.enum.variant.s.discriminant.value.cannot.refer.to.static", constContext.psi.name.orEmpty(), element.text)

                RsConstContextKind.ArraySize -> RsBundle.message("inspection.message.array.size.cannot.refer.to.static", element.text)
                RsConstContextKind.ConstGenericArgument -> RsBundle.message("inspection.message.const.generic.argument.cannot.refer.to.static", element.text)
            }
        )
    }

    class IncorrectFunctionArgumentCountError(
        element: PsiElement,
        endElement: PsiElement?,
        private val expectedCount: Int,
        private val realCount: Int,
        private val functionType: FunctionType = FunctionType.FUNCTION,
        private val fixes: List<QuickFixWithRange> = emptyList(),
    ) : RsDiagnostic(element, endElement) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            functionType.errorCode,
            errorText(),
            fixes = fixes,
        )

        @InspectionMessage
        private fun errorText(): String {
            return RsBundle.message("inspection.message.this.function.takes.choice.at.least.but.choice.was.were.supplied", if (functionType.variadic) 0 else 1, expectedCount, pluralize("parameter", expectedCount), realCount, pluralize("parameter", realCount), if (realCount == 1) 0 else 1)
        }

        enum class FunctionType(val variadic: Boolean, val errorCode: RsErrorCode) {
            VARIADIC_FUNCTION(true, E0060),
            FUNCTION(false, E0061),
            CLOSURE(false, E0057)
        }
    }

    class ReturnMustHaveValueError(
        element: PsiElement
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0069,
            RsBundle.message("inspection.message.return.in.function.whose.return.type.not")
        )
    }

    class DuplicateEnumDiscriminant(
        variant: RsEnumVariant,
        private val id: Long
    ) : RsDiagnostic(variant.variantDiscriminant?.expr ?: variant) {
        override fun prepare(): PreparedAnnotation = PreparedAnnotation(
            ERROR,
            E0081,
            RsBundle.message("inspection.message.discriminant.value.already.exists", id)
        )
    }

    class ReprForEmptyEnumError(
        val attr: RsAttr,
        element: PsiElement = attr.metaItem.path?.referenceNameElement ?: attr.metaItem
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0084,
            RsBundle.message("inspection.message.enum.with.no.variants.can.t.have.repr.attribute"),
            fixes = listOfFixes(RemoveAttrFix(attr))
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

        @InspectionMessage
        private fun errorText(): String {
            return RsBundle.message("inspection.message.field.already.declared", fieldName)
        }
    }

    sealed class InvalidStartAttrError(
        element: PsiElement
    ) : RsDiagnostic(element) {
        class ReturnMismatch(element: PsiElement) : RsDiagnostic(element) {
            override fun prepare() = PreparedAnnotation(
                ERROR,
                E0132,
                RsBundle.message("inspection.message.functions.with.start.attribute.must.return.isize")
            )
        }

        class InvalidOwner(element: PsiElement) : RsDiagnostic(element) {
            override fun prepare() = PreparedAnnotation(
                ERROR,
                E0132,
                RsBundle.message("inspection.message.start.attribute.can.be.placed.only.on.functions")
            )
        }

        class InvalidParam(
            element: PsiElement,
            private val num: Int = -1
        ) : RsDiagnostic(element) {
            override fun prepare() = PreparedAnnotation(
                ERROR,
                E0132,
                RsBundle.message(
                    "inspection.message.functions.with.start.attribute.must.have", when (num) {
                    0 -> "`isize` as first parameter"
                    1 -> "`*const *const u8` as second parameter"
                    else -> "the following signature: `fn(isize, *const *const u8) -> isize`"
                }
                )
            )
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

        @InspectionMessage
        private fun errorText(): String {
            return RsBundle.message("inspection.message.reserved.lifetime.name", lifetimeName)
        }
    }

    class LoopOnlyKeywordUsedInClosureError(
        element: PsiElement
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0267,
            RsBundle.message("inspection.message.cannot.be.used.in.closures.only.inside.loop.while.blocks", element.text)
        )
    }

    class LoopOnlyKeywordUsedOutsideOfLoopError(
        element: PsiElement
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0268,
            RsBundle.message("inspection.message.may.only.be.used.inside.loop.while.blocks", element.text)
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

        @InspectionMessage
        private fun errorText(): String {
            return RsBundle.message("inspection.message.identifier.bound.more.than.once.in.this.parameter.list", fieldName)
        }
    }

    class RepeatedIdentifierInPattern(
        repeatedIdentifier: RsPatBinding,
        private val name: String
    ) : RsDiagnostic(repeatedIdentifier) {
        override fun prepare(): PreparedAnnotation {
            return PreparedAnnotation(
                ERROR,
                E0416,
                RsBundle.message("inspection.message.identifier.bound.more.than.once.in.same.pattern", name)
            )
        }
    }

    class DuplicateGenericParameterError(
        element: PsiElement,
        private val genericParamName: String
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0403,
            errorText()
        )

        @InspectionMessage
        private fun errorText(): String {
            return RsBundle.message("inspection.message.name.already.used.for.generic.parameter.in.this.item.s.generic.parameters", genericParamName)
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

        @InspectionMessage
        private fun errorText(): String {
            val itemKind = found.itemKindName
            val name = found.name
            return RsBundle.message("inspection.message.expected.trait.found", itemKind, name?:"")
        }
    }

    class DuplicateAssociatedItemError(
        element: PsiElement,
        private val fieldName: String
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0201,
            errorText()
        )

        @InspectionMessage
        private fun errorText(): String {
            return RsBundle.message("inspection.message.duplicate.definitions.with.name", fieldName)
        }
    }

    /**
     * [E0428] - item            vs    item
     * [E0255] - item            vs    import
     * [E0260] - item            vs    extern crate
     * [E0252] - import          vs    import
     * [E0254] - import          vs    extern crate
     * [E0259] - extern crate    vs    extern crate
     */
    class DuplicateDefinitionError private constructor(
        element: PsiElement,
        private val itemType: String,
        private val itemName: String,
        private val scopeType: String,
        private val errorCode: RsErrorCode,
    ) : RsDiagnostic(element) {

        constructor(
            element: PsiElement,
            itemNamespace: Namespace,
            itemName: String,
            scope: PsiElement,
            errorCode: RsErrorCode,
        ) : this(element, itemNamespace.itemName, itemName, scope.formatScope(), errorCode)

        // TODO: provide quick fixes:
        //  - add type alias for `use` and `extern crate` items
        //  - navigate to/show other items
        override fun prepare(): PreparedAnnotation = PreparedAnnotation(
            ERROR,
            errorCode,
            RsBundle.message("inspection.message.name.defined.multiple.times", itemName),
            RsBundle.message("tooltip.must.be.defined.only.once.in.namespace.this", itemName, itemType, scopeType)
        )

        companion object {
            private fun PsiElement.formatScope(): String =
                when (this) {
                    is RsBlock -> "block"
                    is RsMod, is RsForeignModItem -> "module"
                    is RsTraitItem -> "trait"
                    is RsEnumBody -> "enum"
                    else -> "scope"
                }
        }
    }

    class ImplSizedError(
        element: PsiElement
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0322,
            RsBundle.message("inspection.message.explicit.impls.for.sized.trait.are.not.permitted")
        )
    }

    class ImplUnsizeError(
        element: PsiElement
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0328,
            RsBundle.message("inspection.message.explicit.impls.for.unsize.trait.are.not.permitted")
        )
    }

    class ConstTraitFnError(
        element: PsiElement
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0379,
            RsBundle.message("inspection.message.trait.functions.cannot.be.declared.const")
        )
    }

    class UndeclaredLabelError(
        element: RsMandatoryReferenceElement
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0426,
            errorText()
        )

        @InspectionMessage
        private fun errorText(): String {
            return RsBundle.message("inspection.message.use.undeclared.label", element.text)
        }
    }

    class UnreachableLabelError(
        element: RsMandatoryReferenceElement
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0767,
            errorText()
        )

        @InspectionMessage
        private fun errorText(): String {
            return RsBundle.message("inspection.message.use.unreachable.label", element.text)
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
                fixes = listOfFixes(CreateLifetimeParameterFromUsageFix.tryCreate(lifetime))
            )
        }

        @InspectionMessage
        private fun errorText(): String {
            return RsBundle.message("inspection.message.use.undeclared.lifetime.name", element.text)
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
            fixes = listOfFixes(ImplementMembersFix(impl))
        )

        @InspectionMessage
        private fun errorText(): String {
            return RsBundle.message("inspection.message.not.all.trait.items.implemented.missing", missing)
        }
    }

    class CrateNotFoundError(
        startElement: PsiElement,
        private val crateName: String
    ) : RsDiagnostic(startElement) {
        override fun prepare() = PreparedAnnotation(
            UNKNOWN_SYMBOL,
            E0463,
            errorText()
        )

        @InspectionMessage
        private fun errorText(): String {
            return RsBundle.message("inspection.message.can.t.find.crate.for", crateName)
        }
    }

    class TraitIsNotImplemented(
        element: RsElement,
        @InspectionMessage private val description: String,
        private val fixes: List<LocalQuickFix>,
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0277,
            description,
            fixes = listOfFixes(*fixes.toTypedArray())
        )
    }

    class SizedTraitIsNotImplemented(
        element: RsTypeReference,
        private val ty: Ty
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0277,
            header = RsBundle.message("inspection.message.trait.bound.std.marker.sized.not.satisfied", ty),
            description = RsBundle.message("tooltip.does.not.have.constant.size.known.at.compile.time", ty),
            fixes = listOfFixes(ConvertToReferenceFix(element), ConvertToBoxFix(element))
        )
    }

    class SuperTraitIsNotImplemented(
        element: RsTraitRef,
        type: Ty,
        private val missingTrait: String,
        private val fix: QuickFixWithRange?,
    ) : RsDiagnostic(element) {
        private val typeText = type.shortPresentableText

        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0277,
            header = RsBundle.message("inspection.message.trait.bound.not.satisfied", typeText, missingTrait),
            description = RsBundle.message("tooltip.trait.not.implemented.for", missingTrait, typeText),
            fixes = listOfNotNull(fix)
        )
    }

    class ExperimentalFeature(
        element: PsiElement,
        endElement: PsiElement?,
        @InspectionMessage private val message: String,
        private val fixes: List<LocalQuickFix>
    ) : RsDiagnostic(element, endElement) {
        override fun prepare(): PreparedAnnotation = PreparedAnnotation(
            ERROR,
            E0658,
            header = message,
            fixes = fixes.toQuickFixInfo()
        )
    }

    class RemovedFeature(
        element: PsiElement,
        endElement: PsiElement?,
        @InspectionMessage private val message: String,
        private val fixes: List<LocalQuickFix>
    ) : RsDiagnostic(element, endElement) {
        override fun prepare(): PreparedAnnotation = PreparedAnnotation(
            ERROR,
            null,
            header = message,
            fixes = fixes.toQuickFixInfo()
        )
    }

    class UndeclaredTypeOrModule(
        element: PsiElement
    ) : RsDiagnostic(element) {
        override fun prepare(): PreparedAnnotation = PreparedAnnotation(
            ERROR,
            E0433,
            header = errorText()
        )

        @InspectionMessage
        private fun errorText(): String {
            // TODO: support other cases
            return when (val elementType = element.elementType) {
                RsElementTypes.CRATE -> RsBundle.message("inspection.message.crate.in.paths.can.only.be.used.in.start.position")
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
            RsBundle.message("inspection.message.missing.lifetime.specifier")
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

        @InspectionMessage
        private fun errorText(): String {
            return RsBundle.message("inspection.message.wrong.number.lifetime.arguments.expected.found", expectedLifetimes, actualLifetimes)
        }
    }

    class WrongNumberOfGenericArguments(
        element: PsiElement,
        @InspectionMessage private val errorText: String,
        private val fixes: List<LocalQuickFix>
    ) : RsDiagnostic(element) {
        override fun prepare(): PreparedAnnotation {
            return PreparedAnnotation(
                ERROR,
                E0107,
                errorText,
                fixes = fixes.toQuickFixInfo()
            )
        }
    }

    class WrongOrderOfGenericArguments(
        element: PsiElement,
        @InspectionMessage private val errorText: String,
        private val fixes: List<LocalQuickFix>
    ) : RsDiagnostic(element) {
        override fun prepare(): PreparedAnnotation = PreparedAnnotation(
            ERROR,
            E0747,
            errorText,
            fixes = fixes.toQuickFixInfo()
        )
    }

    class WrongNumberOfGenericParameters(element: PsiElement, @InspectionMessage private val errorText: String) : RsDiagnostic(element) {
        override fun prepare(): PreparedAnnotation {
            return PreparedAnnotation(
                ERROR,
                E0049,
                errorText
            )
        }
    }

    class ImplForNonAdtError(
        element: PsiElement
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0118,
            RsBundle.message("inspection.message.can.impl.only.struct.s.enum.s.union.s.trait.objects")
        )
    }

    class InclusiveRangeWithNoEndError(
        element: PsiElement
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0586,
            RsBundle.message("inspection.message.inclusive.ranges.must.be.bounded.at.end.b.or.b")
        )
    }

    class InvalidReprAlign(
        element: PsiElement,
        @InspectionMessage private val message: String,
        private val fixes: List<LocalQuickFix> = emptyList()
    ): RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0589,
            message,
            fixes = fixes.toQuickFixInfo()
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
            RsBundle.message("inspection.message.cannot.assign.to", message),
            fixes = listOfFixes(fix)
        )
    }

    class CannotReassignToImmutable(
        element: PsiElement,
        private val fix: AddMutableFix?
    ) : RsDiagnostic(element) {
        override fun prepare(): PreparedAnnotation = PreparedAnnotation(
            ERROR,
            E0384,
            RsBundle.message("inspection.message.cannot.assign.twice.to.immutable.variable"),
            fixes = listOfFixes(fix)
        )
    }

    class IncorrectVisibilityRestriction(
        private val visRestriction: RsVisRestriction
    ) : RsDiagnostic(visRestriction.path) {
        override fun prepare(): PreparedAnnotation = PreparedAnnotation(
            ERROR,
            E0704,
            RsBundle.message("inspection.message.incorrect.visibility.restriction"),
            RsBundle.message("tooltip.visibility.restriction.with.module.path.should.start.with.in.keyword"),
            fixes = listOfFixes(FixVisRestriction(visRestriction))
        )
    }

    class AsyncNonMoveClosureWithParameters(
        asyncElement: PsiElement,
        parameterList: RsValueParameterList
    ) : RsDiagnostic(asyncElement, parameterList) {
        override fun prepare(): PreparedAnnotation = PreparedAnnotation(
            ERROR,
            E0708,
            RsBundle.message("inspection.message.async.non.move.closures.with.parameters.are.currently.not.supported")
        )
    }

    class RustHasNoIncDecOperator(element: RsElement) : RsDiagnostic(element) {
        private val operatorName: String
            get() = when (element.parent) {
                is RsPrefixIncExpr -> "prefix increment"
                is RsPostfixIncExpr -> "postfix increment"
                is RsPostfixDecExpr -> "postfix decrement"
                else -> error("invalid operator")
            }

        override fun prepare(): PreparedAnnotation = PreparedAnnotation(
            ERROR,
            null,
            RsBundle.message("inspection.message.rust.has.no.incdec.operator", operatorName),
            fixes = listOfFixes(ReplaceIncDecOperatorFix.create(element))
        )
    }

    class VisibilityRestrictionMustBeAncestorModule(element: PsiElement) : RsDiagnostic(element) {
        override fun prepare(): PreparedAnnotation = PreparedAnnotation(
            ERROR,
            E0742,
            RsBundle.message("inspection.message.visibilities.can.only.be.restricted.to.ancestor.modules"),
        )
    }

    class ModuleNotFound(
        private val modDecl: RsModDeclItem
    ) : RsDiagnostic(modDecl.identifier) {
        override fun prepare(): PreparedAnnotation = PreparedAnnotation(
            UNKNOWN_SYMBOL,
            E0583,
            RsBundle.message("inspection.message.file.not.found.for.module", modDecl.name ?: ""),
            fixes = AddModuleFileFix.createFixes(modDecl, expandModuleFirst = false).toQuickFixInfo()
        )
    }

    class NonExhaustiveMatch(
        private val matchExpr: RsMatchExpr,
        private val patterns: List<Pattern>
    ) : RsDiagnostic(matchExpr.match) {
        override fun prepare(): PreparedAnnotation = PreparedAnnotation(
            ERROR,
            E0004,
            RsBundle.message("inspection.message.match.must.be.exhaustive"),
            fixes = listOfFixes(
                AddRemainingArmsFix(matchExpr, patterns).takeIf { patterns.isNotEmpty() },
                AddWildcardArmFix(matchExpr).takeIf { matchExpr.arms.isNotEmpty() }
            )
        )
    }

    class ExpectedFunction(callExpr: RsCallExpr) : RsDiagnostic(callExpr.expr) {
        override fun prepare(): PreparedAnnotation = PreparedAnnotation(
            ERROR,
            E0618,
            RsBundle.message("inspection.message.expected.function.found", element.text)
        )
    }

    class IncorrectlyDeclaredAlignRepresentationHint(
        element: PsiElement,
        @InspectionMessage private val message: String,
        private val fixes: List<LocalQuickFix> = emptyList()
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0693,
            message,
            fixes = fixes.toQuickFixInfo()
        )
    }

    class UnlabeledControlFlowExpr(element: PsiElement) : RsDiagnostic(element) {
        override fun prepare(): PreparedAnnotation {
            return PreparedAnnotation(
                ERROR,
                E0695,
                RsBundle.message("inspection.message.unlabeled.inside.labeled.block", element.text)
            )
        }
    }

    class ReprIntRequired(element: PsiElement) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0732,
            RsBundle.message("inspection.message.repr.inttype.must.be.specified")
        )
    }

    class NonStructuralMatchTypeAsConstGenericParameter(
        element: PsiElement,
        private val typeName: String
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0741,
            RsBundle.message("inspection.message.doesn.t.derive.both.partialeq.eq", typeName)
        )
    }

    class ImplTraitNotAllowedHere(traitType: RsTraitType, val fixes: List<LocalQuickFix> = emptyList()) : RsDiagnostic(traitType) {
        override fun prepare(): PreparedAnnotation =
            PreparedAnnotation(
                ERROR,
                E0562,
                RsBundle.message("inspection.message.impl.trait.not.allowed.outside.function.inherent.method.return.types"),
                fixes = fixes.toQuickFixInfo()
            )
    }

    class ImplTraitNotAllowedInPathParams(traitType: RsTraitType) : RsDiagnostic(traitType) {
        override fun prepare(): PreparedAnnotation =
            PreparedAnnotation(
                ERROR,
                E0667,
                RsBundle.message("inspection.message.impl.trait.not.allowed.in.path.parameters")
            )
    }

    class NestedImplTraitNotAllowed(traitType: RsTraitType) : RsDiagnostic(traitType) {
        override fun prepare(): PreparedAnnotation =
            PreparedAnnotation(
                ERROR,
                E0666,
                RsBundle.message("inspection.message.nested.impl.trait.not.allowed")
            )
    }

    class MissingFieldsInTuplePattern(
        pat: RsPat,
        private val declaration: RsFieldsOwner,
        private val expectedAmount: Int,
        private val actualAmount: Int
    ) : RsDiagnostic(pat) {
        override fun prepare(): PreparedAnnotation {
            val itemType = if (declaration is RsEnumVariant) RsBundle.message("inspection.message.enum.variant") else RsBundle.message("inspection.message.tuple.struct")
            return PreparedAnnotation(
                ERROR,
                E0023,
                RsBundle.message("inspection.message.pattern.does.not.correspond.to.its.declaration.expected.found", itemType, expectedAmount, pluralize("field", expectedAmount), actualAmount),
                fixes = listOfFixes(AddStructFieldsPatFix(element), AddPatRestFix(element))
            )
        }
    }

    class MissingFieldsInStructPattern(
        pat: RsPat,
        private val declaration: RsFieldsOwner,
        private val missingFields: List<RsFieldDecl>
    ) : RsDiagnostic(pat) {
        override fun prepare(): PreparedAnnotation {
            val itemType = if (declaration is RsEnumVariant) RsBundle.message("inspection.message.enum.variant") else RsBundle.message("inspection.message.struct")
            val missingFieldNames = missingFields.joinToString(", ") { "`${it.name!!}`" }
            return PreparedAnnotation(
                ERROR,
                E0027,
                RsBundle.message("inspection.message.pattern.does.not.mention", itemType, pluralize("field", missingFields.size), missingFieldNames),
                fixes = listOfFixes(AddStructFieldsPatFix(element), AddPatRestFix(element))
            )
        }
    }

    class ExtraFieldInStructPattern(private val extraField: RsPatField, private val kindName: String) : RsDiagnostic(extraField) {
        override fun prepare(): PreparedAnnotation {
            return PreparedAnnotation(
                ERROR,
                E0026,
                RsBundle.message("inspection.message.extra.field.found.in.pattern", kindName, extraField.kind.fieldName)
            )
        }
    }

    class RepeatedFieldInStructPattern(
        repeatedField: RsPatField,
        private val name: String
    ) : RsDiagnostic(repeatedField) {
        override fun prepare(): PreparedAnnotation {
            return PreparedAnnotation(
                ERROR,
                E0025,
                RsBundle.message("inspection.message.field.bound.multiple.times.in.pattern", name)
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
                RsBundle.message("inspection.message.extra.fields.found.in.tuple.struct.pattern.expected.found", expectedAmount, extraFieldsAmount)
            )
        }
    }

    class MissingFieldsInUnionPattern(pat: RsPat) : RsDiagnostic(pat) {
        override fun prepare(): PreparedAnnotation {
            return PreparedAnnotation(
                ERROR,
                null,
                RsBundle.message("inspection.message.union.patterns.requires.field")
            )
        }
    }

    class TooManyFieldsInUnionPattern(pat: RsPat) : RsDiagnostic(pat) {
        override fun prepare(): PreparedAnnotation {
            return PreparedAnnotation(
                ERROR,
                null,
                RsBundle.message("inspection.message.union.patterns.should.have.exactly.one.field")
            )
        }
    }

    class MainFunctionNotFound(file: RsFile, private val crateName: String) : RsDiagnostic(file) {
        override fun prepare(): PreparedAnnotation {
            return PreparedAnnotation(
                ERROR,
                E0601,
                RsBundle.message("inspection.message.main.function.not.found.in.crate", crateName),
                fixes = listOfFixes(AddMainFnFix(element))
            )
        }
    }

    class AsyncMainFunction(element: PsiElement, private val entryPointName: String, private val fixes: List<LocalQuickFix>) : RsDiagnostic(element) {
        override fun prepare(): PreparedAnnotation {
            return PreparedAnnotation(
                ERROR,
                E0752,
                RsBundle.message("inspection.message.main.is.async", entryPointName),
                fixes = fixes.toQuickFixInfo()
            )
        }
    }

    class NonConstantValueInConstantError(element: PsiElement) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0435,
            RsBundle.message("inspection.message.non.constant.value.was.used.in.constant.expression")
        )
    }

    class LiteralOutOfRange(
        element: PsiElement,
        private val value: String,
        private val ty: String,
        private val fix: LocalQuickFix?
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            null,
            RsBundle.message("inspection.message.literal.out.of.range", value, ty),
            fixes = listOfFixes(fix)
        )
    }

    class NonConstantCallInConstantError(element: PsiElement) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0015,
            RsBundle.message("inspection.message.calls.in.constants.are.limited.to.constant.functions.tuple.structs.tuple.variants")
        )
    }

    class DeriveAttrUnsupportedItem(element: RsAttr) : RsDiagnostic(element) {
        override fun prepare(): PreparedAnnotation = PreparedAnnotation(
            ERROR,
            E0774,
            RsBundle.message("inspection.message.derive.may.only.be.applied.to.structs.enums.unions"),
            fixes = listOfFixes(RemoveAttrFix(element as RsAttr))
        )
    }

    class NoAttrParentheses(element: RsMetaItem, private val attrName: String) : RsDiagnostic(element) {
        override fun prepare(): PreparedAnnotation = PreparedAnnotation(
            ERROR,
            null,
            RsBundle.message("inspection.message.malformed.attribute.input.missing.parentheses", attrName),
            fixes = listOfFixes(AddAttrParenthesesFix(element as RsMetaItem, attrName))
        )
    }

    class ReprAttrUnsupportedItem(
        element: PsiElement,
        @InspectionMessage private val errorText: String
    ) : RsDiagnostic(element) {
        override fun prepare(): PreparedAnnotation = PreparedAnnotation(
            ERROR,
            E0517,
            errorText,
            fixes = listOfFixes(RemoveReprValueFix(element))
        )
    }

    class UnrecognizedReprAttribute(
        element: PsiElement,
        private val reprName: String
    ) : RsDiagnostic(element) {
        override fun prepare(): PreparedAnnotation = PreparedAnnotation(
            ERROR,
            E0552,
            RsBundle.message("inspection.message.unrecognized.representation", reprName),
            fixes = listOfFixes(RemoveReprValueFix(element))
        )
    }

    class InvalidReexport(
        element: PsiElement,
        private val name: String,
        private val exportedItem: RsItemElement
    ) : RsDiagnostic(element) {
        override fun prepare(): PreparedAnnotation = PreparedAnnotation(
            ERROR,
            if (exportedItem is RsMod) E0365 else E0364,
            RsBundle.message("inspection.message.private.cannot.be.re.exported", name),
            fixes = listOfFixes(MakePublicFix.createIfCompatible(exportedItem, exportedItem.name, false))
        )
    }

    class DefaultsConstGenericNotAllowed(expr: RsExpr) : RsDiagnostic(expr) {
        override fun prepare(): PreparedAnnotation = PreparedAnnotation(
            ERROR,
            null,
            RsBundle.message("inspection.message.defaults.for.const.parameters.are.only.allowed.in.struct.enum.type.or.trait.definitions"),
        )
    }

    class InherentImplDifferentCrateError(element: PsiElement) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0116,
            RsBundle.message("inspection.message.cannot.define.inherent.impl.for.type.outside.crate.where.type.defined")
        )
    }

    class TraitImplOrphanRulesError(element: PsiElement) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0117,
            RsBundle.message("inspection.message.only.traits.defined.in.current.crate.can.be.implemented.for.arbitrary.types")
        )
    }

    class CfgNotPatternIsMalformed(
        element: PsiElement,
        private val fixes: List<LocalQuickFix>
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0536,
            RsBundle.message("inspection.message.expected.cfg.pattern"),
            fixes = fixes.toQuickFixInfo()
        )
    }

    class UnknownCfgPredicate(
        element: PsiElement,
        private val name: String,
        private val fixes: List<LocalQuickFix> = emptyList()
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0537,
            RsBundle.message("inspection.message.invalid.predicate", name),
            fixes = fixes.toQuickFixInfo()
        )
    }

    class InvalidAbi(
        element: RsLitExpr,
        private val abiName: String
    ) : RsDiagnostic(element) {
        override fun prepare(): PreparedAnnotation = PreparedAnnotation(
            ERROR,
            E0703,
            RsBundle.message("inspection.message.invalid.abi.found", abiName),
            description = RsBundle.message("tooltip.valid.abis", SUPPORTED_CALLING_CONVENTIONS.keys.joinToString(", ")),
            fixes = createSuggestionFixes().toQuickFixInfo()
        )

        private fun createSuggestionFixes(): List<NameSuggestionFix<PsiElement>> {
            val factory = RsPsiFactory(element.project)
            return NameSuggestionFix.createApplicable(element, abiName, SUPPORTED_CALLING_CONVENTIONS.keys.toList(), 2) {
                factory.createExpression("\"$it\"")
            }
        }
    }

    class FeatureAttributeInNonNightlyChannel(
        element: PsiElement,
        private val channelName: String,
        private val quickFix: RemoveElementFix?
    ) : RsDiagnostic(element) {
        override fun prepare(): PreparedAnnotation = PreparedAnnotation(
            ERROR,
            E0554,
            RsBundle.message("inspection.message.feature.may.not.be.used.on.release.channel", channelName),
            fixes = listOfFixes(quickFix)
        )
    }

    class FeatureAttributeHasBeenRemoved(
        element: PsiElement,
        private val featureName: String,
        private val quickFix: RemoveElementFix?
    ) : RsDiagnostic(element) {
        override fun prepare(): PreparedAnnotation = PreparedAnnotation(
            ERROR,
            E0557,
            RsBundle.message("inspection.message.feature.has.been.removed", featureName),
            fixes = listOfFixes(quickFix)
        )
    }

    class InvalidConstGenericArgument(expr: RsExpr) : RsDiagnostic(expr) {
        override fun prepare(): PreparedAnnotation = PreparedAnnotation(
            ERROR,
            null,
            RsBundle.message("inspection.message.expressions.must.be.enclosed.in.braces.to.be.used.as.const.generic.arguments"),
            fixes = listOfFixes(EncloseExprInBracesFix(element as RsExpr))
        )
    }

    class IllegalLifetimeName(element: PsiElement) : RsDiagnostic(element) {
        override fun prepare(): PreparedAnnotation = PreparedAnnotation(
            ERROR,
            null,
            RsBundle.message("inspection.message.lifetimes.cannot.use.keyword.names")
        )
    }

    class InvalidLabelName(element: PsiElement, private val labelName: String) : RsDiagnostic(element) {
        override fun prepare(): PreparedAnnotation = PreparedAnnotation(
            ERROR,
            null,
            RsBundle.message("inspection.message.invalid.label.name", labelName)
        )
    }

    class SelfImportNotInUseGroup(element: PsiElement) : RsDiagnostic(element) {
        override fun prepare(): PreparedAnnotation = PreparedAnnotation(
            ERROR,
            E0429,
            RsBundle.message("inspection.message.self.imports.are.only.allowed.within.list"),
        )
    }

    class DuplicateSelfInUseGroup(element: PsiElement) : RsDiagnostic(element) {
        override fun prepare(): PreparedAnnotation = PreparedAnnotation(
            ERROR,
            E0430,
            RsBundle.message("inspection.message.self.import.appears.more.than.once.in.list"),
        )
    }

    class SelfImportInUseGroupWithEmptyPrefix(element: PsiElement) : RsDiagnostic(element) {
        override fun prepare(): PreparedAnnotation = PreparedAnnotation(
            ERROR,
            E0431,
            RsBundle.message("inspection.message.self.import.can.only.appear.in.import.list.with.non.empty.prefix"),
        )
    }

    class AwaitOutsideAsyncContext(element: PsiElement, private val fix: LocalQuickFix?) : RsDiagnostic(element) {
        override fun prepare(): PreparedAnnotation = PreparedAnnotation(
            ERROR,
            E0728,
            RsBundle.message("inspection.message.await.only.allowed.inside.async.functions.blocks"),
            fixes = listOfFixes(fix)
        )
    }

    class CannotCaptureDynamicEnvironment(
        element: PsiElement,
        private val fix: LocalQuickFix?,
    ) : RsDiagnostic(element) {
        override fun prepare(): PreparedAnnotation = PreparedAnnotation(
            ERROR,
            E0434,
            RsBundle.message("inspection.message.can.t.capture.dynamic.environment.in.fn.item"),
            fixes = listOfFixes(fix)
        )
    }

    class RecursiveAsyncFunction(element: PsiElement, private val fix: LocalQuickFix?) : RsDiagnostic(element) {
        override fun prepare(): PreparedAnnotation = PreparedAnnotation(
            ERROR,
            E0733,
            RsBundle.message("inspection.message.recursion.in.async.fn.requires.boxing"),
            fixes = listOfFixes(fix)
        )
    }

    class UnknownAssocTypeBinding(
        element: RsAssocTypeBinding,
        private val name: String,
        private val trait: String
    ) : RsDiagnostic(element) {
        override fun prepare(): PreparedAnnotation = PreparedAnnotation(
            ERROR,
            E0220,
            RsBundle.message("inspection.message.associated.type.not.found.for", name, trait),
            fixes = listOfFixes(RemoveAssocTypeBindingFix(element))
        )
    }

    class MissingAssocTypeBindings(
        element: RsElement,
        private val missingTypes: List<RsWrongAssocTypeArgumentsInspection.MissingAssocTypeBinding>
    ) : RsDiagnostic(element) {
        @InspectionMessage
        private fun getText(): String {
            val typeText = pluralize(RsBundle.message("inspection.message.type"), missingTypes.size)
            val missing = missingTypes.joinToString(", ") { RsBundle.message("inspection.message.from.trait", it.name, it.trait) }
            return RsBundle.message("inspection.message.value.associated.must.be.specified", typeText, missing)
        }

        override fun prepare(): PreparedAnnotation = PreparedAnnotation(
            ERROR,
            E0191,
            getText(),
            fixes = listOfFixes(AddAssocTypeBindingsFix(element as RsElement, missingTypes.map { it.name }))
        )
    }

    class ConstOrTypeParamsInExternError(
        element: PsiElement,
        private val kinds: String
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0044,
            RsBundle.message("inspection.message.foreign.items.may.not.have.parameters", kinds),
        )
    }

    class PatternArgumentInForeignFunctionError(
        element: PsiElement
    ) : RsDiagnostic(element) {
        override fun prepare(): PreparedAnnotation = PreparedAnnotation(
            ERROR,
            E0130,
            RsBundle.message("inspection.message.patterns.aren.t.allowed.in.foreign.function.declarations"),
        )
    }

    class PatternArgumentInFunctionWithoutBodyError(
        element: PsiElement
    ) : RsDiagnostic(element) {
        override fun prepare(): PreparedAnnotation = PreparedAnnotation(
            ERROR,
            E0642,
            RsBundle.message("inspection.message.patterns.aren.t.allowed.in.functions.without.bodies"),
        )
    }


    class PatternArgumentInFunctionPointerTypeError(
        element: PsiElement
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0561,
            RsBundle.message("inspection.message.patterns.aren.t.allowed.in.function.pointer.types")
        )
    }

    class UnsafeInherentImplError(
        element: PsiElement,
        private val fixes: List<LocalQuickFix>
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0197,
            RsBundle.message("inspection.message.inherent.impls.cannot.be.unsafe"),
            fixes = fixes.toQuickFixInfo()
        )
    }

    class MainWithGenericsError(
        element: PsiElement,
        private val fixes: List<LocalQuickFix>
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0131,
            RsBundle.message("inspection.message.main.function.not.allowed.to.have.generic.parameters"),
            fixes = fixes.toQuickFixInfo()
        )
    }

    class MultipleRelaxedBoundsError(
        element: PsiElement
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0203,
            RsBundle.message("inspection.message.type.parameter.has.more.than.one.relaxed.default.bound.only.one.supported")
        )
    }

    class BreakExprInNonLoopError(
        element: PsiElement,
        private val kind: String
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0571,
            RsBundle.message("inspection.message.break.with.value.from.loop", kind)
        )
    }

    class BreakContinueInWhileConditionWithoutLoopError(
        element: RsLabelReferenceOwner,
        private val kind: String,
        private val fixes: List<LocalQuickFix>
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0590,
            RsBundle.message("inspection.message.with.no.label.in.condition.while.loop", kind),
            fixes = fixes.toQuickFixInfo()
        )
    }

    class ContinueLabelTargetBlock(
        element: RsContExpr,
        private val fixes: List<RsConvertBlockToLoopFix>
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0696,
            RsBundle.message("inspection.message.continue.pointing.to.labeled.block"),
            fixes = fixes.toQuickFixInfo()
        )
    }


    class AtLeastOneTraitForObjectTypeError(
        element: PsiElement
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0224,
            RsBundle.message("inspection.message.at.least.one.trait.required.for.object.type")
        )
    }

    class ImplCopyForWrongTypeError(
        element: PsiElement
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0206,
            RsBundle.message("inspection.message.trait.copy.may.not.be.implemented.for.this.type")
        )
    }

    class LiteralValueInsideDeriveError(
        element: PsiElement,
        private val fixes: List<LocalQuickFix>
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0777,
            RsBundle.message("inspection.message.expected.path.to.trait.found.literal"),
            fixes = fixes.toQuickFixInfo()
        )
    }

    class CannotImplForDynAutoTraitError(
        element: PsiElement
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0785,
            RsBundle.message("inspection.message.cannot.define.inherent.impl.for.dyn.auto.trait")
        )
    }

    class UnionExprWithWrongFieldCount(
        element: PsiElement,
        private val fixes: List<LocalQuickFix>
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0784,
            RsBundle.message("inspection.message.union.expressions.should.have.exactly.one.field"),
            fixes = fixes.toQuickFixInfo(),
        )
    }

    class TooManyLifetimeBoundsOnTraitObjectError(
        element: PsiElement,
        private val fixes: List<LocalQuickFix>
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0226,
            RsBundle.message("inspection.message.only.single.explicit.lifetime.bound.permitted"),
            fixes = fixes.toQuickFixInfo(),
        )
    }

    class ReservedIdentifierIsUsed(
        element: PsiElement,
        private val fixes: List<LocalQuickFix>
    ) : RsDiagnostic(element) {
        override fun prepare(): PreparedAnnotation = PreparedAnnotation(
            ERROR,
            null,
            RsBundle.message("inspection.message.reserved.keyword", element.text),
            fixes = fixes.toQuickFixInfo(),
        )
    }

    class NestedQuantificationOfLifetimeBoundsError(
        element: RsPolybound,
        private val fixes: List<LocalQuickFix>
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0316,
            RsBundle.message("inspection.message.nested.quantification.lifetimes"),
            fixes = fixes.toQuickFixInfo(),
        )
    }

    class ManualImplementationOfFnTraitError(
        element: PsiElement,
        private val fnType: String
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0183,
            RsBundle.message("inspection.message.manual.implementations.are.experimental", fnType),
            fixes = listOfFixes(UNBOXED_CLOSURES.addFeatureFix(element))
        )
    }

    class OnlyAutoTraitsInTraitObjectError(
        element: PsiElement
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0225,
            RsBundle.message("inspection.message.only.auto.traits.can.be.used.as.additional.traits.in.trait.object")
        )
    }


    class WrongMetaDelimiters(
        beginElement: PsiElement,
        endElement: PsiElement,
        private val fix: LocalQuickFix
    ) : RsDiagnostic(beginElement, endElement) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            null,
            RsBundle.message("inspection.message.wrong.meta.list.delimiters"),
            description = RsBundle.message("tooltip.delimiters.should.be"),
            fixes = listOfFixes(fix),
        )
    }

    class MalformedAttributeInput(
        element: PsiElement,
        private val name: String,
        @Tooltip private val suggestions: String
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            null,
            RsBundle.message("inspection.message.malformed.attribute.input", name),
            description = suggestions,
        )
    }

    class AttributeSuffixedLiteral(
        element: PsiElement,
        private val fix: LocalQuickFix
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            null,
            RsBundle.message("inspection.message.suffixed.literals.are.not.allowed.in.attributes"),
            description = RsBundle.message("tooltip.instead.using.suffixed.literal.1u8.0f32.etc.use.unsuffixed.version.etc"),
            fixes = listOfFixes(fix),
        )
    }

    class UnusedAttribute(
        element: PsiElement,
        private val fix: LocalQuickFix,
        private val isFutureWarning: Boolean = false
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            WARN,
            null,
            RsBundle.message("inspection.message.unused.attribute"),
            description = if (isFutureWarning) RsBundle.message("tooltip.this.was.previously.accepted.by.compiler.but.being.phased.out.it.will.become.hard.error.in.future.release") else "",
            fixes = listOfFixes(fix)
        )
    }

    class MultipleAttributes(
        element: PsiElement,
        private val name: String,
        private val fix: LocalQuickFix
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            null,
            RsBundle.message("inspection.message.multiple.attributes", name),
            fixes = listOfFixes(fix),
        )
    }

    class VariadicParametersUsedOnNonCABIError(
        element: PsiElement
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0045,
            RsBundle.message("inspection.message.c.variadic.function.must.have.compatible.calling.convention.like.c.or.cdecl")
        )
    }

    class MultipleItemsInDeprecatedAttr(
        element: PsiElement,
        private val itemName: String,
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0538,
            RsBundle.message("inspection.message.multiple.items", itemName),
        )
    }

    class UnknownItemInDeprecatedAttr(
        element: PsiElement,
        private val itemName: String
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0541,
            RsBundle.message("inspection.message.unknown.meta.item", itemName),
        )
    }

    class IncorrectItemInDeprecatedAttr(
        element: PsiElement
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0551,
            RsBundle.message("inspection.message.incorrect.meta.item"),
        )
    }

    class UseOfMovedValueError(
        element: PsiElement,
        private val fix: LocalQuickFix?,
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0382,
            RsBundle.message("inspection.message.use.moved.value"),
            fixes = listOfFixes(fix)
        )
    }

    class UseOfUninitializedVariableError(
        element: PsiElement,
        private val fix: LocalQuickFix?,
    ) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0381,
            RsBundle.message("inspection.message.use.possibly.uninitialized.variable"),
            fixes = listOfFixes(fix)
        )
    }

    class MoveOutWhileBorrowedError(element: PsiElement) : RsDiagnostic(element) {
        override fun prepare() = PreparedAnnotation(
            ERROR,
            E0505,
            RsBundle.message("inspection.message.value.was.moved.out.while.it.was.still.borrowed"),
        )
    }

    class TraitObjectWithNoDyn(
        element: PsiElement,
        private val fix: LocalQuickFix
    ) : RsDiagnostic(element) {
        private val severity = if (element.isAtLeastEdition2021) ERROR else WARN
        override fun prepare() = PreparedAnnotation(
            severity,
            E0782,
            RsBundle.message("inspection.message.trait.objects.must.include.dyn.keyword"),
            description = if (!element.isAtLeastEdition2021) RsBundle.message("tooltip.this.accepted.in.current.edition.rust.but.hard.error.in.rust") else "",
            fixes = listOfFixes(fix)
        )
    }
}

enum class RsErrorCode {
    E0004, E0013, E0015, E0023, E0025, E0026, E0027, E0040, E0044, E0045, E0046, E0049, E0050, E0054, E0057, E0060, E0061, E0069, E0081, E0084,
    E0106, E0107, E0116, E0117, E0118, E0120, E0121, E0124, E0130, E0131, E0132, E0133, E0183, E0184, E0185, E0186, E0191, E0197, E0198,
    E0199, E0200, E0201, E0203, E0206, E0220, E0224, E0225, E0226, E0252, E0254, E0255, E0259, E0260, E0261, E0262, E0267, E0268, E0277,
    E0308, E0316, E0322, E0323, E0324, E0325, E0328, E0364, E0365, E0379, E0381, E0382, E0384,
    E0403, E0404, E0407, E0415, E0416, E0424, E0426, E0428, E0429, E0430, E0431, E0433, E0434, E0435, E0437, E0438, E0449, E0451, E0463,
    E0505, E0517, E0518, E0536, E0537, E0538, E0541, E0551, E0552, E0554, E0557, E0561, E0562, E0569, E0571, E0583, E0586, E0589, E0590, E0594,
    E0601, E0603, E0614, E0616, E0618, E0624, E0642, E0658, E0666, E0667, E0693, E0695, E0696,
    E0703, E0704, E0708, E0728, E0732, E0733, E0741, E0742, E0747, E0752, E0767, E0774, E0777, E0782, E0784, E0785;

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
    val errorCode: RsErrorCode?,
    @Suppress("UnstableApiUsage") @InspectionMessage val header: String,
    @Suppress("UnstableApiUsage") @Tooltip val description: String = "",
    val fixes: List<QuickFixWithRange> = emptyList(),
    val textAttributes: TextAttributesKey? = null
)

data class QuickFixWithRange(
    val fix: LocalQuickFix,
    val availabilityRange: TextRange?,
)

private fun listOfFixes(vararg fixes: LocalQuickFix?): List<QuickFixWithRange> =
    fixes.mapNotNull { if (it == null) null else QuickFixWithRange(it, null) }

private fun List<LocalQuickFix>.toQuickFixInfo(): List<QuickFixWithRange> = map { QuickFixWithRange(it, null) }

fun RsDiagnostic.addToHolder(holder: RsAnnotationHolder, checkExistsAfterExpansion: Boolean = true) {
    if (!checkExistsAfterExpansion || element.existsAfterExpansion) {
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

    val message = simpleHeader(prepared.errorCode, prepared.header)

    val annotationBuilder = holder.newAnnotation(prepared.severity.toHighlightSeverity(), message)
        .tooltip(prepared.fullDescription)
        .range(textRange)
        .highlightType(prepared.severity.toProblemHighlightType())

    if (prepared.textAttributes != null) {
        annotationBuilder.textAttributes(prepared.textAttributes)
    }

    for ((fix, range) in prepared.fixes) {
        if (fix is IntentionAction) {
            val fixBuilder = annotationBuilder.newFix(fix)
            if (range != null) {
                fixBuilder.range(range)
            }
            fixBuilder.registerFix()
        } else {
            val descriptor = InspectionManager.getInstance(element.project)
                .createProblemDescriptor(
                    element,
                    endElement ?: element,
                    message,
                    prepared.severity.toProblemHighlightType(),
                    true,
                    fix
                )

            annotationBuilder.newLocalQuickFix(fix, descriptor).registerFix()
        }
    }

    annotationBuilder.create()
}

fun RsDiagnostic.addToHolder(holder: RsProblemsHolder) {
    val prepared = prepare()
    holder.registerProblem(
        element,
        endElement ?: element,
        prepared.fullDescription,
        prepared.severity.toProblemHighlightType(),
        *prepared.fixes.map { it.fix }.toTypedArray()
    )
}

private val PreparedAnnotation.fullDescription: String
    get() = "<html>${htmlHeader(errorCode, escapeString(header))}<br>${escapeString(description)}</html>"

private fun Severity.toProblemHighlightType(): ProblemHighlightType = when (this) {
    INFO -> ProblemHighlightType.INFORMATION
    WARN -> ProblemHighlightType.WARNING
    ERROR -> ProblemHighlightType.GENERIC_ERROR
    UNKNOWN_SYMBOL -> ProblemHighlightType.LIKE_UNKNOWN_SYMBOL
}

private fun Severity.toHighlightSeverity(): HighlightSeverity = when (this) {
    INFO -> HighlightSeverity.INFORMATION
    WARN -> HighlightSeverity.WARNING
    ERROR, UNKNOWN_SYMBOL -> HighlightSeverity.ERROR
}

@InspectionMessage
private fun simpleHeader(error: RsErrorCode?, @InspectionMessage description: String): String =
    if (error == null) {
        description
    } else {
        RsBundle.message("inspection.message.", description, error.code)
    }

private fun htmlHeader(error: RsErrorCode?, description: String): String =
    if (error == null) {
        description
    } else {
        "$description [<a href='${error.infoUrl}'>${error.code}</a>]"
    }

private val RsSelfParameter.canonicalDecl: String
    get() = buildString {
        if (isRef) append('&')
        if (mutability.isMut) append("mut ")
        append("self")
    }

private fun Ty.rendered(useQualifiedName: Set<RsQualifiedNamedElement> = emptySet()): String =
    render(useQualifiedName = useQualifiedName)

private fun getConflictingNames(element: PsiElement, vararg tys: Ty): Set<RsQualifiedNamedElement> {
    val context = element.ancestorOrSelf<RsElement>()
    return if (context != null) {
        getTypeReferencesInfoFromTys(context, *tys).toQualify
    } else {
        emptySet()
    }
}

val SUPPORTED_CALLING_CONVENTIONS = mapOf(
    "Rust" to null,
    "C" to null,
    "C-unwind" to C_UNWIND,
    "cdecl" to null,
    "stdcall" to null,
    "stdcall-unwind" to C_UNWIND,
    "fastcall" to null,
    "vectorcall" to ABI_VECTORCALL,
    "thiscall" to ABI_THISCALL,
    "thiscall-unwind" to C_UNWIND,
    "aapcs" to null,
    "win64" to null,
    "sysv64" to null,
    "ptx-kernel" to ABI_PTX,
    "msp430-interrupt" to ABI_MSP430_INTERRUPT,
    "x86-interrupt" to ABI_X86_INTERRUPT,
    "amdgpu-kernel" to ABI_AMDGPU_KERNEL,
    "efiapi" to ABI_EFIAPI,
    "avr-interrupt" to ABI_AVR_INTERRUPT,
    "avr-non-blocking-interrupt" to ABI_AVR_INTERRUPT,
    "C-cmse-nonsecure-call" to ABI_C_CMSE_NONSECURE_CALL,
    "wasm" to WASM_ABI,
    "system" to null,
    "system-unwind" to C_UNWIND,
    "rust-intrinsic" to INTRINSICS,
    "rust-call" to UNBOXED_CLOSURES,
    "platform-intrinsic" to PLATFORM_INTRINSICS,
    "unadjusted" to ABI_UNADJUSTED
)

fun RsElement.areUnstableFeaturesAvailable(version: RustcVersion): ThreeState {
    val crate = containingCrate

    val origin = crate.origin
    val isStdlibPart = origin == PackageOrigin.STDLIB || origin == PackageOrigin.STDLIB_DEPENDENCY
    return if (version.channel != RustChannel.NIGHTLY && !isStdlibPart) ThreeState.NO else ThreeState.YES
}
