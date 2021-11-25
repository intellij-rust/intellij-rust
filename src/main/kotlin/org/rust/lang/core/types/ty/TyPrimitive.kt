/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.ty

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsBaseType
import org.rust.lang.core.psi.RsPath
import org.rust.lang.core.psi.RsTypeAlias
import org.rust.lang.core.psi.ext.RsMod
import org.rust.lang.core.types.BoundElement
import org.rust.lang.core.types.infer.TypeFolder

/**
 * These are "atomic" ty.
 *
 * Definition intentionally differs from the reference: we don't treat
 * tuples or arrays as primitive.
 */
sealed class TyPrimitive : Ty() {
    abstract val name: String

    override fun superFoldWith(folder: TypeFolder): Ty {
        val alias = aliasedBy
        return if (alias == null) {
            this
        } else {
            withAlias(alias.foldWith(folder))
        }
    }

    override fun isEquivalentToInner(other: Ty): Boolean {
        return javaClass == other.javaClass
    }

    companion object {
        fun fromPath(path: RsPath): TyPrimitive? {
            val name = path.referenceName ?: return null

            val result = fromName(name) ?: return null

            if (path.hasColonColon || path.typeQual != null) return null
            val parent = path.parent
            if (parent !is RsBaseType && parent !is RsPath) return null

            // struct u8;
            // let a: u8; // this is a struct "u8", not a primitive type "u8"
            val pathReference = path.reference ?: return null
            val resolvedTo = pathReference.multiResolve()
            if (parent is RsBaseType && resolvedTo.any { it !is RsMod }) return null
            if (parent is RsPath && resolvedTo.isNotEmpty()) return null

            return result
        }

        private fun fromName(name: String): TyPrimitive? {
            TyInteger.fromName(name)?.let { return it }
            TyFloat.fromName(name)?.let { return it }

            return when (name) {
                "bool" -> TyBool.INSTANCE
                "char" -> TyChar.INSTANCE
                "str" -> TyStr.INSTANCE
                else -> null
            }
        }
    }
}

class TyBool(override val aliasedBy: BoundElement<RsTypeAlias>? = null) : TyPrimitive() {
    override val name: String
        get() = "bool"

    override fun withAlias(aliasedBy: BoundElement<RsTypeAlias>): Ty = TyBool(aliasedBy)

    companion object {
        val INSTANCE: TyBool = TyBool()
    }
}

class TyChar(override val aliasedBy: BoundElement<RsTypeAlias>? = null) : TyPrimitive() {
    override val name: String
        get() = "char"

    override fun withAlias(aliasedBy: BoundElement<RsTypeAlias>): Ty = TyChar(aliasedBy)

    companion object {
        val INSTANCE: TyChar = TyChar()
    }
}

class TyUnit(override val aliasedBy: BoundElement<RsTypeAlias>? = null) : TyPrimitive() {
    override val name: String
        get() = "unit"

    override fun withAlias(aliasedBy: BoundElement<RsTypeAlias>): Ty = TyUnit(aliasedBy)

    companion object {
        val INSTANCE: TyUnit = TyUnit()
    }
}

/** The `!` type. E.g. `unimplemented!()` */
object TyNever : TyPrimitive() {
    override val name: String
        get() = "never"
}

class TyStr(override val aliasedBy: BoundElement<RsTypeAlias>? = null) : TyPrimitive() {
    override val name: String
        get() = "str"

    override fun withAlias(aliasedBy: BoundElement<RsTypeAlias>): Ty = TyStr(aliasedBy)

    companion object {
        val INSTANCE: TyStr = TyStr()
    }
}

sealed class TyNumeric : TyPrimitive()

sealed class TyInteger : TyNumeric() {
    abstract val ordinal: Int

    // This fixes NPE caused by java classes initialization order. Details:
    // Kotlin `object`s compile into java classes with `INSTANCE` static field
    // and `companion object` fields compile into static field of the host class.
    // Our objects (`U8`, `U16` etc) are extend `TyInteger` class.
    // In java, parent classes are initialized first. So, if we accessing,
    // for example, `U8` object first, we really accessing `U8.INSTANCE` field,
    // that requests to initialize `U8` class, that requests to initialize
    // `TyInteger` before. Then, when we initializing `TyInteger`, `U8` is not
    // initialized and `U8.INSTANCE` is null. So if `VALUES` is a field of
    // `TyInteger` class, it will be filled with null value instead of `U8`
    // We fixing it by moving fields from `companion object` an independent object
    private object TyIntegerValuesHolder {
        val DEFAULT = I32.INSTANCE
        val VALUES = listOf(U8.INSTANCE, U16.INSTANCE, U32.INSTANCE, U64.INSTANCE, U128.INSTANCE, USize.INSTANCE, I8.INSTANCE, I16.INSTANCE, I32.INSTANCE, I64.INSTANCE, I128.INSTANCE, ISize.INSTANCE)
        val NAMES = VALUES.map { it.name }
    }

    companion object {
        val DEFAULT: TyInteger get() = TyIntegerValuesHolder.DEFAULT
        val VALUES: List<TyInteger> get() = TyIntegerValuesHolder.VALUES
        val NAMES: List<String> get() = TyIntegerValuesHolder.NAMES

        fun fromName(name: String): TyInteger? {
            return VALUES.find { it.name == name }
        }

        fun fromSuffixedLiteral(literal: PsiElement): TyInteger? {
            val text = literal.text
            return VALUES.find { text.endsWith(it.name) }
        }
    }

    class U8(override val aliasedBy: BoundElement<RsTypeAlias>? = null): TyInteger() {
        override val name: String
            get() = "u8"
        override val ordinal: Int
            get() = 0

        override fun withAlias(aliasedBy: BoundElement<RsTypeAlias>): Ty = U8(aliasedBy)

        companion object {
            val INSTANCE: U8 = U8()
        }
    }
    class U16(override val aliasedBy: BoundElement<RsTypeAlias>? = null): TyInteger() {
        override val name: String
            get() = "u16"
        override val ordinal: Int
            get() = 1

        override fun withAlias(aliasedBy: BoundElement<RsTypeAlias>): Ty = U16(aliasedBy)

        companion object {
            val INSTANCE: U16 = U16()
        }
    }
    class U32(override val aliasedBy: BoundElement<RsTypeAlias>? = null): TyInteger() {
        override val name: String
            get() = "u32"
        override val ordinal: Int
            get() = 2

        override fun withAlias(aliasedBy: BoundElement<RsTypeAlias>): Ty = U32(aliasedBy)

        companion object {
            val INSTANCE: U32 = U32()
        }
    }
    class U64(override val aliasedBy: BoundElement<RsTypeAlias>? = null): TyInteger() {
        override val name: String
            get() = "u64"
        override val ordinal: Int
            get() = 3

        override fun withAlias(aliasedBy: BoundElement<RsTypeAlias>): Ty = U64(aliasedBy)

        companion object {
            val INSTANCE: U64 = U64()
        }
    }
    class U128(override val aliasedBy: BoundElement<RsTypeAlias>? = null): TyInteger() {
        override val name: String
            get() = "u128"
        override val ordinal: Int
            get() = 4

        override fun withAlias(aliasedBy: BoundElement<RsTypeAlias>): Ty = U128(aliasedBy)

        companion object {
            val INSTANCE: U128 = U128()
        }
    }
    class USize(override val aliasedBy: BoundElement<RsTypeAlias>? = null): TyInteger() {
        override val name: String
            get() = "usize"
        override val ordinal: Int
            get() = 5

        override fun withAlias(aliasedBy: BoundElement<RsTypeAlias>): Ty = USize(aliasedBy)

        companion object {
            val INSTANCE: USize = USize()
        }
    }

    class I8(override val aliasedBy: BoundElement<RsTypeAlias>? = null): TyInteger() {
        override val name: String
            get() = "i8"
        override val ordinal: Int
            get() = 6

        override fun withAlias(aliasedBy: BoundElement<RsTypeAlias>): Ty = I8(aliasedBy)

        companion object {
            val INSTANCE: I8 = I8()
        }
    }
    class I16(override val aliasedBy: BoundElement<RsTypeAlias>? = null): TyInteger() {
        override val name: String
            get() = "i16"
        override val ordinal: Int
            get() = 7

        override fun withAlias(aliasedBy: BoundElement<RsTypeAlias>): Ty = I16(aliasedBy)

        companion object {
            val INSTANCE: I16 = I16()
        }
    }
    class I32(override val aliasedBy: BoundElement<RsTypeAlias>? = null): TyInteger() {
        override val name: String
            get() = "i32"
        override val ordinal: Int
            get() = 8

        override fun withAlias(aliasedBy: BoundElement<RsTypeAlias>): Ty = I32(aliasedBy)

        companion object {
            val INSTANCE: I32 = I32()
        }
    }
    class I64(override val aliasedBy: BoundElement<RsTypeAlias>? = null): TyInteger() {
        override val name: String
            get() = "i64"
        override val ordinal: Int
            get() = 9

        override fun withAlias(aliasedBy: BoundElement<RsTypeAlias>): Ty = I64(aliasedBy)

        companion object {
            val INSTANCE: I64 = I64()
        }
    }
    class I128(override val aliasedBy: BoundElement<RsTypeAlias>? = null): TyInteger() {
        override val name: String
            get() = "i128"
        override val ordinal: Int
            get() = 10

        override fun withAlias(aliasedBy: BoundElement<RsTypeAlias>): Ty = I128(aliasedBy)

        companion object {
            val INSTANCE: I128 = I128()
        }
    }
    class ISize(override val aliasedBy: BoundElement<RsTypeAlias>? = null): TyInteger() {
        override val name: String
            get() = "isize"
        override val ordinal: Int
            get() = 11

        override fun withAlias(aliasedBy: BoundElement<RsTypeAlias>): Ty = ISize(aliasedBy)

        companion object {
            val INSTANCE: ISize = ISize()
        }
    }
}

sealed class TyFloat : TyNumeric() {
    abstract val ordinal: Int

    // See TyIntegerValuesHolder
    private object TyFloatValuesHolder {
        val DEFAULT = F64.INSTANCE
        val VALUES = listOf(F32.INSTANCE, F64.INSTANCE)
        val NAMES = VALUES.map { it.name }
    }

    companion object {
        val DEFAULT: TyFloat get() = TyFloatValuesHolder.DEFAULT
        val VALUES: List<TyFloat> get() = TyFloatValuesHolder.VALUES
        val NAMES: List<String> get() = TyFloatValuesHolder.NAMES

        fun fromName(name: String): TyFloat? {
            return VALUES.find { it.name == name }
        }

        fun fromSuffixedLiteral(literal: PsiElement): TyFloat? {
            val text = literal.text
            return VALUES.find { text.endsWith(it.name) }
        }
    }

    class F32(override val aliasedBy: BoundElement<RsTypeAlias>? = null): TyFloat() {
        override val name: String
            get() = "f32"
        override val ordinal: Int
            get() = 0

        override fun withAlias(aliasedBy: BoundElement<RsTypeAlias>): Ty = F32(aliasedBy)

        companion object {
            val INSTANCE: F32 = F32()
        }
    }
    class F64(override val aliasedBy: BoundElement<RsTypeAlias>? = null): TyFloat() {
        override val name: String
            get() = "f64"
        override val ordinal: Int
            get() = 1

        override fun withAlias(aliasedBy: BoundElement<RsTypeAlias>): Ty = F64(aliasedBy)

        companion object {
            val INSTANCE: F64 = F64()
        }
    }
}
