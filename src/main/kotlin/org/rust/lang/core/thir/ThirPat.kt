/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.thir

import org.rust.lang.core.mir.asSpan
import org.rust.lang.core.mir.schemas.MirSpan
import org.rust.lang.core.mir.wrapper
import org.rust.lang.core.psi.RsPat
import org.rust.lang.core.psi.RsPatIdent
import org.rust.lang.core.psi.RsSelfParameter
import org.rust.lang.core.types.infer.typeOfValue
import org.rust.lang.core.types.ty.Mutability
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.type

/** See also [org.rust.ide.utils.checkMatch.PatternKind] */
sealed class ThirPat(
    val ty: Ty,
    val source: MirSpan,
) {
    class Wild(ty: Ty, source: MirSpan) : ThirPat(ty, source)

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

    class Variant(ty: Ty, source: MirSpan) : ThirPat(ty, source)
    class Leaf(ty: Ty, source: MirSpan) : ThirPat(ty, source)
    class Deref(ty: Ty, source: MirSpan) : ThirPat(ty, source)
    class Const(ty: Ty, source: MirSpan) : ThirPat(ty, source)
    class Range(ty: Ty, source: MirSpan) : ThirPat(ty, source)
    class Slice(ty: Ty, source: MirSpan) : ThirPat(ty, source)
    class Array(ty: Ty, source: MirSpan) : ThirPat(ty, source)
    class Or(ty: Ty, source: MirSpan) : ThirPat(ty, source)

    companion object {
        // TODO: adjustments
        fun from(pattern: RsPat): ThirPat {
            return when (pattern) {
                is RsPatIdent -> {
                    val bindingMode = pattern.patBinding.bindingMode.wrapper
                    val mode: ThirBindingMode
                    val mutability: Mutability
                    when (bindingMode.ref) {
                        null -> {
                            mode = ThirBindingMode.ByValue
                            mutability = if (bindingMode.mut == null) Mutability.IMMUTABLE else Mutability.MUTABLE
                        }
                        else -> TODO()
                    }

                    Binding(
                        mutability = mutability,
                        mode = mode,
                        name = pattern.patBinding.name ?: error("Could not get name of pattern binding"),
                        variable = LocalVar(pattern.patBinding), // TODO: this is wrong in case isPrimary = false
                        varTy = pattern.type,
                        subpattern = null, // TODO
                        ty = pattern.type,
                        source = pattern.asSpan,
                        isPrimary = true, // TODO: can this even be false? didn't find example, chat gpt says it can't
                    )
                }
                else -> TODO("Not implemented for type ${pattern::class}")
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

val ThirPat.simpleIdent: String? get() = when {
    this is ThirPat.Binding && this.mode is ThirBindingMode.ByValue && this.subpattern == null -> this.name
    else -> null
}
