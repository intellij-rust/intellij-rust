/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import junit.framework.ComparisonFailure
import org.intellij.lang.annotations.Language
import org.rust.ProjectDescriptor
import org.rust.RsTestBase
import org.rust.WithStdlibRustProjectDescriptor
import org.rust.lang.core.psi.RsMacroCall
import org.rust.lang.core.psi.ext.*
import java.util.*

class RsMacroGraphWalkerTest : RsTestBase() {
    private fun check(@Language("Rust") code: String, expected: HashSet<FragmentKind>) {
        InlineFile(code)
        val macroCall = myFixture.file.descendantsOfType<RsMacroCall>().single()
        val macro = macroCall.resolveToMacro()!!
        val graph = macro.graph!!

        val position = myFixture.file.findElementAt(myFixture.caretOffset - 1)!!
        val bodyTextRange = macroCall.bodyTextRange!!
        val offset = position.endOffset - bodyTextRange.startOffset

        val walker = MacroGraphWalker(myFixture.project, graph, macroCall.macroBody!!, offset)
        val actual = walker.run().map { it.kind }.toSet()
        check(actual == expected) { throw ComparisonFailure("Comparision failed", expected.toString(), actual.toString()) }
    }

    fun `test simple`() = check("""
        macro_rules! my_macro {
            ($ e:expr) => (1);
            ($ i:ident) => (1);
        }

        fn main() {
            my_macro!(x/*caret*/);
        }
    """, hashSetOf(FragmentKind.Expr, FragmentKind.Ident))

    fun `test complex expr`() = check("""
        macro_rules! my_macro {
            ($ e:expr) => (1);
        }

        fn main() {
            my_macro!(x * (y.a/*caret*/ - y.b) * z);
        }
    """, hashSetOf(FragmentKind.Expr))

    fun `test ident repetition *`() = check("""
        macro_rules! my_macro {
            ($ ($ id:ident)* $ t:ty) => (1);
        }

        fn main() {
            my_macro!(x y z/*caret*/);
        }
    """, hashSetOf(FragmentKind.Ident, FragmentKind.Ty))

    fun `test ident repetition +`() = check("""
        macro_rules! my_macro {
            ($ ($ id:ident),+ ) => (1);
        }

        fn main() {
            my_macro!(x, y/*caret*/);
        }
    """, hashSetOf(FragmentKind.Ident))

    fun `test ident repetition ?`() = check("""
        macro_rules! my_macro {
            ($ ($ id:ident)? $ t:ty) => (1);
        }

        fn main() {
            my_macro!(x y/*caret*/);
        }
    """, hashSetOf(FragmentKind.Ty))

    fun `test parens`() = check("""
        macro_rules! my_macro {
            ($ i:ident($ t:ty)) => (1);
        }
        fn main() {
            my_macro!(foo(i/*caret*/));
        }
    """, hashSetOf(FragmentKind.Ty))

    fun `test many rules 1`() = check("""
        macro_rules! my_macro {
            ($ e:expr) => (1);
            ($ ($ id:ident)+ ) => (2);
            ($ x:expr, $ y:ident) => (2);
            ($ x:literal, $ y:expr) => (4);
        }

        fn main() {
            my_macro!(5, 5+7, i/*caret*/);
        }
    """, hashSetOf())

    fun `test nom method 1`() = check("""
        macro_rules! method {
            ($ name:ident<$ a:ty>( $ i:ty ) -> $ o:ty, $ self_:ident, $ submac:ident!( $ ($ args:tt)* )) => (1);
            ($ name:ident<$ a:ty,$ i:ty,$ o:ty,$ e:ty>, $ self_:ident, $ submac:ident!( $ ($ args:tt)* )) => (1);
            ($ name:ident<$ a:ty,$ i:ty,$ o:ty>, $ self_:ident, $ submac:ident!( $ ($ args:tt)* )) => (1);
            ($ name:ident<$ a:ty,$ o:ty>, $ self_:ident, $ submac:ident!( $ ($ args:tt)* )) => (1);
            ($ name:ident<$ a:ty>, $ self_:ident, $ submac:ident!( $ ($ args:tt)* )) => (1);
            (pub $ name:ident<$ a:ty>( $ i:ty ) -> $ o:ty, $ self_:ident, $ submac:ident!( $ ($ args:tt)* )) => (1);
            (pub $ name:ident<$ a:ty,$ i:ty,$ o:ty,$ e:ty>, $ self_:ident, $ submac:ident!( $ ($ args:tt)* )) => (1);
            (pub $ name:ident<$ a:ty,$ i:ty,$ o:ty>, $ self_:ident, $ submac:ident!( $ ($ args:tt)* )) => (1);
            (pub $ name:ident<$ a:ty,$ o:ty>, $ self_:ident, $ submac:ident!( $ ($ args:tt)* )) => (1);
            (pub $ name:ident<$ a:ty>, $ self_:ident, $ submac:ident!( $ ($ args:tt)* )) => (1);
            ($ name:ident<$ a:ty>( $ i:ty ) -> $ o:ty, mut $ self_:ident, $ submac:ident!( $ ($ args:tt)* )) => (1);
            ($ name:ident<$ a:ty,$ i:ty,$ o:ty,$ e:ty>, mut $ self_:ident, $ submac:ident!( $ ($ args:tt)* )) => (1);
            ($ name:ident<$ a:ty,$ i:ty,$ o:ty>, mut $ self_:ident, $ submac:ident!( $ ($ args:tt)* )) => (1);
            ($ name:ident<$ a:ty,$ o:ty>, mut $ self_:ident, $ submac:ident!( $ ($ args:tt)* )) => (1);
            ($ name:ident<$ a:ty>, mut $ self_:ident, $ submac:ident!( $ ($ args:tt)* )) => (1);
            (pub $ name:ident<$ a:ty>( $ i:ty ) -> $ o:ty, mut $ self_:ident, $ submac:ident!( $ ($ args:tt)* )) => (1);
            (pub $ name:ident<$ a:ty,$ i:ty,$ o:ty,$ e:ty>, mut $ self_:ident, $ submac:ident!( $ ($ args:tt)* )) => (1);
            (pub $ name:ident<$ a:ty,$ i:ty,$ o:ty>, mut $ self_:ident, $ submac:ident!( $ ($ args:tt)* )) => (1);
            (pub $ name:ident<$ a:ty,$ o:ty>, mut $ self_:ident, $ submac:ident!( $ ($ args:tt)* )) => (1);
            (pub $ name:ident<$ a:ty>, mut $ self_:ident, $ submac:ident!( $ ($ args:tt)* )) => (1);
        }

        fn main() {
            method!(pub foo<MyType, u8, i/*caret*/);
        }
    """, hashSetOf(FragmentKind.Ty))

    fun `test nom method 2`() = check("""
        macro_rules! method {
            ($ name:ident<$ a:ty>( $ i:ty ) -> $ o:ty, $ self_:ident, $ submac:ident!( $ ($ args:tt)* )) => (1);
            ($ name:ident<$ a:ty,$ i:ty,$ o:ty,$ e:ty>, $ self_:ident, $ submac:ident!( $ ($ args:tt)* )) => (1);
            ($ name:ident<$ a:ty,$ i:ty,$ o:ty>, $ self_:ident, $ submac:ident!( $ ($ args:tt)* )) => (1);
            ($ name:ident<$ a:ty,$ o:ty>, $ self_:ident, $ submac:ident!( $ ($ args:tt)* )) => (1);
            ($ name:ident<$ a:ty>, $ self_:ident, $ submac:ident!( $ ($ args:tt)* )) => (1);
            (pub $ name:ident<$ a:ty>( $ i:ty ) -> $ o:ty, $ self_:ident, $ submac:ident!( $ ($ args:tt)* )) => (1);
            (pub $ name:ident<$ a:ty,$ i:ty,$ o:ty,$ e:ty>, $ self_:ident, $ submac:ident!( $ ($ args:tt)* )) => (1);
            (pub $ name:ident<$ a:ty,$ i:ty,$ o:ty>, $ self_:ident, $ submac:ident!( $ ($ args:tt)* )) => (1);
            (pub $ name:ident<$ a:ty,$ o:ty>, $ self_:ident, $ submac:ident!( $ ($ args:tt)* )) => (1);
            (pub $ name:ident<$ a:ty>, $ self_:ident, $ submac:ident!( $ ($ args:tt)* )) => (1);
            ($ name:ident<$ a:ty>( $ i:ty ) -> $ o:ty, mut $ self_:ident, $ submac:ident!( $ ($ args:tt)* )) => (1);
            ($ name:ident<$ a:ty,$ i:ty,$ o:ty,$ e:ty>, mut $ self_:ident, $ submac:ident!( $ ($ args:tt)* )) => (1);
            ($ name:ident<$ a:ty,$ i:ty,$ o:ty>, mut $ self_:ident, $ submac:ident!( $ ($ args:tt)* )) => (1);
            ($ name:ident<$ a:ty,$ o:ty>, mut $ self_:ident, $ submac:ident!( $ ($ args:tt)* )) => (1);
            ($ name:ident<$ a:ty>, mut $ self_:ident, $ submac:ident!( $ ($ args:tt)* )) => (1);
            (pub $ name:ident<$ a:ty>( $ i:ty ) -> $ o:ty, mut $ self_:ident, $ submac:ident!( $ ($ args:tt)* )) => (1);
            (pub $ name:ident<$ a:ty,$ i:ty,$ o:ty,$ e:ty>, mut $ self_:ident, $ submac:ident!( $ ($ args:tt)* )) => (1);
            (pub $ name:ident<$ a:ty,$ i:ty,$ o:ty>, mut $ self_:ident, $ submac:ident!( $ ($ args:tt)* )) => (1);
            (pub $ name:ident<$ a:ty,$ o:ty>, mut $ self_:ident, $ submac:ident!( $ ($ args:tt)* )) => (1);
            (pub $ name:ident<$ a:ty>, mut $ self_:ident, $ submac:ident!( $ ($ args:tt)* )) => (1);
        }

        fn main() {
            method!(foo<i/*caret*/>, bar, baz!(a));
        }
    """, hashSetOf(FragmentKind.Ty))

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test thread_local 1`() = check("""
        fn main() {
            thread_local! {
               pub static FOO: RefCell<i/*caret*/> = RefCell::new(1);
            }
        }
    """, hashSetOf(FragmentKind.Ty))

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test thread_local 2`() = check("""
        fn main() {
            thread_local! {
               pub static FOO: RefCell<i32> = RefCell::new(i/*caret*/);
            }
        }
    """, hashSetOf(FragmentKind.Expr))

    fun `test cond reduce`() = check("""
        macro_rules! cond_reduce {
            ($ i:expr, $ cond:expr, $ submac:ident!( $($ args:tt)* )) => (1);
            ($ i:expr, $ cond:expr) => (1);
            ($ i:expr, $ cond:expr, $ f:expr) => (1);
        }

        fn main() {
            cond_reduce!(foo, foo.bar(i/*caret*/), foobar!(a b c));
        }
    """, hashSetOf(FragmentKind.Expr))

    fun `test collapsed tokens`() = check("""
        macro_rules! my_macro {
            (&& $ i:ident) => (1);
        }

        fn main() {
            my_macro!(&& foo/*caret*/);
        }
    """, hashSetOf(FragmentKind.Ident))
}
