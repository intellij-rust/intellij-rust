/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.formatter

import org.intellij.lang.annotations.Language
import org.rust.cargo.project.settings.rustfmtSettings
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.RsStructItem
import org.rust.lang.core.psi.ext.descendantOfTypeStrict

class RsTrailingCommaFormatProcessorTest : RsFormatterTestBase() {
    fun `test removes trailing comma if single line in brace blocks`() = doTextTest("""
        use foo::{bar, baz,};

        struct S1 { a: i32, }

        struct S2 { a: i32 }

        enum E {
            V { a: i32, }
        }

        fn main() {
            let _ = S {};
            let _ = S { x: 92 };
            let _ = S { x: 92, };
            let _ = S { x: 92, y: 62 };
            let _ = S { x: 92, y: 62, };
            let _ = S {
                x: 92,
            };

            let _ = S {
                x: 92
            };
        }
    """, """
        use foo::{bar, baz};

        struct S1 {
            a: i32,
        }

        struct S2 {
            a: i32,
        }

        enum E {
            V { a: i32 }
        }

        fn main() {
            let _ = S {};
            let _ = S { x: 92 };
            let _ = S { x: 92 };
            let _ = S { x: 92, y: 62 };
            let _ = S { x: 92, y: 62 };
            let _ = S {
                x: 92,
            };

            let _ = S {
                x: 92
            };
        }
    """)

    fun `test removes trailing comma if single in parent blocks`() = doTextTest("""
        struct T(i32,);

        fn f(x: i32,) {
            foo(1,);
            foo.bar(1, 2,);
        }
    """, """
        struct T(i32);

        fn f(x: i32) {
            foo(1);
            foo.bar(1, 2);
        }
    """)

    fun `test adds trailing commas in multiline blocks blocks`() = doTextTest("""
        struct T1(
            i32
        );

        struct T2(
            i32,
            i32
        );

        fn f(
            x: i32,
            y: i32
        ) {
            foo(
                1
            );
            foo.bar(
                1,
                2
            );
        }
    """, """
        struct T1(
            i32
        );

        struct T2(
            i32,
            i32,
        );

        fn f(
            x: i32,
            y: i32,
        ) {
            foo(
                1
            );
            foo.bar(
                1,
                2,
            );
        }
    """)

    fun `test trailing comma processor works when RustfmtExternalFormatProcessor is used`() {
        @Language("Rust")
        val before = """
            mod foo {
                struct T1(
                    i32,
                    i32
                                                                        // gap
                );

                struct T2(
                    i32,
                    i32
                );
            }
        """.trimIndent()

        @Language("Rust")
        val after = """
            mod foo {
                struct T1(
                    i32,
                    i32,
                    // gap
                );

                struct T2(
                    i32,
                    i32
                );
            }
        """.trimIndent()

        // This enables usage of `RustfmtExternalFormatProcessor`, but not `Rustfmt` itself
        project.rustfmtSettings.modifyTemporary(testRootDisposable) { it.useRustfmt = true }
        // `Rustfmt` will not be used because of range restriction
        myTextRange = RsPsiFactory(project).createFile(before).descendantOfTypeStrict<RsStructItem>()!!.textRange

        doTextTest(before, after)
    }
}
