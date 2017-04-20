package org.rust.ide.annotator

import com.intellij.testFramework.LightProjectDescriptor

class RsUnknownCrateAnnotationTest : RsAnnotatorTestBase() {
    override fun getProjectDescriptor(): LightProjectDescriptor = WithStdlibAndDependencyRustProjectDescriptor

    fun testInvalidCrate() = checkErrors("""
        <error descr="Can't find crate for `litarvan` [E0463]">extern crate litarvan;</error>
    """)
}
