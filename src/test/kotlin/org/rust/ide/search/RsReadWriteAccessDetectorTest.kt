/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.search

import com.intellij.codeInsight.highlighting.ReadWriteAccessDetector
import org.intellij.lang.annotations.Language
import org.rust.ProjectDescriptor
import org.rust.RsTestBase
import org.rust.WithStdlibRustProjectDescriptor
import org.rust.lang.core.psi.ext.RsReferenceElement

class RsReadWriteAccessDetectorTest : RsTestBase() {
    fun `test read assignment`() = doTest("""
        fn main() {
            let a = 0;
            let b = a;
        }         //^ read
    """)

    fun `test write assignment`() = doTest("""
        fn main() {
            let mut a = 0;
            a = 1;
        } //^ write
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test read assignment add`() = doTest("""
        fn main() {
            let a = 0;
            let mut b = 0;
            b += a;
        }      //^ read
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test read_write assignment add`() = doTest("""
        fn main() {
            let mut a = 0;
            a += 1;
        } //^ read_write
    """)

    fun `test deref read assignment`() = doTest("""
        fn main() {
            let a = 0;
            let b = &a;
            let c = *b;
        }          //^ read
    """)

    fun `test deref write assignment`() = doTest("""
        fn main() {
            let mut a = 0;
            let b = &mut a;
            *b = 1;
        }  //^ write
    """)

    fun `test field read assignment`() = doTest("""
        struct S { field: i32 }
        fn foo(s: S) {
            let a = s.field;
        }           //^ read
    """)

    fun `test field write assignment`() = doTest("""
        struct S { field: i32 }
        fn foo(s: &mut S) {
            s.field = 1;
        }   //^ write
    """)

    fun `test struct literal`() = doTest("""
        struct S { field: i32 }
        fn main() {
            let s = S { field: 0 };
        }             //^ write
    """)

    private fun doTest(@Language("Rust") code: String) {
        InlineFile(code.trimIndent())
        val (element, data) = findElementAndDataInEditor<RsReferenceElement>()
        val reference = element.reference ?: error("Failed to get reference for `${element.text}`")
        val definition = reference.resolve() ?: error("Element not resolved")
        val detector = RsReadWriteAccessDetector()
        check(detector.isReadWriteAccessible(definition))

        val expectedAccess = when (data) {
            "read" -> ReadWriteAccessDetector.Access.Read
            "write" -> ReadWriteAccessDetector.Access.Write
            "read_write" -> ReadWriteAccessDetector.Access.ReadWrite
            else -> error("Unknown access type: $data")
        }
        val actualAccess = detector.getReferenceAccess(definition, reference)
        assertEquals(expectedAccess, actualAccess)
    }
}
