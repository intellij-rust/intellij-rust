package org.rust.lang.core.parser

import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.DebugUtil
import com.intellij.testFramework.ParsingTestCase
import org.junit.Assert
import org.rust.lang.core.RustParserDefinition
import java.io.File

public class RustParserCtrsTestCase : ParsingTestCase("ctrs", ".rs", RustParserDefinition()) {

    override fun getTestDataPath() = "testData"

    public fun hasError(file: PsiFile): Boolean {
        var hasErrors = false
        file.accept(object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement?) {
                if (element is PsiErrorElement) {
                    hasErrors = true
                    return
                }
                element!!.acceptChildren(this)
            }
        })
        return hasErrors
    }

    fun testCtrs() {
        FileUtil.visitFiles(File(myFullDataPath, "test"), {
            if (it.isFile && it.extension == myFileExt.trimStart('.')) {
                val text = FileUtil.loadFile(it)
                val psi = createPsiFile(it.name, text)
                val expectedError = expectedErrors.contains(it.path)
                val messageTail = "in ${it.path}:\n\n" +
                        "$text\n\n" +
                        "${DebugUtil.psiToString(psi, true)}"
                if (hasError(psi) ) {
                    Assert.assertTrue("New error " + messageTail, expectedError);
                } else {
                    Assert.assertFalse("No error " + messageTail, expectedError);
                }
            }
            true
        })
    }

    private val expectedErrors = setOf(
            "testData/ctrs/test/1.1.0/doc/doc_trpl_macros_md_0011.rs",
            "testData/ctrs/test/1.1.0/doc/doc_reference_md_0054.rs",
            "testData/ctrs/test/1.1.0/doc/doc_trpl_documentation_md_0052.rs",
            "testData/ctrs/test/1.1.0/doc/doc_trpl_patterns_md_0005.rs",
            "testData/ctrs/test/1.1.0/doc/doc_trpl_macros_md_0021.rs",
            "testData/ctrs/test/1.1.0/run-pass/issue-22463.rs",
            "testData/ctrs/test/1.1.0/run-pass/utf8-bom.rs",
            "testData/ctrs/test/1.1.0/run-pass/macro-interpolation.rs",
            "testData/ctrs/test/1.1.0/run-pass/trait-impl-2.rs",
            "testData/ctrs/test/1.1.0/run-pass/macro-method-issue-4621.rs",
            "testData/ctrs/test/1.1.0/run-pass/match-arm-statics.rs",
            "testData/ctrs/test/1.1.0/run-pass/pub-method-inside-macro.rs",
            "testData/ctrs/test/1.1.0/run-pass/concat.rs",
            "testData/ctrs/test/1.1.0/run-pass/issue-10853.rs",
            "testData/ctrs/test/1.1.0/run-pass/issue-20055-box-trait.rs",
            "testData/ctrs/test/1.1.0/run-pass/issue-24313.rs",
            "testData/ctrs/test/1.1.0/run-pass/issue-20055-box-unsized-array.rs",
            "testData/ctrs/test/1.1.0/run-pass/issue-21350.rs",
            "testData/ctrs/test/1.1.0/run-pass/ranges-precedence.rs",
            "testData/ctrs/test/1.1.0/run-pass/mut-in-ident-patterns.rs",
            "testData/ctrs/test/1.1.0/run-pass/small-enums-with-fields.rs",
            "testData/ctrs/test/1.1.0/run-pass/issue-15221.rs",
            "testData/ctrs/test/1.1.0/run-pass/vec-macro-rvalue-scope.rs",
            "testData/ctrs/test/1.1.0/run-pass/struct-lit-functional-no-fields.rs",
            "testData/ctrs/test/1.1.0/run-pass/issue-20616.rs",
            "testData/ctrs/test/1.1.0/run-pass/issue-8391.rs",
            "testData/ctrs/test/1.1.0/run-pass/macro-of-higher-order.rs",
            "testData/ctrs/test/1.1.0/run-pass/match-pattern-bindings.rs",
            "testData/ctrs/test/1.1.0/run-pass/macro-pat.rs",
            "testData/ctrs/test/1.1.0/run-pass/string-escapes.rs",
            "testData/ctrs/test/1.1.0/run-pass/issue-8851.rs",
            "testData/ctrs/test/1.1.0/doc-std/libstd_io_prelude_rs_0000.rs",
            "testData/ctrs/test/1.1.0/doc-std/libstd_thread_mod_rs_0003.rs",
            "testData/ctrs/test/1.1.0/doc-std/libstd_thread_local_rs_0000.rs",
            "testData/ctrs/test/1.1.0/doc-std/libstd_path_rs_0001.rs",
            "testData/ctrs/test/1.1.0/doc-core/libcore_macros_rs_0006.rs",
            "testData/ctrs/test/1.1.0/doc-core/libcore_macros_rs_0004.rs",
            "testData/ctrs/test/1.1.0/doc-collections/libcollections_fmt_rs_0011.rs",
            "testData/ctrs/test/1.2.0/doc/doc_trpl_documentation_md_0051.rs",
            "testData/ctrs/test/1.2.0/run-pass/macro-with-braces-in-expr-position.rs",
            "testData/ctrs/test/1.2.0/run-pass/macro-pat-follow.rs",
            "testData/ctrs/test/1.2.0/run-pass/ranges-precedence.rs",
            "testData/ctrs/test/1.2.0/run-pass/string-escapes.rs",
            "testData/ctrs/test/1.2.0/rust-rosetta/longest_common_subsequence.rs",
            "testData/ctrs/test/1.3.0/run-pass/down-with-thread-dtors.rs",
            "testData/ctrs/test/1.3.0/doc-core/libcore_mem_rs_0011.rs"
    )
}


