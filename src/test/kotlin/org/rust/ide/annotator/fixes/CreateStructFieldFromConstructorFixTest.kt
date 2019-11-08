/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import org.rust.ide.annotator.RsAnnotatorTestBase
import org.rust.ide.annotator.RsExpressionAnnotator

class CreateStructFieldFromConstructorFixTest : RsAnnotatorTestBase(RsExpressionAnnotator::class) {

    fun `test basic`() = checkFixByText("Create field", """
        struct S {
            foo: String
        }

        impl S {
            fn new(foo: String, bar: i32) -> S {
                S {
                    foo, <error descr="No such field">bar/*caret*/</error>
                }
            }
        }
    """, """
        struct S {
            foo: String,
            bar: i32
        }

        impl S {
            fn new(foo: String, bar: i32) -> S {
                S {
                    foo, bar/*caret*/
                }
            }
        }
    """)

    fun `test basic 2`() = checkFixByText("Create field", """
        struct S {
            foo: String
        }

        impl S {
            fn new(foo: String, bar: i32, baz: i64) -> S {
                S {
                    foo, <error descr="No such field">bar</error>, <error descr="No such field">baz/*caret*/</error>
                }
            }
        }
    """, """
        struct S {
            foo: String,
            baz: i64
        }

        impl S {
            fn new(foo: String, bar: i32, baz: i64) -> S {
                S {
                    foo, bar, baz/*caret*/
                }
            }
        }
    """)


    fun `test basic 3`() = checkFixByText("Create field", """
        struct S {
            foo: String,
            baz: i64
        }

        impl S {
            fn new(foo: String, bar: i32, baz: i64) -> S {
                S {
                    foo, <error descr="No such field">bar/*caret*/</error>, baz
                }
            }
        }
    """, """
        struct S {
            foo: String,
            baz: i64,
            bar: i32
        }

        impl S {
            fn new(foo: String, bar: i32, baz: i64) -> S {
                S {
                    foo, bar/*caret*/, baz
                }
            }
        }
    """)


    fun `test no block`() = checkFixByText("Create field", """
        struct S;

        impl S {
            fn new(foo: i32) -> S {
                S {
                    <error descr="No such field">foo/*caret*/</error>
                }
            }
        }
    """, """
        struct S { foo: i32 }

        impl S {
            fn new(foo: i32) -> S {
                S {
                    foo/*caret*/
                }
            }
        }
    """)

    fun `test empty block`() = checkFixByText("Create field", """
        struct S {
        }

        impl S {
            fn new(foo: i32) -> S {
                S {
                    <error descr="No such field">foo/*caret*/</error>
                }
            }
        }
    """, """
        struct S {
            foo: i32
        }

        impl S {
            fn new(foo: i32) -> S {
                S {
                    foo/*caret*/
                }
            }
        }
    """)

    fun `test let`() = checkFixByText("Create field", """
        struct S {}

        impl S {
            fn new() -> S {
                let foo: i32 = 42;
                S { <error descr="No such field">foo/*caret*/</error> }
            }
        }
    """, """
        struct S { foo: i32 }

        impl S {
            fn new() -> S {
                let foo: i32 = 42;
                S { foo/*caret*/ }
            }
        }
    """)

    fun `test filed has expression`() = checkFixByText("Create field", """
        struct S {}

        impl S {
            fn new(bar: i32) -> S {
                S { <error descr="No such field">foo/*caret*/</error>: bar }
            }
        }
    """, """
        struct S { foo: i32 }

        impl S {
            fn new(bar: i32) -> S {
                S { foo/*caret*/: bar }
            }
        }
    """)

    fun `test pub`() = checkFixByText("Create field", """
        mod foo {
            pub struct S {
            }
        }

        impl foo::S {
            fn new(foo: i32) -> foo::S {
                foo::S {
                    <error descr="No such field">foo/*caret*/</error>
                }
            }
        }
    """, """
        mod foo {
            pub struct S {
                pub foo: i32
            }
        }

        impl foo::S {
            fn new(foo: i32) -> foo::S {
                foo::S {
                    foo/*caret*/
                }
            }
        }
    """)

    fun `test pub2`() = checkFixByText("Create field", """
        mod foo {
            pub struct S;
        }

        impl foo::S {
            fn new(foo: i32) -> foo::S {
                foo::S {
                    <error descr="No such field">foo/*caret*/</error>
                }
            }
        }
    """, """
        mod foo {
            pub struct S { pub foo: i32 }
        }

        impl foo::S {
            fn new(foo: i32) -> foo::S {
                foo::S {
                    foo/*caret*/
                }
            }
        }
    """)

    fun `test tuple type`() = checkFixByText("Create field", """
        struct S;

        impl S {
            fn new((x, y): (i32, i64)) -> S {
                S {
                    <error descr="No such field">x/*caret*/</error>
                }
            }
        }
    """, """
        struct S { x: i32 }

        impl S {
            fn new((x, y): (i32, i64)) -> S {
                S {
                    x/*caret*/
                }
            }
        }
    """)

    fun `test type arguments`() = checkFixIsUnavailable("Create field", """
        struct S;

        struct A<'a>(&'a str);

        impl S {
            fn new<'a>(foo: A<'a>) -> S {
                S { <error descr="No such field">foo/*caret*/</error> }
            }
        }
    """)

    fun `test tuple`() = checkFixIsUnavailable("Create field", """
        struct S(i32);

        fn main() {
            let foo: i32 = 0;
            <error descr="Some fields are missing">S</error> { <error descr="No such field">foo/*caret*/</error> };
        }
    """)

    fun `test unavailable type`() = checkFixIsUnavailable("Create field", """
        struct St {}
        trait Tr {}

        impl St {
            fn new<T>(x: T, t: impl Tr, z: &str) -> St {
                St { <error descr="No such field">x/*caret*/</error> }
            }
        }
    """)


    fun `test unavailable type 2`() = checkFixIsUnavailable("Create field", """
        struct St {}
        trait Tr {}

        impl St {
            fn new<T>(x: T, t: impl Tr, z: &str) -> St {
                St { <error descr="No such field">t/*caret*/</error> }
            }
        }
    """)


    fun `test unavailable type 3`() = checkFixIsUnavailable("Create field", """
        struct St {}
        trait Tr {}

        impl St {
            fn new<T>(x: T, t: impl Tr, z: &str) -> St {
                St { <error descr="No such field">z/*caret*/</error> }
            }
        }
    """)
}
