/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ml

import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.completion.ml.util.RelevanceUtil.asRelevanceMaps
import com.intellij.testFramework.UsefulTestCase
import org.intellij.lang.annotations.Language
import org.rust.ProjectDescriptor
import org.rust.RsTestBase
import org.rust.WithStdlibRustProjectDescriptor
import org.rust.lang.core.psi.RS_KEYWORDS

@Suppress("UnstableApiUsage")
class RsElementFeatureProviderTest : RsTestBase() {
    fun `test top level keyword kind features`() = doTest("ml_rust_kind", """
        /*caret*/
        fn main() {}
    """, mapOf(
        "struct" to RsKeywordMLKind.Struct.name,
        "enum" to RsKeywordMLKind.Enum.name,
        "fn" to RsKeywordMLKind.Fn.name,
        "const" to RsKeywordMLKind.Const.name,
        "pub" to RsKeywordMLKind.Pub.name,
        "extern" to RsKeywordMLKind.Extern.name,
        "trait" to RsKeywordMLKind.Trait.name,
        "type" to RsKeywordMLKind.Type.name,
        "use" to RsKeywordMLKind.Use.name,
        "static" to RsKeywordMLKind.Static.name,
    ))

    fun `test body keyword kind features`() = doTest("ml_rust_kind", """
        fn main() {
            /*caret*/
        }
    """, mapOf(
        "let" to RsKeywordMLKind.Let.name,
        "struct" to RsKeywordMLKind.Struct.name,
        "enum" to RsKeywordMLKind.Enum.name,
        "if" to RsKeywordMLKind.If.name,
        "match" to RsKeywordMLKind.Match.name,
        "return" to RsKeywordMLKind.Return.name,
        "crate" to RsKeywordMLKind.Crate.name,
    ))

    fun `test named elements kind features`() = doTest("ml_rust_kind", """
        fn foo_func() {}
        fn main() {
            let foo_var = 42;
            f/*caret*/
        }
    """, mapOf(
        "foo_var" to RsPsiElementMLKind.PatBinding.name,
        "foo_func" to RsPsiElementMLKind.Function.name,
    ))

    fun `test struct field method kind feature`() = doTest("ml_rust_kind", """
        struct S { field1: i32, field2: i32 }
        impl S {
            fn foo(&self) {}
        }
        fn foo(s: S) {
            s.f/*caret*/
        }
    """, mapOf(
        "field1" to RsPsiElementMLKind.NamedFieldDecl.name,
        "foo" to RsPsiElementMLKind.Function.name,
    ))

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test is_from_stdlib feature`() = doTest("ml_rust_is_from_stdlib", """
        fn my_print() {}
        fn main() {
            prin/*caret*/
        }
    """, mapOf(
        "println" to 1,
        "my_print" to 0,
    ))

    fun `test all keywords are covered`() {
        val kindsKeywords = RsKeywordMLKind.values().map {
            it.lookupString
        }
        val actualKeywords = RS_KEYWORDS.types.map {
            when (val name = it.toString()) {
                "async_kw" -> "async"
                "auto_kw" -> "auto"
                "default_kw" -> "default"
                "dyn_kw" -> "dyn"
                "raw_kw" -> "raw"
                "union_kw" -> "union"
                else -> name
            }
        }
        UsefulTestCase.assertSameElements(kindsKeywords, actualKeywords)
    }


    private fun doTest(feature: String, @Language("Rust") code: String, values: Map<String, Any>) {
        InlineFile(code.trimIndent()).withCaret()
        myFixture.completeBasic()
        val lookup = LookupManager.getInstance(project).activeLookup as LookupImpl
        for ((lookupString, expectedValue) in values) {
            checkFeatureValue(lookup, feature, lookupString, expectedValue)
        }
    }

    private fun checkFeatureValue(
        lookup: LookupImpl,
        feature: String,
        lookupString: String,
        expectedValue: Any
    ) {
        val items = lookup.items
        val allRelevanceObjects = lookup.getRelevanceObjects(items, false)

        val matchedItem = items.firstOrNull { it.lookupString == lookupString } ?: error("No `$lookupString` in lookup")
        val relevanceObjects = allRelevanceObjects[matchedItem].orEmpty()
        val featuresMap = asRelevanceMaps(relevanceObjects).second
        val actualValue = featuresMap[feature]
        assertEquals("Invalid value for `$feature` of `$lookupString`", expectedValue, actualValue)
    }
}
