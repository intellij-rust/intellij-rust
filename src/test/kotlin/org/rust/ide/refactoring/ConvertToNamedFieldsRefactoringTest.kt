/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring

import org.intellij.lang.annotations.Language
import org.rust.RsTestBase
import org.rust.ide.refactoring.convertStruct.RsConvertToNamedFieldsAction

class ConvertToNamedFieldsRefactoringTest : RsTestBase() {

    fun `test simple`() = doAvailableTest("""
        struct Test (pub usize,/*caret*/ i32);
    """, """
        struct Test {
            pub _0: usize,
            _1: i32
        }
    """)

    fun `test empty`() = doAvailableTest("""
        struct Test (/*caret*/);
    """, """
        struct Test {}
    """)

    fun `test simple enum`() = doAvailableTest("""
        enum Test{
            A(usize,/*caret*/ i32),
            B
        }
        fn main(){
            let Test::A(a, b) = Test::A(0, 0);
        }
    """, """
        enum Test{
            A {
                _0: usize,
                _1: i32
            },
            B
        }
        fn main(){
            let Test::A { _0: a, _1: b } = Test::A {
                _0: 0,
                _1: 0
            };
        }
    """)

    fun `test convert tuple literal`() = doAvailableTest("""
        struct Test(pub usize,/*caret*/ i32);
        fn main (){
            let a = Test(0, 0);
            let Test{0: a, 1: b}= Test{0: 0, 1: 0};
        }
    """, """
        struct Test {
            pub _0: usize,
            _1: i32
        }

        fn main (){
            let a = Test {
                _0: 0,
                _1: 0
            };
            let Test{ _0: a, _1: b}= Test{ _0: 0, _1: 0};
        }
    """)

    fun `test convert field access`() = doAvailableTest("""
        struct Test(pub usize,/*caret*/ i32);
        fn main (){
            let var = Test(0, 0);
            let x = var.0;
            let x = var.1;
        }
    """, """
        struct Test {
            pub _0: usize,
            _1: i32
        }

        fn main (){
            let var = Test {
                _0: 0,
                _1: 0
            };
            let x = var._0;
            let x = var._1;
        }
    """)

    fun `test convert destructuring`() = doAvailableTest("""
        struct Test(pub usize,/*caret*/ i32);
        fn main (){
            let var = Test(0, 0);
            let Test(a, b) = &var;
            let Test(ref a, mut b) = &var;
            let Test(a, _) = &var;
            let Test(a, ..) = &var;
            let Test(.. ,b) = &var;
            let Test(_, b) = &var;
        }
    """, """
        struct Test {
            pub _0: usize,
            _1: i32
        }

        fn main (){
            let var = Test {
                _0: 0,
                _1: 0
            };
            let Test { _0: a, _1: b } = &var;
            let Test { _0: ref a, _1: mut b } = &var;
            let Test { _0: a, _1: _ } = &var;
            let Test { _0: a, .. } = &var;
            let Test { _1: b, .. } = &var;
            let Test { _0: _, _1: b } = &var;
        }
    """)

    // https://github.com/intellij-rust/intellij-rust/issues/5017
    fun `test convert function call`() = doAvailableTest("""
        struct S(/*caret*/u32);
        impl S {
            fn new(v: u32) -> S {
                S(v)
            }
        }

        fn main() {
            let s = S::new(0);
        }
    """, """
        struct S { _0: u32 }

        impl S {
            fn new(v: u32) -> S {
                S { _0: v }
            }
        }

        fn main() {
            let s = S::new(0);
        }
    """)

    fun `test convert function call in mod`() = doAvailableTest("""
        mod nested {
            pub struct S(/*caret*/pub u32);
            impl S {
                pub fn new(v: u32) -> S {
                    S(v)
                }
            }
        }

        fn main() {
            let s1 = nested::S(1);
            let s2 = nested::S::new(0);
        }
    """, """
        mod nested {
            pub struct S { pub _0: u32 }

            impl S {
                pub fn new(v: u32) -> S {
                    S { _0: v }
                }
            }
        }

        fn main() {
            let s1 = nested::S { _0: 1 };
            let s2 = nested::S::new(0);
        }
    """)

    private fun doAvailableTest(@Language("Rust") before: String, @Language("Rust") after: String) {
        InlineFile(before.trimIndent()).withCaret()
        myFixture.testAction(RsConvertToNamedFieldsAction())
        myFixture.checkResult(replaceCaretMarker(after.trimIndent()))
    }
}
