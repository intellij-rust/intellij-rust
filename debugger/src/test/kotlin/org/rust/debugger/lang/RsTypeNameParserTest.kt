/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.debugger.lang

import org.junit.Assert.assertEquals
import org.junit.Test

class RsTypeNameParserTest {

    // MSVC type names

    @Test
    fun `test parse msvc str`() = checkParse(
        "str",
        "(typeName (msvcTypeName (msvcStr str)))"
    )

    @Test
    fun `test parse msvc str$`() = checkParse(
        "str$",
        "(typeName (msvcTypeName (msvcStrDollar str$)))"
    )

    @Test
    fun `test parse msvc never`() = checkParse(
        "never$",
        "(typeName (msvcTypeName (msvcNever never$)))"
    )

    @Test
    fun `test parse msvc tuple`() = checkParse(
        "tuple$<i32, i32>",
        "(typeName (msvcTypeName (msvcTuple tuple$ < (commaSeparatedList (typeName (commonTypeName (qualifiedName (qualifiedNameSegment i32)))) , (typeName (commonTypeName (qualifiedName (qualifiedNameSegment i32))))) >)))"
    )

    @Test
    fun `test parse msvc tuple unit`() = checkParse(
        "tuple$<>",
        "(typeName (msvcTypeName (msvcTuple tuple$ < >)))"
    )

    @Test
    fun `test parse msvc ptr_const`() = checkParse(
        "ptr_const$<Foo>",
        "(typeName (msvcTypeName (msvcPtr_const ptr_const$ < (typeName (commonTypeName (qualifiedName (qualifiedNameSegment Foo)))) >)))"
    )

    @Test
    fun `test parse msvc ptr_mut`() = checkParse(
        "ptr_mut$<Foo>",
        "(typeName (msvcTypeName (msvcPtr_mut ptr_mut$ < (typeName (commonTypeName (qualifiedName (qualifiedNameSegment Foo)))) >)))"
    )

    @Test
    fun `test parse msvc ref`() = checkParse(
        "ref$<Foo>",
        "(typeName (msvcTypeName (msvcRef ref$ < (typeName (commonTypeName (qualifiedName (qualifiedNameSegment Foo)))) >)))"
    )

    @Test
    fun `test parse msvc ref_mut`() = checkParse(
        "ref_mut$<Foo>",
        "(typeName (msvcTypeName (msvcRef_mut ref_mut$ < (typeName (commonTypeName (qualifiedName (qualifiedNameSegment Foo)))) >)))"
    )

    @Test
    fun `test parse msvc array`() = checkParse(
        "array$<Foo, 10>",
        "(typeName (msvcTypeName (msvcArray array$ < (typeName (commonTypeName (qualifiedName (qualifiedNameSegment Foo)))) , 10 >)))"
    )

    @Test
    fun `test parse msvc slice`() = checkParse(
        "slice$<Foo>",
        "(typeName (msvcTypeName (msvcSlice slice$ < (typeName (commonTypeName (qualifiedName (qualifiedNameSegment Foo)))) >)))"
    )

    @Test
    fun `test parse msvc slice2`() = checkParse(
        "slice2$<Foo>",
        "(typeName (msvcTypeName (msvcSlice2 slice2$ < (typeName (commonTypeName (qualifiedName (qualifiedNameSegment Foo)))) >)))"
    )

    @Test
    fun `test parse msvc enum`() = checkParse(
        "enum$<MyEnum>",
        "(typeName (msvcTypeName (msvcEnum enum$ < (typeName (commonTypeName (qualifiedName (qualifiedNameSegment MyEnum)))) >)))"
    )

    @Test
    fun `test parse msvc enum single`() = checkParse(
        "enum$<SingleVariantEnum, SingleVariant>",
        "(typeName (msvcTypeName (msvcEnum enum$ < (typeName (commonTypeName (qualifiedName (qualifiedNameSegment SingleVariantEnum)))) , SingleVariant >)))"
    )

    @Test
    fun `test parse msvc enum niche`() = checkParse(
        "enum$<Option<i32>, 2, 16, Some>",
        "(typeName (msvcTypeName (msvcEnum enum$ < (typeName (commonTypeName (qualifiedName (qualifiedNameSegment Option < (commaSeparatedList (typeName (commonTypeName (qualifiedName (qualifiedNameSegment i32))))) >)))) , (typeName (commonTypeName (qualifiedName (qualifiedNameSegment 2)))) , (typeName (commonTypeName (qualifiedName (qualifiedNameSegment 16)))) , Some >)))"
    )

    @Test
    fun `test parse msvc enum2`() = checkParse(
        "enum2$<MyEnum>",
        "(typeName (msvcTypeName (msvcEnum2 enum2$ < (typeName (commonTypeName (qualifiedName (qualifiedNameSegment MyEnum)))) >)))"
    )

    // Common type names

    @Test
    fun `test parse str`() = checkParse(
        "&str",
        "(typeName (commonTypeName (str & str)))"
    )

    @Test
    fun `test parse never`() = checkParse(
        "!",
        "(typeName (commonTypeName (never !)))"
    )

    @Test
    fun `test parse tuple`() = checkParse(
        "(i8, i16)",
        "(typeName (commonTypeName (tuple ( (commaSeparatedList (typeName (commonTypeName (qualifiedName (qualifiedNameSegment i8)))) , (typeName (commonTypeName (qualifiedName (qualifiedNameSegment i16))))) ))))"
    )

    @Test
    fun `test parse nested tuple`() = checkParse(
        "((i8, i16), i32)",
        "(typeName (commonTypeName (tuple ( (commaSeparatedList (typeName (commonTypeName (tuple ( (commaSeparatedList (typeName (commonTypeName (qualifiedName (qualifiedNameSegment i8)))) , (typeName (commonTypeName (qualifiedName (qualifiedNameSegment i16))))) )))) , (typeName (commonTypeName (qualifiedName (qualifiedNameSegment i32))))) ))))"
    )

    @Test
    fun `test parse tuple unit`() = checkParse(
        "()",
        "(typeName (commonTypeName (tuple ( ))))"
    )

    @Test
    fun `test parse ptr_const`() = checkParse(
        "*const Foo",
        "(typeName (commonTypeName (ptrConst * const (typeName (commonTypeName (qualifiedName (qualifiedNameSegment Foo)))))))"
    )

    @Test
    fun `test parse ptr_mut`() = checkParse(
        "*mut Foo",
        "(typeName (commonTypeName (ptrMut * mut (typeName (commonTypeName (qualifiedName (qualifiedNameSegment Foo)))))))"
    )

    @Test
    fun `test parse ref`() = checkParse(
        "&Foo",
        "(typeName (commonTypeName (ref & (typeName (commonTypeName (qualifiedName (qualifiedNameSegment Foo)))))))"
    )

    @Test
    fun `test parse ref_mut`() = checkParse(
        "&mut Foo",
        "(typeName (commonTypeName (refMut & mut (typeName (commonTypeName (qualifiedName (qualifiedNameSegment Foo)))))))"
    )

    @Test
    fun `test parse array`() = checkParse(
        "[Foo; 10]",
        "(typeName (commonTypeName (array [ (typeName (commonTypeName (qualifiedName (qualifiedNameSegment Foo)))) ; 10 ])))"
    )

    @Test
    fun `test parse slice`() = checkParse(
        "[Foo]",
        "(typeName (commonTypeName (slice [ (typeName (commonTypeName (qualifiedName (qualifiedNameSegment Foo)))) ])))"
    )

    @Test
    fun `test not parsed empty`() = checkParse("", null)

    @Test
    fun `test not parsed incomplete type`() = checkParse("foo::Foo<", null)

    @Test
    fun `test not parsed unknown type`() = checkParse("Foo<Bar<Baz<Quux>, unknown$>>", null)

    private fun checkParse(before: String, after: String?) {
        val actual = RsTypeNameParserFacade.parseToStringTree(before)
        assertEquals(after, actual)
    }
}
