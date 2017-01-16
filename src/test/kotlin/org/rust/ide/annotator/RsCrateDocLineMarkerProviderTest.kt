package org.rust.ide.annotator

import com.intellij.testFramework.LightProjectDescriptor

class RsCrateDocLineMarkerProviderTest : RsLineMarkerProviderTestBase() {

    override fun getProjectDescriptor(): LightProjectDescriptor = WithStdlibAndDependencyRustProjectDescriptor

    fun testDocumentationLink() = doTestByText("""
        #[cfg(not(windows))]
        extern crate trans_lib;             // - Open documentation
    """)

    fun testDocumentationLinkWithAlias() = doTestByText("""
        extern crate trans_lib as tlib;     // - Open documentation
    """)

    fun testNoDocumentationLink() = doTestByText("""
        extern crate dep_lib;               // It's a package name, no documentation
    """)
}
