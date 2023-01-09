/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import com.intellij.openapi.options.advanced.AdvancedSettings
import org.rust.ProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor
import org.rust.ide.colors.RsColor

// TODO: std-related tests fails after the rebase on fresh master. why?
class RsDynCallAnnotatorTest : RsAnnotatorTestBase(RsDynCallAnnotator::class) {
    override fun setUp() {
        super.setUp()
        annotationFixture.registerSeverities(listOf(RsColor.DYN_CALL.testSeverity))
        AdvancedSettings.setBoolean("org.rust.dyn.call.highlight", true)
    }

    fun `test no dyn call highlighting for simple static call`() = checkHighlighting("""
        trait T { fn f(&self); }
        struct S;
        impl S { fn f(&self) {} }
        fn main() {
            let a: S = S;
            a.f();
            S::f(&a);
        }
    """, ignoreExtraHighlighting=false)

    fun `test basic dyn call highlighting`() = checkHighlighting("""
        trait T { fn f(&self); }
        impl T for () { fn f(&self) {} }
        fn main() {
            let b: &dyn T = &();
            b.<DYN_CALL descr="Dynamically dispatched method call">f</DYN_CALL>();
            T::<DYN_CALL descr="Dynamically dispatched method call">f</DYN_CALL>(b);
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test dyn call highlighting in a loop`() = checkHighlighting("""
        trait T { fn f(&self); }
        impl T for () { fn f(&self) {} }
        fn main() {
            let n: fn() -> &'static dyn T = || &();
            for r in &[n(), n(), n()] {
                r.<DYN_CALL descr="Dynamically dispatched method call">f</DYN_CALL>();
            }
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test dyn call highlighting with deref`() = checkHighlighting("""
        use std::ops::Deref;
        trait T { fn f(&self); }
        impl T for () { fn f(&self) {} }
        struct Wrapper<T>(T);
        impl<T> Deref for Wrapper<T> {
            type Target = T;
            fn deref(&self) -> &Self::Target { &self.0 }
        }
        fn main() {
            let w1 = Wrapper(&());
            w1.f(); // no highlighting expected here
            let w2: Wrapper<&dyn T> = Wrapper(&());
            w2.<DYN_CALL descr="Dynamically dispatched method call">f</DYN_CALL>();
        }
    """, ignoreExtraHighlighting=false)

    fun `test no dyn call highlighting for impl ref dyn trait`() = checkHighlighting("""
        trait T { fn f(&self); }
        impl T for () { fn f(&self) {} }
        trait A: Sized { fn a(&self) {} }
        impl<'a> A for &'a dyn T1 {
            fn a(&self) {
                println!("<&dyn T1 as A>::a");
            }
        }
        fn main() {
            s1.a(); // it's a static call so no highlighting expected here
        }
    """, ignoreExtraHighlighting=false)

    // TODO: there's a error in the logic of this test that needs to be fixed
    fun `test no dyn call highlighting for impl dyn`() = checkHighlighting("""
        trait T {}
        impl dyn T { fn foo(&self) -> u8 { 0 } }
        struct A;
        impl T for A {}
        fn main() {
            <dyn T>::foo(&A); // it's a static call so  no highlighting expected here
        }
    """, ignoreExtraHighlighting=false)

    // TODO: there's a error in the logic of this test that needs to be somehow fixed.
    fun `test no dyn call highlighting with qualified paths`() = checkHighlighting("""
        trait MyTrait { fn abc(&self) -> bool; }
        impl MyTrait for u8 { fn abc(&self) -> bool { *self == 0 } }
        fn main() {
            let trait_object: &dyn MyTrait = &0_u8;
            let _s2 = trait_object.<DYN_CALL descr="Dynamically dispatched method call">abc</DYN_CALL>();
            let _s3 = MyTrait::<DYN_CALL descr="Dynamically dispatched method call">abc</DYN_CALL>(trait_object);
            <dyn MyTrait>::<DYN_CALL descr="Dynamically dispatched method call">abc</DYN_CALL>(&0_u8); // TODO: must be fixed!

            // no highlighting expected in the following calls since they're static
            0_u8.abc();
            MyTrait::abc(&0_u8);
            <u8 as MyTrait>::abc(&0_u8);
            MyTrait::abc(&0_u8);
        }
    """, ignoreExtraHighlighting=false)

    @BatchMode
    fun `test no highlighting in batch mode`() = checkHighlighting("""
        trait T { fn f(&self); }
        impl T for () { fn f(&self) {} }
        fn main() {
            let t: &dyn T = &();
            t.f();
            T::f(t);
        }
    """, ignoreExtraHighlighting = false)
}
