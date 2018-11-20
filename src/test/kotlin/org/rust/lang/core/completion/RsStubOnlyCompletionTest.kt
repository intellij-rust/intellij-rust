/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

class RsStubOnlyCompletionTest : RsCompletionTestBase() {
    fun `test function`() = doSingleCompletionMultifile("""
    //- foo.rs
        pub fn bar(a: i32, b: u8) {}
    //- main.rs
        mod foo;
        use foo::b/*caret*/;
    """, """
        mod foo;
        use foo::bar/*caret*/;
    """)

    fun `test constant`() = doSingleCompletionMultifile("""
    //- foo.rs
        pub const CONST: i32 = 0;
    //- main.rs
        mod foo;
        use foo::C/*caret*/;
    """, """
        mod foo;
        use foo::CONST/*caret*/;
    """)

    fun `test tuple struct`() = doSingleCompletionMultifile("""
    //- foo.rs
        pub struct Struct(i32);
    //- main.rs
        mod foo;
        use foo::S/*caret*/;
    """, """
        mod foo;
        use foo::Struct/*caret*/;
    """)

    fun `test field`() = doSingleCompletionMultifile("""
    //- foo.rs
        pub struct S {
            pub field: i32
        }
    //- main.rs
        mod foo;
        fn bar(s: foo::S) { s.f/*caret*/ }
    """, """
        mod foo;
        fn bar(s: foo::S) { s.field/*caret*/ }
    """)
}
