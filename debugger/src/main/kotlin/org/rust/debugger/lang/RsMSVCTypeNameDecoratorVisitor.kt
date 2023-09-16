/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.debugger.lang

import org.rust.debugger.lang.RsTypeNameParser.*

class RsMSVCTypeNameDecoratorVisitor : RsTypeNameBaseVisitor<Unit>() {
    private var nestedGenericsLevel: Int = 0

    private val builder: StringBuilder = StringBuilder()
    fun getDecoratedTypeName(): String = builder.toString()

    // str -> &str
    override fun visitMsvcStr(ctx: MsvcStrContext) {
        builder.append("&str")
    }

    // str$ -> str
    override fun visitMsvcStrDollar(ctx: MsvcStrDollarContext) {
        builder.append("str")
    }

    // never$ -> !
    override fun visitMsvcNever(ctx: MsvcNeverContext) {
        builder.append("!")
    }

    // tuple$<MyType1, MyType2, ...> -> (MyType1, MyType2, ...)
    override fun visitMsvcTuple(ctx: MsvcTupleContext) {
        builder.append("(")
        ctx.items?.accept(this)
        builder.append(")")
    }

    // ptr_const$<MyType> -> *const MyType
    // ptr_const$<str> -> *const str
    // ptr_const$<slice$<MyType> > -> *const [MyType]
    @Suppress("DuplicatedCode")
    override fun visitMsvcPtr_const(ctx: MsvcPtr_constContext) {
        builder.append("*const ")

        // BACKCOMPAT: Rust 1.66. Get rid of if-else, replace with `ctx.type.accept(this)`
        val msvcTypeName = ctx.type.msvcTypeName()
        if (msvcTypeName?.msvcStr() != null) {
            builder.append("str")
        } else if (msvcTypeName?.msvcSlice() != null) {
            builder.append("[")
            msvcTypeName.msvcSlice().type.accept(this)
            builder.append("]")
        } else {
            ctx.type.accept(this)
        }
    }

    // ptr_mut$<MyType> -> *mut MyType
    // ptr_mut$<str> -> *mut str
    // ptr_mut$<slice$<MyType> > -> *mut [MyType]
    @Suppress("DuplicatedCode")
    override fun visitMsvcPtr_mut(ctx: MsvcPtr_mutContext) {
        builder.append("*mut ")

        // BACKCOMPAT: Rust 1.66. Get rid of if-else, replace with `ctx.type.accept(this)`
        val msvcTypeName = ctx.type.msvcTypeName()
        if (msvcTypeName?.msvcStr() != null) {
            builder.append("str")
        } else if (msvcTypeName?.msvcSlice() != null) {
            builder.append("[")
            msvcTypeName.msvcSlice().type.accept(this)
            builder.append("]")
        } else {
            ctx.type.accept(this)
        }
    }

    // ref$<MyType> -> &MyType
    override fun visitMsvcRef(ctx: MsvcRefContext) {
        builder.append("&")
        ctx.type.accept(this)
    }

    // ref_mut$<MyType> -> &mut MyType
    override fun visitMsvcRef_mut(ctx: MsvcRef_mutContext) {
        builder.append("&mut ")
        ctx.type.accept(this)
    }

    // array$<MyType, length> -> [MyType; length]
    override fun visitMsvcArray(ctx: MsvcArrayContext) {
        builder.append("[")
        ctx.type.accept(this)
        builder.append("; ")
        builder.append(ctx.length.text)
        builder.append("]")
    }

    // slice$<MyType> -> &[MyType]
    override fun visitMsvcSlice(ctx: MsvcSliceContext) {
        builder.append("&[")
        ctx.type.accept(this)
        builder.append("]")
    }

    // slice2$<MyType> -> [MyType]
    override fun visitMsvcSlice2(ctx: MsvcSlice2Context) {
        builder.append("[")
        ctx.type.accept(this)
        builder.append("]")
    }

    // Enums before Rust 1.65
    // enum$<MyEnum> -> MyEnum
    // enum$<MyEnum, MyVariant> -> MyEnum::MyVariant
    // enum$<MyEnum, _, _, MyVariant> -> MyEnum::MyVariant
    override fun visitMsvcEnum(ctx: MsvcEnumContext) {
        ctx.type.accept(this)
        val variant = ctx.variant
        if (variant != null) {
            builder.append("::${variant.text}")
        }
    }

    // enum2$<MyEnum> -> MyEnum
    override fun visitMsvcEnum2(ctx: MsvcEnum2Context) {
        ctx.type.accept(this)
    }

    // &str
    override fun visitStr(ctx: StrContext) {
        builder.append("&str")
    }

    // !
    override fun visitNever(ctx: NeverContext) {
        builder.append("!")
    }

    // (MyType1, ..., MyTypeN)
    override fun visitTuple(ctx: TupleContext) {
        builder.append("(")
        ctx.items?.accept(this)
        builder.append(")")
    }

    // *const MyType
    override fun visitPtrConst(ctx: PtrConstContext) {
        builder.append("*const ")
        ctx.type.accept(this)
    }

    // *mut MyType
    override fun visitPtrMut(ctx: PtrMutContext) {
        builder.append("*mut ")
        ctx.type.accept(this)
    }

    // &MyType
    override fun visitRef(ctx: RefContext) {
        builder.append("&")
        ctx.type.accept(this)
    }

    // &mut MyType
    override fun visitRefMut(ctx: RefMutContext) {
        builder.append("&mut ")
        ctx.type.accept(this)
    }

    // [MyType; length]
    override fun visitArray(ctx: ArrayContext) {
        builder.append("[")
        ctx.type.accept(this)
        builder.append("; ")
        builder.append(ctx.length.text)
        builder.append("]")
    }

    // &[MyType]
    override fun visitSlice(ctx: SliceContext) {
        builder.append("[")
        ctx.type.accept(this)
        builder.append("]")
    }

    // foo::bar::Foo<MyType>
    override fun visitQualifiedName(ctx: QualifiedNameContext) {
        val segments = ctx.namespaceSegments
        for (segment in segments.dropLast(1)) {
            segment.accept(this)
            builder.append("::")
        }
        segments.last().accept(this)
    }

    // Foo<MyType>
    override fun visitQualifiedNameSegment(ctx: QualifiedNameSegmentContext) {
        builder.append(ctx.name.text)
        val items = ctx.items
        if (items != null) {
            nestedGenericsLevel++
            if (nestedGenericsLevel >= MAX_NESTED_GENERICS_LEVEL) {
                builder.append("<…>")
                return
            }
            builder.append('<')
            items.accept(this)
            builder.append('>')
            nestedGenericsLevel--
        }
    }

    override fun visitCommaSeparatedList(ctx: CommaSeparatedListContext) {
        val items = ctx.items
        for (child in items.dropLast(1)) {
            child.accept(this)
            builder.append(", ")
        }
        items.last().accept(this)
    }

    companion object {
        private const val MAX_NESTED_GENERICS_LEVEL: Int = 2
    }
}
