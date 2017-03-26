package org.rust.ide.annotator

import com.intellij.testFramework.LightProjectDescriptor

class RsCrateDocLineMarkerProviderTest : RsLineMarkerProviderTestBase() {

    override fun getProjectDescriptor(): LightProjectDescriptor = WithStdlibAndDependencyRustProjectDescriptor

    fun testDocumentationLink() = doTestByText("""
        #[cfg(not(windows))]
        extern crate dep_lib;             // - Open documentation for `dep_lib`
    """)

    fun testDocumentationLinkWithAlias() = doTestByText("""
        extern crate dep_lib as dlib;     // - Open documentation for `dep_lib`
    """)

    fun testNoDocumentationLink() = doTestByText("""
        extern crate dep_nosrc_lib;       // It's a package name, no documentation
    """)
}
