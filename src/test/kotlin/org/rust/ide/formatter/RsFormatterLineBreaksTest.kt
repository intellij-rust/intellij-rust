/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.formatter

class RsFormatterLineBreaksTest : RsFormatterTestBase() {
    override fun getTestDataPath() = "src/test/resources"

    override fun getBasePath() = "org/rust/ide/formatter/fixtures/line_breaks"

    override fun getFileExtension() = "rs"

    fun `test all`() = doTest()
    fun `test traits`() = doTest()

    fun `test line breaks between top-level items`() = doTextTest("""
        struct Foo { }
        enum Moo { }
        mod Bar { }
        extern { }


        struct A;



        // Non-doc comments
        // count
        // As part of an item
        struct B;
    """, """
        struct Foo {}

        enum Moo {}

        mod Bar {}

        extern {}


        struct A;


        // Non-doc comments
        // count
        // As part of an item
        struct B;
    """)

    fun `test line breaks between attributes`() = doTextTest("""
        #![allow(dead_code)]    #![cfg(test)]
        #![foo]



        #![bar]


        #[baz] /* x */
        #[allow(dead_code)]/* a */    #[cfg(test)]       /* b */ #[foo] /* c */ enum Person {
        }

        #[cfg(test)] fn foo() {}
    """, """
        #![allow(dead_code)]
        #![cfg(test)]
        #![foo]


        #![bar]


        #[baz] /* x */
        #[allow(dead_code)] /* a */
        #[cfg(test)] /* b */
        #[foo] /* c */
        enum Person {}

        #[cfg(test)]
        fn foo() {}
    """)

    fun `test line breaks between enum variants`() = doTextTest("""
        enum Person {


            // An `enum` may either be `unit-like`,
            Skinny, Fat,



            // like tuple structs,
            Height(i32), Weight(i32),



            // or like structures.
            Info {


                name: String,


                height: i32


            }


        }
    """, """
        enum Person {
            // An `enum` may either be `unit-like`,
            Skinny,
            Fat,

            // like tuple structs,
            Height(i32),
            Weight(i32),

            // or like structures.
            Info {
                name: String,

                height: i32,
            },
        }
    """)

    fun `test line breaks between struct fields`() = doTextTest("""
        struct Rectangle {


            p1: Point, p2: Point,
            p3: Point,

            p4: Point, p5: Point,


            p6: Point, p7: Point


        }
    """, """
        struct Rectangle {
            p1: Point,
            p2: Point,
            p3: Point,

            p4: Point,
            p5: Point,

            p6: Point,
            p7: Point,
        }
    """)

    fun `test blocks`() = doTextTest("""
        fn main() {
            let foo = { foo(123, 456,
                            789) };
        }
    """, """
        fn main() {
            let foo = {
                foo(123, 456,
                    789)
            };
        }
    """)

    fun `test blocks 2`() = checkNotChanged("""
        fn main() {
            let foo = { foo(123, 456, 789) };
        }
    """)

    fun `test keeps comment after block`() = doTextTest("""
        fn main() {
            let bum = || { // does stuff
        };
        }
    """, """
        fn main() {
            let bum = || { // does stuff
            };
        }
    """)

    fun `test multiline blocks`() = doTextTest("""
        struct S1 { f: i32 }
        struct S2 {
        f: i32}
        struct S3 {f: i32
        }

        enum E {
        V{f:i32},
        X{f: i32,
        x: i32}}

        trait Empty { /*bla-bla-bla*/ }

        trait HasStuff { fn foo();
          fn bar();
          fn baz();}

        fn main() {
            let _ = || { /*comment*/ foo(); };
            let _  = || {
                92};
        }
    """, """
        struct S1 { f: i32 }

        struct S2 {
            f: i32
        }

        struct S3 {
            f: i32
        }

        enum E {
            V { f: i32 },
            X {
                f: i32,
                x: i32,
            },
        }

        trait Empty { /*bla-bla-bla*/ }

        trait HasStuff {
            fn foo();
            fn bar();
            fn baz();
        }

        fn main() {
            let _ = || { /*comment*/ foo(); };
            let _ = || {
                92
            };
        }
""")
}
