package org.rust.ide.annotator

import com.intellij.testFramework.LightProjectDescriptor

class RsRespectsCrateAliasesTest : RsAnnotatorTestBase() {
    override fun getProjectDescriptor(): LightProjectDescriptor = WithStdlibAndDependencyRustProjectDescriptor

    fun testE0428_RespectsCrateAliases() = checkErrors("""
        extern crate dep_lib_target as num_lib;
        mod num {}

        // FIXME: ideally we want to highlight these
        extern crate foo_lib_target;
        mod foo {}
    """)
}
