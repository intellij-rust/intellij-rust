/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import org.rust.ProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor
import org.rust.ide.colors.RsColor

class RsHighlightingMutableAnnotatorTest : RsAnnotationTestBase() {

    val MUT_PARAMETER = RsColor.MUT_PARAMETER.humanName
    val MUT_BINDING = RsColor.MUT_BINDING.humanName

    fun `test mut self highlight`() = checkInfo("""
        struct <info>Foo</info> {}
        impl <info>Foo</info> {
            fn <info>bar</info>(&mut <info><info descr="$MUT_PARAMETER">self</info></info>) {
                <info descr="$MUT_PARAMETER">self</info>.<info>bar</info>();
            }
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test mut binding`() {
        checkInfo("""
            fn <info descr="null">main</info>() {
                let mut <info descr="$MUT_BINDING">a</info> = 1;
                let b = <info descr="$MUT_BINDING">a</info>;
                let <info descr="null">Some</info>(ref mut <info descr="$MUT_BINDING">c</info>) = <info descr="null">Some</info>(10);
                let <info descr="$MUT_BINDING">d</info> = <info descr="$MUT_BINDING">c</info>;
            }
        """)
    }

    fun `test mut parameter`() = checkInfo("""
        fn <info>test</info>(mut <info><info descr="$MUT_PARAMETER">para</info></info>: <info>i32</info>) {
            let b = <info><info descr="$MUT_PARAMETER">para</info></info>;
        }
    """)
}
