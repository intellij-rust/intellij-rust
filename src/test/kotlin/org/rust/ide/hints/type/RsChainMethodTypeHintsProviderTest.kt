/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.hints.type

import com.intellij.codeInsight.hints.InlayHintsSettings
import org.intellij.lang.annotations.Language
import org.rust.ProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor
import org.rust.lang.RsLanguage

class RsChainMethodTypeHintsProviderTest : RsInlayTypeHintsTestBase(RsChainMethodTypeHintsProvider::class) {
    private val types = """
        struct A;
        struct B;

        impl A {
            fn clone(&self) -> A { A }
            fn change(&self) -> B { B }
        }

        impl B {
            fn clone(&self) -> B { B }
            fn change(&self) -> A { A }
        }
    """

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test result and option`() = doTest("""
        fn main() {
            let foo: Result<Option<i32>, &str> = Ok(Some(10i32));
            foo
              .map_err(|_| 0_u32)/*hint text="[:  [Result [< [[Option [< i32 >]] ,  u32] >]]]"*/
              .unwrap()/*hint text="[:  [Option [< i32 >]]]"*/
              .and_then(|_| Some("foo"))/*hint text="[:  [Option [< [& str] >]]]"*/
              .unwrap();
        }
    """)

    fun `test show only last type on a line`() = doTest("""
        $types

        fn main() {
            let foo = A;
            foo
              .clone()/*hint text="[:  A]"*/
              .clone().change()/*hint text="[:  B]"*/
              .clone();
        }
    """)

    fun `test show only last type on a line with comment`() = doTest("""
        $types

        fn main() {
            let foo = A;
            foo
              .change()/**/.change()/*hint text="[:  A]"*/
              .change() .change()/*hint text="[:  A]"*//**/
              .change().change()/*hint text="[:  A]"*/ /**/
              .change().change()/*hint text="[:  A]"*///
              .clone();
        }
    """, showSameConsecutiveTypes = true)

    fun `test respect last type on a previous line`() = doTest("""
        $types

        fn main() {
            let foo = A;
            foo
              .clone()/*hint text="[:  A]"*/
              .change().change()
              .change()/*hint text="[:  B]"*/
              .change();
        }
    """, showSameConsecutiveTypes = false)

    fun `test ignore repeated types`() = doTest("""
        $types

        fn main() {
            let foo = A;
            foo
              .clone()/*hint text="[:  A]"*/
              .clone()
              .change()/*hint text="[:  B]"*/
              .clone()
              .change();
        }
    """, showSameConsecutiveTypes = false)

    fun `test do not show hints for a single method call`() = doTest("""
        struct S;
        impl S {
            fn foo(): u32 { 0 }
        }

        fn main() {
            let s = S;
            s.foo();
        }
    """)

    fun `test do not show hints for unknown type`() = doTest("""
        fn main() {
            let s = S;
            s.bar()
             .foo();
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test iterator special case`() = doTest("""
        fn main() {
            vec![1, 2, 3]
                .into_iter()/*hint text="[:  [impl  [Iterator [< [Item = i32] >]] ]]"*/
                .map(|x| x as u8)/*hint text="[:  [impl  [Iterator [< [Item = u8] >]] ]]"*/
                .map(|x| x as u16)/*hint text="[:  [impl  [Iterator [< [Item = u16] >]] ]]"*/
                .filter(|x| x % 2 == 0)/*hint text="[:  [impl  [Iterator [< [Item = u16] >]] ]]"*/
                .for_each(|x| {})
            ;
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test iterator consecutive types`() = doTest("""
        struct S<T>(T);

        impl<T> std::iter::Iterator for S<T> {
            type Item = ();

            fn next(&self) -> Option<Self::Item> { None }
        }

        impl<T> S<T> {
            fn foo(self) -> S<Self> { unreachable!(); }
        }

        fn main() {
            S(0)
                .foo()/*hint text="[:  [impl  [Iterator [< [Item = ()] >]] ]]"*/
                .foo()
                .foo()
                .foo();
            ;
        }
    """, showSameConsecutiveTypes = false)

    @Suppress("UnstableApiUsage")
    private fun doTest(@Language("Rust") code: String, showSameConsecutiveTypes: Boolean = true) {
        val service = InlayHintsSettings.instance()
        val key = RsChainMethodTypeHintsProviderBase.KEY
        val settings = RsChainMethodTypeHintsProviderBase.Settings(showSameConsecutiveTypes = showSameConsecutiveTypes)
        val originalSettings = service.findSettings(key, RsLanguage) { settings }
        try {
            service.storeSettings(key, RsLanguage, settings)
            checkByText(code)
        } finally {
            service.storeSettings(key, RsLanguage, originalSettings)
        }
    }
}
