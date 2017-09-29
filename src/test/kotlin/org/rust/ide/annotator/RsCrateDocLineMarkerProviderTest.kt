/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

class RsCrateDocLineMarkerProviderTest : RsLineMarkerProviderTestBase() {

    override fun getProjectDescriptor() = WithStdlibAndDependencyRustProjectDescriptor

    override fun setUp() {
        super.setUp()
        projectDescriptor.setUp(myFixture)
    }

    fun testDocumentationLink() = doTestByText("""
        #[cfg(not(windows))]
        extern crate dep_lib;             // - Open documentation for `dep_lib`
    """)

    fun testDocumentationLinkWithAlias() = doTestByText("""
        extern crate dep_lib as dlib;     // - Open documentation for `dep_lib`
    """)

    fun testNoDocumentationLink() = doTestByText("""
        extern crate nosrc_lib;           // It's a package name, no documentation
    """)
}
