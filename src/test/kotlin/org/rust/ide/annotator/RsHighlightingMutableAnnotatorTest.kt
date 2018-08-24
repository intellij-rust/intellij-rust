/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import org.rust.ProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor

class RsHighlightingMutableAnnotatorTest : RsAnnotationTestBase() {
    fun `test mut self highlight`() = checkInfo("""
        struct <info>Foo</info> {}
        impl <info>Foo</info> {
            fn <info>bar</info>(&mut <info><info descr="Mutable parameter">self</info></info>) {
                <info descr="Mutable parameter">self</info>.<info>bar</info>();
            }
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test mut binding`() = checkInfo("""
        fn <info descr="null">main</info>() {
            let mut <info descr="Mutable binding">a</info> = 1;
            let b = <info descr="Mutable binding">a</info>;
            let <info descr="null">Some</info>(ref mut <info descr="Mutable binding">c</info>) = <info descr="null">Some</info>(10);
            let <info descr="Mutable binding">d</info> = <info descr="Mutable binding">c</info>;
        }
    """)

    fun `test mut parameter`() = checkInfo("""
        fn <info>test</info>(mut <info><info descr="Mutable parameter">para</info></info>: <info>i32</info>) {
            let b = <info><info descr="Mutable parameter">para</info></info>;
        }
    """)
}
