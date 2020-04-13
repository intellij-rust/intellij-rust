/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.console

class RsConsoleCompletionTest : RsConsoleCompletionTestBase() {

    fun `test initial completion`() = checkContainsCompletion("""

    """, """
        /*caret*/
    """, listOf(
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
    """, listOf(
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
    """, listOf(
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
    """, listOf(
        "len",
        "chars"
    ))

    fun `test vector`() = checkContainsCompletion("""
        let v = Vec::<i32>::new();
    """, """
        v.i/*caret*/
    """, listOf(
        "insert",
        "is_empty",
        "index"
    ))
}
