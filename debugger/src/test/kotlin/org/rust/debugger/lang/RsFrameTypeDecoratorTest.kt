/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.debugger.lang

import org.junit.Assert.assertEquals
import org.junit.Test

class RsFrameTypeDecoratorTest {

    // MSVC type names

    @Test
    fun `test decorate msvc str`() = checkDecorate(
        "str",
        "&str"
    )

    @Test
    fun `test decorate msvc ptr_const str`() = checkDecorate(
        "ptr_const$<str>",
        "*const str"
    )

    @Test
    fun `test decorate msvc ptr_mut str`() = checkDecorate(
        "ptr_mut$<str>",
        "*mut str"
    )

    @Test
    fun `test decorate msvc ref str$`() = checkDecorate(
        "ref$<str$>",
        "&str"
    )

    @Test
    fun `test decorate msvc ref_mut str$`() = checkDecorate(
        "ref_mut$<str$>",
        "&mut str"
    )

    @Test
    fun `test decorate msvc ptr_const str$`() = checkDecorate(
        "ptr_const$<str$>",
        "*const str"
    )

    @Test
    fun `test decorate msvc ptr_mut str$`() = checkDecorate(
        "ptr_mut$<str$>",
        "*mut str"
    )

    @Test
    fun `test decorate msvc str in generics`() = checkDecorate(
        "Foo<str>",
        "Foo<&str>"
    )

    @Test
    fun `test decorate msvc never`() = checkDecorate(
        "never$",
        "!"
    )

    @Test
    fun `test decorate msvc tuple`() = checkDecorate(
        "tuple$<Foo, Bar, Baz>",
        "(Foo, Bar, Baz)"
    )

    @Test
    fun `test decorate nested msvc tuple`() = checkDecorate(
        "tuple$<tuple$<Foo, Bar>, Baz>",
        "((Foo, Bar), Baz)"
    )

    @Test
    fun `test decorate msvc tuple unit`() = checkDecorate(
        "tuple$<>",
        "()"
    )

    @Test
    fun `test decorate msvc ptr_const`() = checkDecorate(
        "ptr_const$<Foo>",
        "*const Foo"
    )

    @Test
    fun `test decorate msvc ptr_mut`() = checkDecorate(
        "ptr_mut$<Foo>",
        "*mut Foo"
    )

    @Test
    fun `test decorate msvc ref`() = checkDecorate(
        "ref$<Foo>",
        "&Foo"
    )
    @Test
    fun `test decorate msvc ref_mut`() = checkDecorate(
        "ref_mut$<Foo>",
        "&mut Foo"
    )

    @Test
    fun `test decorate msvc array`() = checkDecorate(
        "array$<Foo, 10>",
        "[Foo; 10]"
    )

    @Test
    fun `test decorate msvc array in generics`() = checkDecorate(
        "Foo<array$<Bar, 10>>",
        "Foo<[Bar; 10]>"
    )

    @Test
    fun `test decorate msvc slice`() = checkDecorate(
        "slice$<Foo>",
        "&[Foo]"
    )

    @Test
    fun `test decorate msvc ptr_const slice`() = checkDecorate(
        "ptr_const$<slice$<Foo> >",
        "*const [Foo]"
    )

    @Test
    fun `test decorate msvc ptr_mut slice`() = checkDecorate(
        "ptr_mut$<slice$<Foo> >",
        "*mut [Foo]"
    )

    @Test
    fun `test decorate msvc ref slice2`() = checkDecorate(
        "ref$<slice2$<Foo> >",
        "&[Foo]"
    )

    @Test
    fun `test decorate msvc ref_mut slice2`() = checkDecorate(
        "ref_mut$<slice2$<Foo> >",
        "&mut [Foo]"
    )

    @Test
    fun `test decorate msvc ptr_const slice2`() = checkDecorate(
        "ptr_const$<slice2$<Foo> >",
        "*const [Foo]"
    )

    @Test
    fun `test decorate msvc ptr_mut slice2`() = checkDecorate(
        "ptr_mut$<slice2$<Foo> >",
        "*mut [Foo]"
    )

    @Test
    fun `test decorate msvc slice in generics`() = checkDecorate(
        "Foo<slice$<Bar>>",
        "Foo<&[Bar]>"
    )

    @Test
    fun `test decorate msvc enum option`() = checkDecorate(
        "enum$<core::option::Option<i32> >",
        "core::option::Option<i32>"
    )

    @Test
    fun `test decorate msvc enum single`() = checkDecorate(
        "enum$<SingleVariantEnum, SingleVariant>",
        "SingleVariantEnum::SingleVariant"
    )

    @Test
    fun `test decorate msvc enum niche`() = checkDecorate(
        "enum$<core::option::Option<Foo>, 2, 16, Some>",
        "core::option::Option<Foo>::Some"
    )

    @Test
    fun `test decorate msvc enum2`() = checkDecorate(
        "enum2$<core::option::Option<i32> >",
        "core::option::Option<i32>"
    )

    @Test
    fun `test decorate msvc tuple enum2`() = checkDecorate(
        "foo::Foo<tuple$<usize,usize,enum2$<bar::Bar> > >",
        "foo::Foo<(usize, usize, bar::Bar)>"
    )

    @Test
    fun `test decorate msvc HashMap`() = checkDecorate(
        "std::collections::hash::map::HashMap<tuple$<usize,usize>,bool,std::collections::hash::map::RandomState>",
        "std::collections::hash::map::HashMap<(usize, usize), bool, std::collections::hash::map::RandomState>"
    )

    @Test
    fun `test decorate msvc Vec`() = checkDecorate(
        "alloc::vec::Vec<tuple$<usize,usize,enum2$<foo::Foo> >,alloc::alloc::Global>",
        "alloc::vec::Vec<(usize, usize, foo::Foo), alloc::alloc::Global>"
    )

    @Test
    fun `test decorate msvc pointer to Vec`() = checkDecorate(
        "*mut alloc::vec::Vec<tuple$<i32, i32>,alloc::alloc::Global>",
        "*mut alloc::vec::Vec<(i32, i32), alloc::alloc::Global>"
    )

    // Common type names

    @Test
    fun `test decorate deeply nested qualified names`() = checkDecorate(
        "Foo<Bar<Baz<Quux>>>",
        "Foo<Bar<…>>"
    )

    @Test
    fun `test decorate nested qualified names unknown type`() = checkDecorate(
        "Foo<Bar<Baz<Quux>, unknown$>>",
        "Foo<Bar>"
    )

    @Test
    fun `test preserve qualified name`() = checkPreserve(
        "foo::Foo"
    )

    @Test
    fun `test preserve nested qualified names`() = checkPreserve(
        "foo::Foo<bar::Bar>"
    )

    @Test
    fun `test preserve str`() = checkPreserve(
        "&str"
    )

    @Test
    fun `test preserve never`() = checkPreserve(
        "!"
    )

    @Test
    fun `test preserve tuple`() = checkPreserve(
        "(i8, i16)"
    )

    @Test
    fun `test preserve tuple unit`() = checkPreserve(
        "()"
    )

    @Test
    fun `test preserve ptr const`() = checkPreserve(
        "*const i32"
    )

    @Test
    fun `test preserve ptr mut`() = checkPreserve(
        "*mut i32"
    )

    @Test
    fun `test preserve ref const`() = checkPreserve(
        "&i32"
    )

    @Test
    fun `test preserve ref mut`() = checkPreserve(
        "&mut i32"
    )

    @Test
    fun `test preserve array 1`() = checkPreserve(
        "[foo::Foo; 5]"
    )

    @Test
    fun `test preserve array 2`() = checkPreserve(
        "[foo::Foo; LEN]"
    )

    @Test
    fun `test preserve slice`() = checkPreserve(
        "[foo::Foo]"
    )

    @Test
    fun `test preserve unknown type`() = checkPreserve(
        "Foo<Bar, unknown$>"
    )

    private fun checkDecorate(before: String, after: String) {
        val decoratedTypeName = RsFrameTypeDecorator.decorate(before)
        assertEquals(after, decoratedTypeName)
    }

    private fun checkPreserve(typeName: String) = checkDecorate(typeName, typeName)
}
