/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.building

import org.rust.lang.core.mir.MirBuilder
import org.rust.lang.core.mir.schemas.*
import org.rust.lang.core.mir.schemas.impls.MirBasicBlockImpl
import org.rust.lang.core.mir.schemas.impls.MirSwitchTargetsImpl
import org.rust.lang.core.psi.RsEnumItem
import org.rust.lang.core.psi.ext.variants
import org.rust.lang.core.thir.MirVariantIndex
import org.rust.lang.core.thir.ThirFieldPat
import org.rust.lang.core.thir.ThirPat
import org.rust.lang.core.thir.discriminants
import org.rust.lang.core.types.ty.*
import org.rust.openapiext.testAssert
import java.util.*

sealed class MirTest(val span: MirSpan) {

    /** Test what enum variant a value is. */
    class Switch(
        span: MirSpan,
        /** The enum type being tested. */
        val item: RsEnumItem,
        /** The set of variants that we should create a branch for. We also create an additional "otherwise" case. */
        val variants: BitSet,
    ) : MirTest(span)

    class SwitchInt(span: MirSpan) : MirTest(span)
    class Eq(span: MirSpan) : MirTest(span)
    class Range(span: MirSpan) : MirTest(span)
    class Len(span: MirSpan) : MirTest(span)

    fun targets(): Int {
        return when (this) {
            is Eq, is Range, is Len -> 2
            is Switch -> item.variants.size + 1
            is SwitchInt -> TODO()
        }
    }

    companion object {
        fun test(matchPair: MirMatchPair): MirTest {
            val span = matchPair.pattern.source
            return when (val pattern = matchPair.pattern) {
                is ThirPat.Variant -> Switch(
                    span,
                    pattern.item,
                    BitSet(pattern.item.variants.size)
                )
                is ThirPat.Const -> TODO()
                is ThirPat.Range -> TODO()
                is ThirPat.Slice -> TODO()
                is ThirPat.Or -> error("Or-patterns should have already been handled")
                is ThirPat.AscribeUserType,
                is ThirPat.Array,
                is ThirPat.Wild,
                is ThirPat.Binding,
                is ThirPat.Leaf,
                is ThirPat.Deref -> error("Simplifiable pattern found")
            }
        }
    }
}

fun addVariantsToSwitch(testPlace: PlaceBuilder, candidate: MirCandidate, variants: BitSet): Boolean {
    val matchPair = candidate.matchPairs.find { it.place == testPlace } ?: return false
    return when (val pattern = matchPair.pattern) {
        is ThirPat.Variant -> {
            variants.set(pattern.variantIndex)
            true
        }
        else -> false
    }
}

fun MirBuilder.performTest(
    matchStartSpan: MirSpan,
    scrutineeSpan: MirSpan,
    block: MirBasicBlockImpl,
    placeBuilder: PlaceBuilder,
    test: MirTest,
    makeTargetBlocks: () -> List<MirBasicBlockImpl>
) {
    val place = placeBuilder.toPlace()
    val placeTy = place.ty()

    val sourceInfo = sourceInfo(test.span)
    return when (test) {
        is MirTest.Switch -> {
            val targetBlocks = makeTargetBlocks()
            val numEnumVariants = test.item.variants.size
            testAssert { targetBlocks.size == numEnumVariants + 1 }
            val otherwiseBlock = targetBlocks.last()
            val switchTargets = MirSwitchTargetsImpl.new(
                test.item.discriminants().mapNotNull { (index, discriminant) ->
                    if (test.variants.get(index)) {
                        discriminant to targetBlocks[index]
                    } else {
                        null
                    }
                },
                otherwiseBlock
            )
            val discrTy = TyInteger.ISize.INSTANCE  // TODO
            val discr = temp(discrTy, test.span)
            block.pushAssign(discr, MirRvalue.Discriminant(place), sourceInfo(scrutineeSpan))
            block.terminateWithSwitchInt(MirOperand.Move(discr), switchTargets, sourceInfo(matchStartSpan))
        }
        is MirTest.SwitchInt -> TODO()
        is MirTest.Eq -> TODO()
        is MirTest.Range -> TODO()
        is MirTest.Len -> TODO()
    }
}

fun sortCandidate(testPlace: PlaceBuilder, test: MirTest, candidate: MirCandidate): Int? {
    val (matchPairIndex, matchPair) = candidate.matchPairs
        .withIndex()
        .find { (_, matchPair) -> matchPair.place == testPlace }
        ?: return null
    val pattern = matchPair.pattern
    return when {
        test is MirTest.Switch && pattern is ThirPat.Variant -> {
            testAssert { pattern.item == test.item }
            candidateAfterVariantSwitch(
                matchPairIndex,
                pattern.item,
                pattern.variantIndex,
                pattern.subpatterns,
                candidate
            )
            return pattern.variantIndex
        }
        test is MirTest.Switch -> null

        test is MirTest.SwitchInt && pattern is ThirPat.Const && pattern.ty.isSwitchType() -> {
            TODO()
        }
        test is MirTest.SwitchInt && pattern is ThirPat.Range -> {
            TODO()
        }
        test is MirTest.SwitchInt -> null

        test is MirTest.Len && pattern is ThirPat.Slice -> TODO()

        test is MirTest.Range && pattern is ThirPat.Range -> TODO()
        test is MirTest.Range && pattern is ThirPat.Const -> TODO()
        test is MirTest.Range -> null

        test is MirTest.Eq || test is MirTest.Len -> TODO()

        else -> error("unreachable")
    }
}

private fun candidateAfterVariantSwitch(
    matchPairIndex: Int,
    item: RsEnumItem,
    variantIndex: MirVariantIndex,
    subpatterns: List<ThirFieldPat>,
    candidate: MirCandidate
) {
    val matchPair = candidate.matchPairs.removeAt(matchPairIndex)

    val downcastPlace = matchPair.place.downcast(item, variantIndex)
    val consequentMatchPairs = subpatterns.map { subpattern ->
        // e.g., `(x as Variant).0`
        val place = downcastPlace.cloneProject(MirProjectionElem.Field(subpattern.field, subpattern.pattern.ty))
        // e.g., `(x as Variant).0 @ P1`
        MirMatchPair.new(place, subpattern.pattern)
    }
    candidate.matchPairs += consequentMatchPairs
}

private fun Ty.isSwitchType(): Boolean =
    isIntegral || this is TyChar || this is TyBool
