/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import org.rust.ProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor
import org.rust.ide.colors.RsColor.MUT_BINDING
import org.rust.ide.colors.RsColor.MUT_PARAMETER

class RsHighlightingMutableAnnotatorTest : RsAnnotatorTestBase(RsHighlightingMutableAnnotator::class) {

    override fun setUp() {
        super.setUp()
        annotationFixture.registerSeverities(listOf(MUT_BINDING.testSeverity, MUT_PARAMETER.testSeverity))
    }

    fun `test mut self highlight`() = checkHighlighting("""
        struct Foo {}
        impl Foo {
            fn bar(&mut <MUT_PARAMETER>self</MUT_PARAMETER>) {
                <MUT_PARAMETER>self</MUT_PARAMETER>.bar();
            }
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test mut binding`() = checkHighlighting("""
        fn main() {
            let mut <MUT_BINDING>a</MUT_BINDING> = 1;
            let b = <MUT_BINDING>a</MUT_BINDING>;
            let Some(ref mut <MUT_BINDING>c</MUT_BINDING>) = Some(10);
            let <MUT_BINDING>d</MUT_BINDING> = <MUT_BINDING>c</MUT_BINDING>;
        }
    """)

    fun `test mut parameter`() = checkHighlighting("""
        fn test(mut <MUT_PARAMETER>para</MUT_PARAMETER>: i32) {
            let b = <MUT_PARAMETER>para</MUT_PARAMETER>;
        }
    """)
}
