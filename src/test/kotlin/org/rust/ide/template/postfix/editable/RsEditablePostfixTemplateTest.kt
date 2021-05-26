/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.template.postfix.editable

import com.intellij.codeInsight.template.postfix.settings.PostfixTemplateStorage
import com.intellij.codeInsight.template.postfix.templates.PostfixLiveTemplate
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate
import com.intellij.codeInsight.template.postfix.templates.editable.PostfixChangedBuiltinTemplate
import com.intellij.openapi.application.runReadAction
import com.intellij.util.containers.ContainerUtil
import org.rust.RsTestBase
import org.rust.cargo.project.model.cargoProjects
import org.rust.ide.template.postfix.AssertPostfixTemplate
import org.rust.ide.template.postfix.RsPostfixTemplateProvider
import org.rust.singleProject
import org.rust.ide.template.postfix.editable.RsPostfixTemplateExpressionCondition as TemplateCondition


class RsEditablePostfixTemplateTest : RsTestBase() {
    private val myProvider: RsPostfixTemplateProvider = RsPostfixTemplateProvider()

    fun `test 'template id' after reload`() {
        val template = RsEditablePostfixTemplate(
            "myId", "myKey", "", "",
            emptySet(), true, myProvider
        )
        assertEquals("myId", reloadRustTemplate(template).id)
    }

    fun `test 'template key' after reload`() {
        val template = RsEditablePostfixTemplate(
            "myId", "myKey", "", "", emptySet(), true, myProvider
        )
        assertEquals(".myKey", reloadRustTemplate(template).key)
    }

    fun `test attribute 'topmost' after reload`() {
        var template = RsEditablePostfixTemplate(
            "myId", "myKey", "", "", emptySet(),
            true, myProvider
        )
        assertTrue(reloadRustTemplate(template).isUseTopmostExpression)
        template = RsEditablePostfixTemplate("myId", "myKey", "", "", emptySet(), false, myProvider)
        assertFalse(reloadRustTemplate(template).isUseTopmostExpression)
    }

    fun `test changed builtin template`() {
        val customTemplate = RsEditablePostfixTemplate(
            "myId", "myKey", "", "", emptySet(), true, myProvider
        )

        val builtinTemplate = AssertPostfixTemplate(myProvider)
        val template = PostfixChangedBuiltinTemplate(customTemplate, builtinTemplate)

        val reloaded = reloadTemplate(template)
        assertInstanceOf(reloaded, PostfixChangedBuiltinTemplate::class.java)
        assertEquals("myId", reloaded.id)
        assertEquals(template.builtinTemplate.id, (reloaded as PostfixChangedBuiltinTemplate).builtinTemplate.id)
        assertTrue(reloaded.isBuiltin())
    }

    fun `test type conditions after reload`() {
        for (type in TemplateCondition.Type.values().filter { it != TemplateCondition.Type.UserEntered }) {
            val condition = TemplateCondition(type)
            assertSameElements(reloadConditions(templateWithCondition(condition)), condition)
        }
    }

    fun `test user entered type name condition after reload`() {
        val condition = TemplateCondition(TemplateCondition.Type.UserEntered, "Test::TypeName")
        val reloadedConditions = reloadConditions(templateWithCondition(condition))
        assertSameElements(reloadedConditions, condition)
    }

    fun `test reload several type conditions at once`() {
        val conditions = TemplateCondition.Type.values().filter { it != TemplateCondition.Type.UserEntered }
            .map { TemplateCondition(it) }.toSet()
        val template = RsEditablePostfixTemplate(
            "myId", "myKey", "", "", conditions, true, myProvider
        )
        assertSameElements(reloadConditions(template), conditions)
    }

    fun `test $EXPR$ variable marks expression position`() = doTest(
        """
            fn main() {
                let x = 0;
                x.key<caret>
            }
        """, """
            fn main() {
                let x = 0;
                x * xx
            }
        """, "key", "\$EXPR\$ * \$EXPR\$\$EXPR\$"
    )

    fun `test $END$ variable marks last caret position`() = doTest(
        """
                fn main() {
                    10.add<caret>
                }
            """, """
                fn main() {
                    (10 + <caret>)
                }
            """, "add", "(\$EXPR\$ + \$END\$)"
    )

    fun `test if let template`() = doTest(
        """
                fn main() {
                    enum MyEnum {A, B(i32), C}
                    let value = MyEnum::B(10);
                    value.myiflet<caret>
                }
            """, """
                fn main() {
                    enum MyEnum {A, B(i32), C}
                    let value = MyEnum::B(10);
                    if let PAT = value {}
                }
            """, "myiflet", "if let \$PAT\$ = \$EXPR\$ {\$END\$}"
    )

    fun `test template without selected type will work with any type`() {
        val values = listOf(
            "true", "123", "\"test string\"", "(1,2,3)", "()", "struct S; S{}", "[1,2,3]",
            "let s: &[i32] = &[1,2,3][..]; s"
        )

        for (v in values)
            checkApplicability(
                true, "key", """
                fn main() {
                    let i = {$v};
                    i.key<caret>
                }
            """, setOf()
            )
    }

    fun `test template with one specific type is not applicable to other types`() {
        val valuesAndTypes = listOf(
            "true" to TemplateCondition(TemplateCondition.Type.Bool),
            "123" to TemplateCondition(TemplateCondition.Type.Number),
            "(1,2,3)" to TemplateCondition(TemplateCondition.Type.Tuple),
            "()" to TemplateCondition(TemplateCondition.Type.Unit),
            "struct S; S{}" to TemplateCondition(TemplateCondition.Type.ADT),
            "[1,2,3]" to TemplateCondition(TemplateCondition.Type.Array),
            "let s: &[i32] = &[1,2,3][..]; s" to TemplateCondition(TemplateCondition.Type.Slice)
        )

        for (a in valuesAndTypes)
            for (b in valuesAndTypes)
                checkApplicability(
                    a == b, "key", """
                    fn main() {
                        let i = {${b.first}};
                        i.key<caret>
                    }
                """, setOf(a.second)
                )
    }

    fun `test template applicable to user entered type`() {
        val type = TemplateCondition(TemplateCondition.Type.UserEntered, "MyEnum")
        checkApplicability(
            true, "key", """
                enum MyEnum { A, B, C }
                fn main() {
                    let e = MyEnum::A;
                    e.key<caret>
                }
            """, setOf(type)
        )
        checkApplicability(
            false, "key", """
                enum MyEnum2 { A, B, C }
                fn main() {
                    let e = MyEnum2::A;
                    e.key<caret>
                }
            """, setOf(type)
        )
    }

    fun `test template user entered type with full path`() {
        val projectName = myFixture.project.cargoProjects.singleProject().presentableName
        val type = TemplateCondition(TemplateCondition.Type.UserEntered, "${projectName}::myMod::MyEnum")
        checkApplicability(
            true, "key", """
                mod myMod { enum MyEnum { A, B, C } }
                enum MyEnum { C, B, A }

                fn main() {
                    let e = myMod::MyEnum::A;
                    e.key<caret>
                }
            """, setOf(type)
        )
        checkApplicability(
            false, "key", """
                mod myMod { enum MyEnum { A, B, C } }
                enum MyEnum { C, B, A }

                fn main() {
                    let e = MyEnum::A;
                    e.key<caret>
                }
            """, setOf(type)
        )
    }

    private fun checkApplicability(
        isApplicable: Boolean, templateName: String, testCase: String,
        conditions: Set<TemplateCondition> = setOf()
    ) {
        InlineFile(testCase).withCaret()
        createAndRegisterTemplate(templateName, "", conditions)

        val result = runReadAction {
            PostfixLiveTemplate.isApplicableTemplate(myProvider, ".$templateName", myFixture.file, myFixture.editor)
        }

        val types = conditions.joinToString { it.presentableName }
        check(result == isApplicable) {
            "custom postfix template ${if (types.isNotEmpty()) "with types: '$types' " else ""} " +
                "${if (isApplicable) "should" else "shouldn't"} be applicable to given case:\n$testCase"
        }
    }

    private fun doTest(
        before: String, after: String, templateName: String, templateText: String,
        conditions: Set<TemplateCondition> = setOf()
    ) {
        createAndRegisterTemplate(templateName, templateText, conditions)
        checkByText(before.trimIndent(), after.trimIndent()) { myFixture.type("\t") }
    }

    private fun templateWithCondition(condition: TemplateCondition): RsEditablePostfixTemplate {
        return RsEditablePostfixTemplate(
            "myId", "myKey", "", "", setOf(condition), true, myProvider
        )
    }

    private fun reloadTemplate(template: PostfixTemplate): PostfixTemplate {
        val saveStorage = PostfixTemplateStorage()
        saveStorage.setTemplates(myProvider, listOf(template))
        val loadStorage = PostfixTemplateStorage.getInstance()
        loadStorage.loadState(saveStorage.state!!)
        return ContainerUtil.getFirstItem(loadStorage.getTemplates(myProvider))
    }

    private fun reloadRustTemplate(template: RsEditablePostfixTemplate): RsEditablePostfixTemplate {
        return reloadTemplate(template) as RsEditablePostfixTemplate
    }

    private fun reloadConditions(template: RsEditablePostfixTemplate): Set<TemplateCondition> {
        return reloadRustTemplate(template).expressionConditions
    }

    private fun createAndRegisterTemplate(
        templateName: String, templateText: String,
        conditions: Set<TemplateCondition> = setOf()
    ) {
        val template = RsEditablePostfixTemplate("myId", templateName, templateText, "", conditions, true, myProvider)
        PostfixTemplateStorage.getInstance().setTemplates(myProvider, setOf(template))
    }
}
