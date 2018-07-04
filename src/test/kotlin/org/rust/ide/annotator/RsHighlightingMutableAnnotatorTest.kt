/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

class RsHighlightingMutableAnnotatorTest : RsAnnotatorTestBase() {

    fun `test mut self highlight`() = checkInfo("""
        struct <info>Foo</info> {}
        impl <info>Foo</info> {
            fn <info>bar</info>(&mut <info><info descr="Mutable parameter">self</info></info>) {
                <info descr="Mutable parameter">self</info>.<info>bar</info>();
            }
        }
    """)

    fun `test mut binding`() = checkInfo("""
        fn <info>main</info>() {
            let mut <info descr="Mutable binding">a</info> = 1;
            let b = <info descr="Mutable binding">a</info>;
            let Some(ref mut <info descr="Mutable binding">c</info>) = Some(10);
            let d = <info descr="Mutable binding">c</info>;
        }
    """)

    fun `test mut parameter`() = checkInfo("""
        fn <info>test</info>(mut <info><info descr="Mutable parameter">para</info></info>: <info>i32</info>) {
            let b = <info><info descr="Mutable parameter">para</info></info>;
        }
    """)
}
