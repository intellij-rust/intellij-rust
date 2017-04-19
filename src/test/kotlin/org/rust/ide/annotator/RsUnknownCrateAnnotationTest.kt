package org.rust.ide.annotator

import com.intellij.testFramework.LightProjectDescriptor

class RsUnknownCrateAnnotationTest : RsAnnotatorTestBase() {
    override fun getProjectDescriptor(): LightProjectDescriptor = WithStdlibAndDependencyRustProjectDescriptor

    fun testValidCrate() = checkErrors("""
        extern crate dep_lib_target;
    """)

    fun testInvalidCrate() = checkErrors("""
        <error descr="Unknown crate 'litarvan' [E0463]">extern crate litarvan;</error>
    """)
}
