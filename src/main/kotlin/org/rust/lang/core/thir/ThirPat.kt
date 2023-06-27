/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.thir

import org.rust.ide.utils.checkMatch.CheckMatchException
import org.rust.lang.core.mir.asSpan
import org.rust.lang.core.mir.schemas.MirSpan
import org.rust.lang.core.mir.wrapper
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.infer.typeOfValue
import org.rust.lang.core.types.ty.Mutability
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyAdt
import org.rust.lang.core.types.ty.TyTuple
import org.rust.lang.core.types.type

// https://github.com/rust-lang/rust/blob/f7b831ac8a897273f78b9f47165cf8e54066ce4b/compiler/rustc_middle/src/thir.rs#L585
/** See also [org.rust.ide.utils.checkMatch.PatternKind] */
sealed class ThirPat(
    val ty: Ty,
    val source: MirSpan,
) {
    class Wild(ty: Ty, source: MirSpan) : ThirPat(ty, source)
    class AscribeUserType(ty: Ty, source: MirSpan) : ThirPat(ty, source)

    class Binding(
        val mutability: Mutability,
        val name: String,
        val mode: ThirBindingMode,
        val variable: LocalVar,
        val varTy: Ty,
        val subpattern: ThirPat?,
        val isPrimary: Boolean,
        ty: Ty,
        source: MirSpan,
    ) : ThirPat(ty, source)

    class Variant(
        val item: RsEnumItem,
        val variantIndex: MirVariantIndex,
        val subpatterns: List<ThirFieldPat>,
        ty: Ty,
        source: MirSpan
    ) : ThirPat(ty, source)

    /** `(...)`, `Foo(...)`, `Foo{...}`, or `Foo`, where `Foo` is a variant name from an ADT with a single variant. */
    class Leaf(val subpatterns: List<ThirFieldPat>, ty: Ty, source: MirSpan) : ThirPat(ty, source)
    class Deref(val subpattern: ThirPat, ty: Ty, source: MirSpan) : ThirPat(ty, source)
    class Const(ty: Ty, source: MirSpan) : ThirPat(ty, source)
    class Range(ty: Ty, source: MirSpan) : ThirPat(ty, source)
    class Slice(ty: Ty, source: MirSpan) : ThirPat(ty, source)
    class Array(ty: Ty, source: MirSpan) : ThirPat(ty, source)
    class Or(ty: Ty, source: MirSpan) : ThirPat(ty, source)

    companion object {
        // TODO: adjustments
        // https://github.com/rust-lang/rust/blob/f7b831ac8a897273f78b9f47165cf8e54066ce4b/compiler/rustc_mir_build/src/thir/pattern/mod.rs#L211
        /** See also [org.rust.ide.utils.checkMatch.CheckMatchUtilsKt.getKind] */
        fun from(pattern: RsPat): ThirPat {
            val ty = pattern.type
            val span = pattern.asSpan
            return when (pattern) {
                is RsPatWild -> Wild(ty, span)

                is RsPatIdent -> {
                    if (pattern.pat != null) TODO("Support `x @ pat`")
                    when (val resolved = pattern.patBinding.reference.resolve()) {
                        is RsEnumVariant -> {
                            if (ty !is TyAdt) error("Expected TyAdt for RsPatIdent resolved to RsEnumVariant")
                            lowerVariantOrLeaf(resolved, span, ty, subpatterns = emptyList())
                        }
                        is RsConstant -> TODO()
                        else -> lowerPatIdent(pattern.patBinding, ty, span)
                    }
                }

                is RsPatConst -> {
                    if (ty is TyAdt) {
                        val item = ty.item as? RsEnumItem
                            ?: error("Unresolved constant")
                        val path = (pattern.expr as RsPathExpr).path
                        val variant = path.reference?.resolve() as? RsEnumVariant
                            ?: error("Can't resolve ${path.text}")
                        val variantIndex = item.indexOfVariant(variant)
                            ?: error("Can't find enum variant")
                        Variant(item, variantIndex, subpatterns = emptyList(), ty, span)
                    } else {
                        TODO()
                    }
                }

                is RsPatRange -> TODO()

                is RsPatRef -> {
                    Deref(from(pattern.pat), ty, span)
                }

                is RsPatSlice -> TODO()

                is RsPatTup -> {
                    if (ty !is TyTuple) error("Unexpected type for tuple pattern")
                    val subpatterns = lowerTupleSubpats(pattern.patList, ty.types.size)
                    Leaf(subpatterns, ty, span)
                }

                is RsPatTupleStruct -> {
                    if (ty !is TyAdt) error("Tuple struct pattern not applied to an ADT")
                    val variant = pattern.path.reference?.resolve() as? RsEnumVariant
                        ?: error("Unresolved variant")
                    val subpatterns = lowerTupleSubpats(pattern.patList, variant.positionalFields.size)
                    lowerVariantOrLeaf(variant, span, ty, subpatterns)
                }

                is RsPatStruct -> {
                    if (ty !is TyAdt) error("Struct pattern not applied to an ADT")
                    val item = pattern.path.reference?.resolve() as? RsFieldsOwner
                        ?: error("Unresolved path for pat struct")
                    val subpatterns = pattern.patFieldList.map { patField ->
                        val patFieldFull = patField.patFieldFull
                        val patBinding = patField.patBinding
                        val (pat, thirPat) = when {
                            patFieldFull != null -> {
                                patFieldFull to from(patFieldFull.pat)
                            }
                            patBinding != null -> {
                                patBinding to lowerPatIdent(patBinding, patField.type, patField.asSpan)
                            }
                            else -> error("Invalid RsPatField")
                        }
                        val field = pat.reference.resolve() as? RsFieldDecl ?: error("Unexpected resolve result")
                        val fieldIndex = field.owner!!.indexOfField(field) ?: error("Can't find field")
                        ThirFieldPat(fieldIndex, thirPat)
                    }
                    lowerVariantOrLeaf(item, span, ty, subpatterns)
                }

                is RsOrPat -> TODO()

                is RsPatMacro -> TODO()

                else -> TODO("Not implemented for type ${pattern::class}")
            }
        }

        private fun lowerPatIdent(binding: RsPatBinding, ty: Ty, span: MirSpan): Binding {
            val bindingMode = binding.bindingMode.wrapper
            val mode: ThirBindingMode
            val mutability: Mutability
            when (bindingMode.ref) {
                null -> {
                    mode = ThirBindingMode.ByValue
                    mutability = if (bindingMode.mut == null) Mutability.IMMUTABLE else Mutability.MUTABLE
                }

                else -> TODO()
            }

            return Binding(
                mutability = mutability,
                mode = mode,
                name = binding.name ?: error("Could not get name of pattern binding"),
                variable = LocalVar(binding), // TODO: this is wrong in case isPrimary = false
                varTy = ty,
                subpattern = null, // TODO
                ty = ty,
                source = span,
                isPrimary = true, // TODO: can this even be false? didn't find example, chat gpt says it can't
            )
        }

        private fun lowerTupleSubpats(pats: List<RsPat>, expectedLen: Int): List<ThirFieldPat> {
            if (pats.count { it is RsPatRest } > 1) {
                error("More then one .. in tuple pattern")
            }
            var hasPatRest = false
            return pats.mapIndexedNotNull { i, pat ->
                if (pat is RsPatRest) {
                    hasPatRest = true
                    return@mapIndexedNotNull null
                }
                val index = i + (if (hasPatRest) expectedLen - pats.size else 0)
                val thirPat = from(pat)
                ThirFieldPat(index, thirPat)
            }
        }

        private fun lowerVariantOrLeaf(
            item: RsElement,
            span: MirSpan,
            ty: TyAdt,
            subpatterns: List<ThirFieldPat>
        ): ThirPat {
            return when (item) {
                is RsEnumVariant -> {
                    val enum = item.parentEnum
                    val variantIndex = enum.indexOfVariant(item) ?: error("Can't find enum variant")
                    Variant(enum, variantIndex, subpatterns, ty, span)
                }
                is RsStructItem -> Leaf(subpatterns, ty, span)
                else -> throw CheckMatchException("Impossible case $item")
            }
        }

        fun from(self: RsSelfParameter): ThirPat {
            return Binding(
                mutability = Mutability.valueOf(self.mut != null && self.and == null),
                mode = ThirBindingMode.ByValue,
                name = "self",
                variable = LocalVar(self),
                varTy = self.typeOfValue,
                subpattern = null,
                ty = self.typeOfValue,
                source = self.asSpan,
                isPrimary = true,
            )
        }
    }
}

class ThirFieldPat(val field: MirFieldIndex, val pattern: ThirPat)

val ThirPat.simpleIdent: String? get() = when {
    this is ThirPat.Binding && this.mode is ThirBindingMode.ByValue && this.subpattern == null -> this.name
    else -> null
}
