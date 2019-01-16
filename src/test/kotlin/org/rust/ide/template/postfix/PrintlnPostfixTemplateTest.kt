/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.template.postfix

import org.rust.ProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor

@ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
class PrintlnPostfixTemplateTests : RsPostfixTemplateTest(PrintlnPostfixTemplate(RsPostfixTemplateProvider())) {
    fun `test string`() = doTest("""
       fn main() {
           "Arbitrary string".println/*caret*/
       }
    """, """
       fn main() {
           println!("Arbitrary string");/*caret*/
       }
    """)

    fun `test method returning Debug`() = doTest("""
        #[derive(Debug)]
        enum E { A }

        fn test() { E::A }

        fn main() {
            test().println/*caret*/
        }
    """, """
        #[derive(Debug)]
        enum E { A }

        fn test() { E::A }

        fn main() {
            println!("{:?}", test());/*caret*/
        }
    """)

    fun `test method returning no Debug`() = doTestNotApplicable("""
        struct S {}

        fn test() -> S { S {} }

        fn main() {
            test().println/*caret*/
        }
    """)

    fun `test macro`() = doTest("""
        fn main() {
            assert_eq!(2, 2).println/*caret*/
        }
    """, """
        fn main() {
            println!("{:?}", assert_eq!(2, 2));/*caret*/
        }
    """)

    fun `test struct implementing Display`() = doTest("""
        use std::fmt::Display;
        use std::fmt::Formatter;
        use std::fmt::Error;

        struct S { }
        impl Display for S { fn fmt(&self, f: &mut Formatter) -> Result<(), Error> { unimplemented!() } }

        fn main() {
            &&S {}.println/*caret*/
        }
    """, """
        use std::fmt::Display;
        use std::fmt::Formatter;
        use std::fmt::Error;

        struct S { }
        impl Display for S { fn fmt(&self, f: &mut Formatter) -> Result<(), Error> { unimplemented!() } }

        fn main() {
            println!("{}", &&S {});/*caret*/
        }
    """)

    fun `test ignored let expression`() = doTestNotApplicable("""
        fn main() {
            let _ = 4.println/*caret*/;
        }
    """)

    fun `test let expression`() = doTest("""
        fn main() {
            let test = 4.println/*caret*/;
        }
    """, """
        fn main() {
            let test = 4;
            println!("{:?}", test);/*caret*/
        }
    """)

    fun `test let expression without semicolon`() = doTest("""
        fn main() {
            let test = 4.println/*caret*/
        }
    """, """
        fn main() {
            let test = 4;
            println!("{:?}", test);/*caret*/
        }
    """)

    fun `test let expression with a comment not in the end of a block`() = doTest("""
        fn main() {
            let a = 123;
            let b = a - 42 * 3.println/*caret*/; // this is a comment
            let c = a + b;
        }
    """, """
        fn main() {
            let a = 123;
            let b = a - 42 * 3; // this is a comment
            println!("{:?}", b);/*caret*/
            let c = a + b;
        }
    """)

    fun `test inner println in the let expression`() = doTest("""
        fn main() {
            let a = 123;
            let b = a - 42.println/*caret*/ * 3; // this is a comment
            let c = a + b;
        }
    """, """
        fn main() {
            let a = 123;
            let b = a - 42 * 3; // this is a comment
            println!("{:?}", 42);/*caret*/
            let c = a + b;
        }
    """)

    fun `test inner println in the match expression`() = doTest("""
        enum E<T> { A(T), B }

        fn main() {
            let a = 123;
            match E::A(22) {
                E::A(value) => a - 42.println/*caret*/ * 3 // this is a comment
                _ => -1
            };
        }
    """, """
        enum E<T> { A(T), B }

        fn main() {
            let a = 123;
            match E::A(22) {
                E::A(value) => {
                    println!("{:?}", 42);/*caret*/
                    a - 42 * 3
                } // this is a comment
                _ => -1
            };
        }
    """)

    fun `test match expression`() = doTest("""
        enum E<T> { A(T), B }

        fn main() {
            match E::A(22) {
                E::A(value) => value,
                _ => -1
            }.println/*caret*/
        }
    """, """
        enum E<T> { A(T), B }

        fn main() {
            println!("{:?}", match E::A(22) {
                E::A(value) => value,
                _ => -1
            });/*caret*/
        }
    """)

    fun `test multi line match arm`() = doTest("""
        enum E<T> { A(T), B }

        fn main() {
            match E::A(22) {
                E::A(value) => {
                    value.println/*caret*/
                }
                _ => ()
            }
        }
    """, """
        enum E<T> { A(T), B }

        fn main() {
            match E::A(22) {
                E::A(value) => {
                    println!("{:?}", value);/*caret*/
                }
                _ => ()
            }
        }
    """)

    fun `test match arm with not empty type`() = doTest("""
        enum E<T> { A(T), B }

        fn main() {
            match E::A(22) {
                E::A(value) => value.println/*caret*/
                _ => -1
            };
        }
    """, """
        enum E<T> { A(T), B }

        fn main() {
            match E::A(22) {
                E::A(value) => {
                    println!("{:?}", value);/*caret*/
                    value
                }
                _ => -1
            };
        }
    """)

    fun `test match arm with empty type`() = doTest("""
        enum E<T> { A(T), B }

        fn main() {
            match E::A(22) {
                E::A(value) => value.println/*caret*/
                _ => ()
            };
        }
    """, """
        enum E<T> { A(T), B }

        fn main() {
            match E::A(22) {
                E::A(value) => println!("{:?}", value),/*caret*/
                _ => ()
            };
        }
    """)

    fun `test generic parameter with no Debug`() = doTestNotApplicable("""
        fn test<T>(variable: T) {
            variable.println/*caret*/
        }
    """)

    fun `test generic parameter with Debug + Display`() = doTest("""
        use std::fmt::Debug;
        use std::fmt::Display;

        fn test<T>(variable: T) where T: Debug + Display {
            variable.println/*caret*/
        }
    """, """
        use std::fmt::Debug;
        use std::fmt::Display;

        fn test<T>(variable: T) where T: Debug + Display {
            println!("{:?}", variable);/*caret*/
        }
    """)
}
