/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.util.ProcessingContext
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.lang.core.psi.ext.returnType
import org.rust.lang.core.types.ty.TyUnit

class RsKeywordCompletionProvider(
    private vararg val keywords: String
) : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext?, result: CompletionResultSet) {
        for (keyword in keywords) {
            var builder = LookupElementBuilder.create(keyword)
            builder = addInsertionHandler(keyword, builder, parameters)
            result.addElement(builder.withPriority(KEYWORD_PRIORITY))
        }
    }
}

fun InsertionContext.addSuffix(suffix: String) {
    document.insertString(selectionEndOffset, suffix)
    EditorModificationUtil.moveCaretRelatively(editor, suffix.length)
}

private val ALWAYS_NEEDS_SPACE = setOf("crate", "const", "enum", "extern", "fn", "impl", "let", "mod", "mut", "pub",
    "static", "struct", "trait", "type", "unsafe", "use", "where")


private fun addInsertionHandler(keyword: String, builder: LookupElementBuilder, parameters: CompletionParameters): LookupElementBuilder {
    val suffix = when (keyword) {
        in ALWAYS_NEEDS_SPACE -> " "
        "return" -> {
            val fn = parameters.position.ancestorStrict<RsFunction>() ?: return builder
            if (fn.returnType !is TyUnit) " " else ";"
        }
        else -> return builder
    }

    return builder.withInsertHandler({ ctx, _ -> ctx.addSuffix(suffix) })
}
