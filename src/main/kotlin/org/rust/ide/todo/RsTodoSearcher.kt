/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.todo

import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.cache.TodoCacheManager
import com.intellij.psi.search.IndexPattern
import com.intellij.psi.search.IndexPatternOccurrence
import com.intellij.psi.search.searches.IndexPatternSearch
import com.intellij.util.Processor
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.RsMacroCall
import org.rust.lang.core.psi.RsRecursiveVisitor
import org.rust.lang.core.psi.ext.endOffset
import org.rust.lang.core.psi.ext.macroName
import org.rust.lang.core.psi.ext.startOffset

class RsTodoSearcher : QueryExecutorBase<IndexPatternOccurrence, IndexPatternSearch.SearchParameters>(true) {

    override fun processQuery(queryParameters: IndexPatternSearch.SearchParameters, consumer: Processor<in IndexPatternOccurrence>) {
        var pattern = queryParameters.pattern
        if (pattern != null && !pattern.isTodoPattern) return
        if (pattern == null) {
            pattern = queryParameters.patternProvider.indexPatterns.firstOrNull(IndexPattern::isTodoPattern) ?: return
        }

        val file = queryParameters.file as? RsFile ?: return

        val cacheManager = TodoCacheManager.SERVICE.getInstance(file.project)
        val patternProvider = queryParameters.patternProvider
        val count = if (patternProvider != null) {
            cacheManager.getTodoCount(file.virtualFile, patternProvider)
        } else {
            cacheManager.getTodoCount(file.virtualFile, pattern)
        }
        if (count == 0) return

        file.accept(object : RsRecursiveVisitor() {
            override fun visitMacroCall(call: RsMacroCall) {
                super.visitMacroCall(call)
                if (call.macroName == "todo") {
                    val startOffset = call.path.referenceNameElement.startOffset
                    val endOffset = call.semicolon?.startOffset ?: call.endOffset
                    val range = TextRange(startOffset, endOffset)
                    consumer.process(RsTodoOccurrence(file, range, pattern))
                }
            }
        })
    }
}

// It's hacky way to check that pattern is used to find TODOs without real computation
val IndexPattern.isTodoPattern: Boolean get() = patternString.contains("TODO", true)

data class RsTodoOccurrence(
    val _file: RsFile,
    val _textRange: TextRange,
    val _pattern: IndexPattern
) : IndexPatternOccurrence {
    override fun getFile(): PsiFile = _file
    override fun getTextRange(): TextRange = _textRange
    override fun getPattern(): IndexPattern = _pattern
}
