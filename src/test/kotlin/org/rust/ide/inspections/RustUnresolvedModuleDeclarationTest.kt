package org.rust.ide.inspections

class RustUnresolvedModuleDeclarationTest : RustInspectionsTestBase() {
    override val dataPath = "org/rust/ide/inspections/fixtures/unresolved_mod_decl"

    fun testAnnotations() = doTest<RustUnresolvedModuleDeclarationInspection>()

    fun testLocalModuleDeclaration() = doTest<RustUnresolvedModuleDeclarationInspection>()

    fun testCreateFileQuickFix() = checkByDirectory {
        enableInspection<RustUnresolvedModuleDeclarationInspection>()
        openFileInEditor("mod.rs")
        applyQuickFix("Create module file")
    }

    fun testCreateFileAndExpandModuleQuickFix() = checkByDirectory {
        enableInspection<RustUnresolvedModuleDeclarationInspection>()
        openFileInEditor("foo.rs")
        applyQuickFix("Create module file")
    }

}
