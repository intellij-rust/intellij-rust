/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.console

class RsConsoleCompletionTest : RsConsoleCompletionTestBase() {

    fun `test initial completion`() = checkReplCompletion("""

    """, """
        /*caret*/
    """, arrayOf(
        "let",
        "fn",
        "struct",
        "Some",
        "None"
    ))

    fun `test variables (primitive types)`() = checkReplCompletion("""
        let var1 = 1;
        let var2 = var1 + 1;
    """, """
        let x = var/*caret*/
    """, arrayOf(
        "var1",
        "var2"
    ))

    fun `test variables (type from prelude)`() = checkReplCompletion("""
        let var1 = Some(1);
    """, """
        let x = var/*caret*/
    """,
        "var1"
    )

    fun `test methods (type from prelude)`() = checkReplCompletion("""
        let var1 = Some(1);
    """, """
        let x = var1./*caret*/
    """, arrayOf(
        "unwrap",
        "map",
        "and_then"
    ))

    fun `test fields (custom struct)`() = checkReplCompletion("""
        struct Foo { field1: u32 }
        let foo = Foo { field1: 1 };
    """, """
        let x = foo./*caret*/
    """,
        "field1"
    )

    fun `test functions`() = checkReplCompletion("""
        fn foo1() {}
    """, """
        foo/*caret*/
    """,
        "foo1"
    )

    fun `test variables with dependant types`() = checkReplCompletion("""
        struct Foo1 { field1: i32 }
        struct Foo2 { field2: Foo1 }
        let foo2 = Foo2 { field2: Foo1 { field1: 0 } };
        let foo1 = foo2.field2;
    """, """
        foo1./*caret*/
    """,
        "field1"
    )

    fun `test variables with unspecified type`() = checkReplCompletion("""
        let s = "foo";
    """, """
        s./*caret*/
    """, arrayOf(
        "len",
        "chars"
    ))

    fun `test vector`() = checkReplCompletion("""
        let v = Vec::<i32>::new();
    """, """
        v.i/*caret*/
    """, arrayOf(
        "insert",
        "is_empty",
        "index"
    ))
}
