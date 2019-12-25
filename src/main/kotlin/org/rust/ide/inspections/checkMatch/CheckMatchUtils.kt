/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.checkMatch

import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.consts.CtValue
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyAdt
import org.rust.lang.core.types.ty.TyUnknown
import org.rust.lang.core.types.type
import org.rust.lang.utils.evaluation.ConstExpr.Value
import org.rust.lang.utils.evaluation.evaluate

class CheckMatchException(message: String) : Exception(message)

typealias Matrix = List<List<Pattern>>

/**
 * Returns the type of elements of the matrix
 *
 * @returns [TyUnknown] if there is more than one distinct known type
 */
val Matrix.type: Ty
    get() = asSequence().flatten()
        .map { it.ty }.filter { it !is TyUnknown }
        .distinct().singleOrNull()
        ?: TyUnknown

/**
 * Returns the type of the first column of the matrix
 *
 * @returns [TyUnknown] if there is more than one distinct known type in first column
 */
val Matrix.firstColumnType: Ty
    get() = mapNotNull { it.firstOrNull()?.ty }
        .filter { it !is TyUnknown }
        .distinct().singleOrNull()
        ?: TyUnknown

@Throws(CheckMatchException::class)
fun List<RsMatchArm>.calculateMatrix(): Matrix =
    flatMap { arm -> arm.patList.map { listOf(it.lower) } }

private val RsExpr.value: Value<*>?
    get() = (evaluate() as? CtValue)?.expr

// lower_pattern_unadjusted
private val RsPat.kind: PatternKind
    get() = when (this) {
        is RsPatIdent -> {
            if (pat != null) throw TODO("Support `x @ pat`")
            when (val resolved = patBinding.reference.resolve()) {
                is RsEnumVariant -> PatternKind.Variant(resolved.parentEnum, resolved, emptyList())
                is RsConstant -> {
                    val value = resolved.expr?.value
                        ?: throw CheckMatchException("Can't evaluate constant ${resolved.text}")
                    PatternKind.Const(value)
                }
                else -> PatternKind.Binding(patBinding.type, patBinding.name.orEmpty())
            }
        }

        is RsPatWild -> PatternKind.Wild

        is RsPatTup -> PatternKind.Leaf(patList.map { it.lower })

        is RsPatStruct -> {
            val item = path.reference.resolve() as? RsFieldsOwner
                ?: throw CheckMatchException("Can't resolve ${path.text}")

            val subPatterns = mutableListOf<Pattern>()
            val nameToPatField = patFieldList.associateBy { it.kind.fieldName }

            for (field in item.namedFields) {
                val patField = nameToPatField[field.name]
                val pattern = createPatternForField(patField, field)
                subPatterns.add(pattern)
            }

            for ((index, field) in item.positionalFields.withIndex()) {
                val patField = patFieldList.getOrNull(index)
                val pattern = createPatternForField(patField, field)
                subPatterns.add(pattern)
            }

            getLeafOrVariant(item, subPatterns)
        }

        is RsPatTupleStruct -> {
            val item = path.reference.resolve() ?: throw CheckMatchException("Can't resolve ${path.text}")
            val subPatterns = patList.map { it.lower }

            getLeafOrVariant(item, subPatterns)
        }

        is RsPatConst -> {
            val ty = expr.type
            if (ty is TyAdt) {
                if (ty.item is RsEnumItem) {
                    val variant = (expr as RsPathExpr).path.reference.resolve() as RsEnumVariant
                    PatternKind.Variant(ty.item, variant, emptyList())
                } else {
                    throw CheckMatchException("Unresolved constant")
                }
            } else {
                val value = expr.value ?: throw CheckMatchException("Can't evaluate constant ${expr.text}")
                PatternKind.Const(value)
            }
        }

        is RsPatRange -> {
            val lc = patConstList.getOrNull(0)?.expr?.value ?: throw CheckMatchException("Incomplete range")
            val rc = patConstList.getOrNull(1)?.expr?.value ?: throw CheckMatchException("Incomplete range")
            PatternKind.Range(lc, rc, isInclusive)
        }

        is RsPatRef -> PatternKind.Deref(pat.lower)
        is RsPatMacro -> TODO()
        is RsPatSlice -> TODO()
        else -> TODO()
    }

private fun createPatternForField(patField: RsPatField?, field: RsFieldDecl): Pattern =
    if (patField != null) {
        patField.patFieldFull?.pat?.let { return it.lower }
        val binding = patField.patBinding ?: throw CheckMatchException("Invalid RsPatField")
        Pattern(binding.type, PatternKind.Binding(binding.type, binding.name.orEmpty()))
    } else {
        val fieldType = field.typeReference?.type ?: throw CheckMatchException("Field type = null")
        Pattern(fieldType, PatternKind.Wild)
    }

// lower_variant_or_leaf
private fun getLeafOrVariant(item: RsElement, subPatterns: List<Pattern>): PatternKind =
    when (item) {
        is RsEnumVariant -> PatternKind.Variant(item.parentEnum, item, subPatterns)
        is RsStructItem -> PatternKind.Leaf(subPatterns)
        else -> throw CheckMatchException("Impossible case $item")
    }

private val RsPat.lower: Pattern
    get() = Pattern(type, kind)
