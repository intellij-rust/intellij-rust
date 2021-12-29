/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.console

import org.rust.UseOldResolve

class RsConsoleCompletionTest : RsConsoleCompletionTestBase() {

    fun `test initial completion`() = checkContainsCompletion("""

    """, """
        /*caret*/
    """, variants = listOf(
        "let",
        "fn",
        "struct",
        "Some",
        "None"
    ))

    fun `test variables (primitive types)`() = checkContainsCompletion("""
        let var1 = 1;
        let var2 = var1 + 1;
    """, """
        let x = var/*caret*/
    """, variants = listOf(
        "var1",
        "var2"
    ))

    fun `test variables (type from prelude)`() = checkSingleCompletion("""
        let frobnicate = Some(1);
    """, """
        let x = frobn/*caret*/
    """, """
        let x = frobnicate/*caret*/
    """)

    fun `test methods (type from prelude)`() = checkContainsCompletion("""
        let var1 = Some(1);
    """, """
        let x = var1./*caret*/
    """, variants = listOf(
        "unwrap",
        "map",
        "and_then"
    ))

    fun `test fields (custom struct)`() = checkSingleCompletion("""
        struct Foo { frobnicate: u32 }
        let foo = Foo { frobnicate: 1 };
    """, """
        let x = foo.frobn/*caret*/
    """, """
        let x = foo.frobnicate/*caret*/
    """)

    fun `test functions`() = checkSingleCompletion("""
        fn frobnicate() {}
    """, """
        frobn/*caret*/
    """, """
        frobnicate()/*caret*/
    """)

    fun `test variables with dependant types`() = checkSingleCompletion("""
        struct Foo1 { field1: i32 }
        struct Foo2 { field2: Foo1 }
        let foo2 = Foo2 { field2: Foo1 { field1: 0 } };
        let foo1 = foo2.field2;
    """, """
        foo1.fie/*caret*/
    """, """
        foo1.field1/*caret*/
    """)

    fun `test variables with unspecified type`() = checkContainsCompletion("""
        let s = "foo";
    """, """
        s./*caret*/
    """, variants = listOf(
        "len",
        "chars"
    ))

    fun `test vector`() = checkContainsCompletion("""
        let v = Vec::<i32>::new();
    """, """
        v.i/*caret*/
    """, variants = listOf(
        "insert",
        "is_empty",
        "index"
    ))

    fun `test after statement`() = checkSingleCompletion("""
        fn nop() {}
        let frobnicate = 1;
    """, """
        nop();
        let x = frobn/*caret*/
    """, """
        nop();
        let x = frobnicate/*caret*/
    """)

    fun `test nested`() = checkContainsCompletion("""
        fn nop() {}
        let var1 = 1;
        let var2 = 2;
    """, """
        nop();
        if true {
            for i in 0..10 {
                let x = var/*caret*/
            }
        }
        nop();
    """, variants = listOf(
        "var1",
        "var2"
    ))

    fun `test child module without import`() = checkSingleCompletion("""
        mod mod1 { pub fn frobnicate() {} }
    """, """
        mod1::frobn/*caret*/
    """, """
        mod1::frobnicate()/*caret*/
    """)

    @UseOldResolve
    fun `test child module with import`() = checkSingleCompletion("""
        mod mod1 { pub fn frobnicate() {} }
        use mod1::frobnicate;
    """, """
        frobn/*caret*/
    """, """
        frobnicate()/*caret*/
    """)

    fun `test imports 1`() = checkContainsCompletion("""
        use std::collections;
    """, """
        collections::/*caret*/
    """, variants = listOf(
        "HashMap",
        "BTreeSet"
    ))

    fun `test imports 2`() = checkContainsCompletion("""
        use std::collections;
    """, """
        use collections::hash_map;
    """, """
        hash_map::/*caret*/
    """, variants = listOf(
        "HashMap",
        "Iter"
    ))

    fun `test import f32 1`() = checkContainsCompletion("""
        use std::f32;
    """, """
        f32::consts::/*caret*/
    """, variants = listOf(
        "PI",
        "E"
    ))

    fun `test import f32 2`() = checkContainsCompletion("""
        let foo = f32::log2(2f32);
    """, """
        use std::f32;
    """, """
        foo./*caret*/
    """, variants = listOf(
        "sin",
        "cos",
        "log"
    ))

    fun `test redefine function`() = checkContainsCompletion("""
        fn foo() -> Option<i32> { Some(1) }
        let var = foo();
    """, """
        fn foo() {}
    """, """
        var./*caret*/
    """, variants = listOf(
        "unwrap",
        "expect"
    ))

    fun `test redefine function 2`() = checkContainsCompletion("""
        fn foo() -> i32 { 1 }
    """, """
        fn foo() -> Option<i32> { Some(1) }
    """, """
        foo()./*caret*/
    """, variants = listOf(
        "unwrap",
        "expect"
    ))

    fun `test redefine struct`() = checkContainsCompletion("""
        struct Foo { field1: i32, field2: i32 }
        let var1 = Foo { field1: 0, field2: 0 };
    """, """
        struct Bar { field3: i32 }
    """, """
        struct Foo { field4: i32 }
    """, """
        var1./*caret*/
    """, variants = listOf(
        "field1",
        "field2"
    ))

    fun `test redefine struct from prelude`() = checkContainsCompletion("""
        let foo = Some(1);
    """, """
        struct Some(i32, i32);
    """, """
        foo./*caret*/
    """, variants = listOf(
        "unwrap",
        "expect"
    ))

    fun `test last expression is RsBlock`() = checkContainsCompletion("""
        fn func1() {}
        fn func2() {}
    """, """
        { func/*caret*/ }
    """, variants = listOf(
        "func1",
        "func2"
    ))
}
