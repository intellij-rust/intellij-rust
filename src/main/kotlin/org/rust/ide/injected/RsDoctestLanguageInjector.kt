/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.injected

import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import com.intellij.util.text.CharArrayUtil
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.cargo.util.AutoInjectedCrates
import org.rust.lang.RsLanguage
import org.rust.lang.core.psi.RS_DOC_COMMENTS
import org.rust.lang.core.psi.RsDocCommentImpl
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.ext.*
import org.rust.lang.doc.psi.RsDocKind
import org.rust.openapiext.toPsiFile
import org.rust.stdext.nextOrNull
import java.util.regex.Pattern

// See https://github.com/rust-lang/rust/blob/5182cc1c/src/librustdoc/html/markdown.rs#L646
private val LANG_SPLIT_REGEX = Pattern.compile("[^\\w-]+", Pattern.UNICODE_CHARACTER_CLASS)
private val RUST_LANG_ALIASES = listOf(
    "rust",
    "allow_fail",
    "should_panic",
    "no_run",
    "test_harness",
//    "compile_fail", // don't highlight code that is expected to contain errors
    "edition2018",
    "edition2015"
)

class RsDoctestLanguageInjector : MultiHostInjector {
    private data class CodeRange(val start: Int, val end: Int, val codeStart: Int) {
        fun isCodeNotEmpty(): Boolean = codeStart + 1 < end

        val indent: Int = codeStart - start

        fun offsetIndent(indent: Int): CodeRange? =
            if (start + indent < end) CodeRange(start + indent, end, codeStart) else null
    }

    override fun elementsToInjectIn(): List<Class<out PsiElement>> =
        listOf(RsDocCommentImpl::class.java)

    override fun getLanguagesToInject(registrar: MultiHostRegistrar, context: PsiElement) {
        if (context !is RsDocCommentImpl) return
        if (!context.isValidHost || context.elementType !in RS_DOC_COMMENTS) return
        if (!context.project.rustSettings.doctestInjectionEnabled) return

        val rsElement = context.ancestorStrict<RsElement>() ?: return
        val cargoTarget = rsElement.containingCargoTarget ?: return
        if (!cargoTarget.isLib) return // only library targets can have doctests
        val crateName = cargoTarget.normName
        val text = context.text

        findDoctestInjectableRanges(text, context.elementType).map { ranges ->
            ranges.map {
                CodeRange(
                    it.startOffset,
                    it.endOffset,
                    CharArrayUtil.shiftForward(text, it.startOffset, it.endOffset, " \t")
                )
            }
        }.map { ranges ->
            val commonIndent = ranges.filter { it.isCodeNotEmpty() }.map { it.indent }.min()
            val indentedRanges = if (commonIndent != null) ranges.mapNotNull { it.offsetIndent(commonIndent) } else ranges

            indentedRanges.map { (start, end, codeStart) ->
                // `cargo doc` has special rules for code lines which start with `#`:
                //   * `# ` prefix is used to mark lines that should be skipped in rendered documentation.
                //   * `##` prefix is converted to `#`
                // See https://github.com/rust-lang/rust/blob/5182cc1c/src/librustdoc/html/markdown.rs#L114
                when {
                    text.startsWith("##", codeStart) -> TextRange(codeStart + 1, end)
                    text.startsWith("# ", codeStart) -> TextRange(codeStart + 2, end)
                    text.startsWith("#\n", codeStart) -> TextRange(codeStart + 1, end)
                    else -> TextRange(start, end)
                }
            }
        }.forEach { ranges ->
            val inj = registrar.startInjecting(RsLanguage)

            ranges.forEachIndexed { index, range ->
                val isFirstIteration = index == 0
                val isLastIteration = index == ranges.size - 1

                val prefix = if (isFirstIteration) {
                    buildString {
                        // Yes, we want to skip the only "std" crate. Not core/alloc/etc, the "std" only
                        val isStdCrate = crateName == AutoInjectedCrates.STD &&
                            cargoTarget.pkg.origin == PackageOrigin.STDLIB
                        if (!isStdCrate) {
                            append("extern crate ")
                            append(crateName)
                            append("; ")
                        }
                        append("fn main() {")
                    }
                } else {
                    null
                }
                val suffix = if (isLastIteration) "}" else null

                inj.addPlace(prefix, suffix, context, range)
            }

            inj.doneInjecting()
        }
    }
}

fun findDoctestInjectableRanges(comment: RsDocCommentImpl): Sequence<List<TextRange>> =
    findDoctestInjectableRanges(comment.text, comment.elementType)

private fun findDoctestInjectableRanges(text: String, elementType: IElementType): Sequence<List<TextRange>> {
    // TODO use markdown parser
    val tripleBacktickIndices = text.indicesOf("```").toList()
    if (tripleBacktickIndices.size < 2) return emptySequence() // no code blocks in the comment

    val infix = RsDocKind.of(elementType).infix

    return tripleBacktickIndices.asSequence().chunked(2).mapNotNull { idx ->
        // Contains code lines inside backticks including `///` at the start and `\n` at the end.
        // It doesn't contain the last line with /// ```
        val lines = run {
            val codeBlockStart = idx[0] + 3 // skip ```
            val codeBlockEnd = idx.getOrNull(1) ?: return@mapNotNull null
            generateSequence(codeBlockStart) { text.indexOf("\n", it) + 1 }
                .takeWhile { it != 0 && it < codeBlockEnd }
                .zipWithNext()
                .iterator()
        }

        // ```rust, should_panic, edition2018
        //     ^ this text
        val lang = lines.nextOrNull()?.let { text.substring(it.first, it.second - 1) } ?: return@mapNotNull null
        if (lang.isNotEmpty()) {
            val parts = lang.split(LANG_SPLIT_REGEX).filter { it.isNotBlank() }
            if (parts.any { it !in RUST_LANG_ALIASES }) return@mapNotNull null
        }

        // skip doc comment infix (`///`, `//!` or ` * `)
        val ranges = lines.asSequence().mapNotNull { (lineStart, lineEnd) ->
            val index = text.indexOf(infix, lineStart)
            if (index != -1 && index < lineEnd) {
                val start = index + infix.length
                TextRange(start, lineEnd)
            } else {
                null
            }
        }.toList()

        if (ranges.isEmpty()) return@mapNotNull null
        ranges
    }
}

private fun String.indicesOf(s: String): Sequence<Int> =
    generateSequence(indexOf(s)) { indexOf(s, it + s.length) }.takeWhile { it != -1 }

fun VirtualFile.isDoctestInjection(project: Project): Boolean {
    val virtualFileWindow = this as? VirtualFileWindow ?: return false
    val hostFile = virtualFileWindow.delegate.toPsiFile(project) as? RsFile ?: return false
    val hostElement = hostFile.findElementAt(virtualFileWindow.documentWindow.injectedToHost(0)) ?: return false
    return hostElement.elementType in RS_DOC_COMMENTS
}

val RsFile.isDoctestInjection: Boolean
    get() = virtualFile?.isDoctestInjection(project) == true

val RsElement.isDoctestInjection: Boolean
    get() = (contextualFile as? RsFile)?.isDoctestInjection == true
