/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import org.rust.ProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor
import org.rust.ide.colors.RsColor

@ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
class RsImplicitCopyHighlightingAnnotatorTest : RsAnnotatorTestBase(RsImplicitCopyAnnotator::class) {
    override fun setUp() {
        super.setUp()
        annotationFixture.registerSeverities(listOf(RsColor.IMPLICIT_COPY.testSeverity))
    }

    fun `test struct with copy`() = checkHighlighting("""
        #[derive(Copy)]
        struct S;

        fn main() {
            let s = S;
            let x = <IMPLICIT_COPY>s</IMPLICIT_COPY>;
        }
    """)

    fun `test primitive type`() = checkHighlighting("""
        fn main() {
            let s = 0;
            let x = <IMPLICIT_COPY>s</IMPLICIT_COPY>;
        }
    """)

    fun `test struct without copy`() = checkHighlighting("""
        struct S;

        fn main() {
            let s = S;
            let x = s;
        }
    """)
}
