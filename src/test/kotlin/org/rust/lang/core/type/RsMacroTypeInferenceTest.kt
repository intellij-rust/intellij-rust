/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.type

class RsMacroTypeInferenceTest : RsTypificationTestBase() {
    override val followMacroExpansions: Boolean get() = true

    fun `test let stmt expanded from a macro`() = testExpr("""
        macro_rules! foo { ($ s:stmt) => { $ s }; }
        fn main() {
            foo! {
                let a = 0;
            }
            a;
        } //^ i32
    """)

    fun `test let stmt (no semicolon) expanded from a macro`() = testExpr("""
        macro_rules! foo { ($ s:stmt) => { $ s }; }
        fn main() {
            foo! {
                let a = 0
            }
            a;
        } //^ i32
    """)

    fun `test expr expanded from a macro`() = testExpr("""
        macro_rules! foo { ($ e:expr) => { $ e }; }
        fn main() {
            let a = foo!(0u16);
            a;
        } //^ u16
    """)

    fun `test stmt-context macro with an expression is typified 1`() = testExpr("""
        macro_rules! foo { ($ e:expr) => { $ e }; }
        fn main() {
            foo!(2 + 2);
               //^ i32
            foobar;
        }
    """)

    fun `test stmt-context macro with an expression is typified 2`() = testExpr("""
        macro_rules! foo { ($ e:expr) => { 0; $ e }; }
        fn main() {
            foo!(2 + 2);
               //^ i32
            foobar;
        }
    """)

    fun `test tail expr 1`() = testExpr("""
        macro_rules! foo { ($ s:stmt) => { 0u8; $ s }; }
        fn main() {
            let a = { foo! { 1u16 } };
            a;
        } //^ u16
    """)

    fun `test tail expr 2`() = testExpr("""
        macro_rules! foo { ($ s:stmt) => { 0u8; $ s }; }
        fn main() {
            let a = { foo!(1u16); };
            a;
        } //^ ()
    """)

    fun `test tail expr 3`() = testExpr("""
        macro_rules! foo { ($ s:stmt) => { 0u8; $ s }; }
        fn main() {
            let a = { foo!(foo!(1u16)); };
            a;
        } //^ ()
    """)

    // TODO looks like there are needed changes in tail expr grammar
    fun `test tail expr 4`() = expect<IllegalStateException> {
    testExpr ("""
        macro_rules! foo { ($($ t:tt)*) => { $($ t)* }; }
        macro_rules! bar { ($ s:stmt) => { 0u8; $ s }; }
        fn main() {
            let a = { foo! { bar!(1u16) } };
            a;
        } //^ u16
    """)
    }

    fun `test tail expr 5`() = testExpr("""
        macro_rules! foo { ($($ t:tt)*) => { 0u8; $($ t)* }; }
        fn main() {
            let a = { foo! { foo!(1u16); } };
            a;
        } //^ ()
    """)

    fun `test tail expr 6`() = testExpr("""
        macro_rules! foo { ($ s:expr) => { $ s }; }
        fn main() {
            let a = { foo!(1u16) };
            a;
        } //^ u16
    """)

    fun `test unification`() = testExpr("""
        macro_rules! foo { ($ s:stmt) => { $ s }; }
        fn main() {
            let a = 0;
            foo! { a += 1u8 }
            a;
        } //^ u8
    """)

    // More hygiene tests in RsMacroExpansionResolveTest
    fun `test hygiene`() = testExpr("""
        macro_rules! foo { ($ i:ident) => { { let $ i = 0u8; a } }; }
        fn main() {
            let a = 0u16;
            let b = foo!(a);
            b;
        } //^ <unknown>
    """)
}
