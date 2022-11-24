/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.injected

import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.lang.PsiBuilder
import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.TokenType.WHITE_SPACE
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.text.CharArrayUtil
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.cargo.util.AutoInjectedCrates
import org.rust.lang.RsLanguage
import org.rust.lang.core.parser.createRustPsiBuilder
import org.rust.lang.core.parser.probe
import org.rust.lang.core.psi.RsElementTypes
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.ext.*
import org.rust.lang.doc.docElements
import org.rust.lang.doc.psi.RsDocCodeFence
import org.rust.lang.doc.psi.RsDocComment
import org.rust.lang.doc.psi.RsDocElementTypes.DOC_DATA
import org.rust.lang.doc.psi.RsDocGap
import org.rust.openapiext.Testmark
import org.rust.openapiext.toPsiFile
import org.rust.stdext.withPrevious
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
    "edition2015",
    "edition2018",
    "edition2021"
)

class RsDoctestLanguageInjector : MultiHostInjector {
    override fun elementsToInjectIn(): List<Class<out PsiElement>> =
        listOf(RsDocCodeFence::class.java)

    override fun getLanguagesToInject(registrar: MultiHostRegistrar, context: PsiElement) {
        if (context !is RsDocCodeFence || !context.isValidHost) return
        val project = context.project
        if (!project.rustSettings.doctestInjectionEnabled) return

        val crate = context.containingCrate
        if (!crate.areDoctestsEnabled) return // only library targets can have doctests
        val crateName = crate.normName

        val info = context.doctestInfo() ?: return
        val text = info.text

        val ranges = info.rangesForInjection.map {
            val codeStart = CharArrayUtil.shiftForward(text, it.startOffset, it.endOffset, " \t")
            // `cargo doc` has special rules for code lines which start with `#`:
            //   * `# ` prefix is used to mark lines that should be skipped in rendered documentation.
            //   * `##` prefix is converted to `#`
            // See https://github.com/rust-lang/rust/blob/5182cc1c/src/librustdoc/html/markdown.rs#L114
            when {
                text.startsWith("##", codeStart) -> TextRange(codeStart + 1, it.endOffset)
                text.startsWith("# ", codeStart) -> TextRange(codeStart + 2, it.endOffset)
                text.startsWith("#\n", codeStart) -> TextRange(codeStart + 1, it.endOffset)
                else -> TextRange(it.startOffset, it.endOffset)
            }
        }

        if (ranges.isEmpty()) return

        val inj = registrar.startInjecting(RsLanguage)

        val fullInjectionText = buildString {
            ranges.forEach {
                append(text, it.startOffset, it.endOffset)
            }
        }

        // Rustdoc doesn't wrap doctest with `main` function and `extern crate` declaration
        // if they are present. Rustdoc uses rust parser to determine existence of them.
        // See https://github.com/rust-lang/rust/blob/f688ba608/src/librustdoc/test.rs#L427
        //
        // We use a lexer instead of parser here to reduce CPU usage. It is less strict,
        // i.e. sometimes we can think that main function exists when it's not. But such
        // code is very rare, so I think this implementation in reasonable.
        val containsMain = fullInjectionText.contains("main")
        val containsExternCrate = fullInjectionText.contains("extern") && fullInjectionText.contains("crate")
        val (alreadyHasMain, alreadyHasExternCrate) = if (containsMain || containsExternCrate) {
            val lexer = project.createRustPsiBuilder(fullInjectionText)
            val alreadyHasMain = lexer.probe {
                lexer.findTokenSequence(RsElementTypes.FN, "main", RsElementTypes.LPAREN)
            }
            val alreadyHasExternCrate =
                lexer.findTokenSequence(RsElementTypes.EXTERN, RsElementTypes.CRATE, crateName)
            alreadyHasMain to alreadyHasExternCrate
        } else {
            false to false
        }

        // Crate attributes like `#![no_std]` should always be at the start of the file
        val (attrsEndIndex, cratesEndIndex) = partitionSource(text, ranges)

        ranges.forEachIndexed { index, range ->
            val isLastIteration = index == ranges.size - 1

            var prefix: StringBuilder? = null
            if (index == attrsEndIndex && !alreadyHasExternCrate) {
                // Yes, we want to skip the only "std" crate. Not core/alloc/etc, the "std" only
                val isStdCrate = crateName == AutoInjectedCrates.STD &&
                    crate.origin == PackageOrigin.STDLIB
                if (!isStdCrate) {
                    prefix = StringBuilder().apply {
                        append("extern crate ")
                        append(crateName)
                        append("; ")
                    }
                }
            }
            if (index == cratesEndIndex && !alreadyHasMain) {
                if (prefix == null) prefix = StringBuilder()
                prefix.append("fn $INJECTED_MAIN_NAME() {\n")
            }

            val suffix = if (isLastIteration && !alreadyHasMain) "\n}" else null

            inj.addPlace(prefix?.toString(), suffix, context, range)
        }

        inj.doneInjecting()
    }

    companion object {
        const val INJECTED_MAIN_NAME: String = "__main"
    }
}

/**
 * ~~~
 * ///.foo
 * ///.---```
 * ///.---let a = 1;
 * ///
 * ///.---let b = a;
 * ///.---```
 * ~~~
 *
 * In this snippet, a dot (.) shows [docIndent] and a `---` shows [fenceIndent].
 * [rangesForInjection] include only `let a = 1;\n` and `let b = a;\n`, and
 * [rangesForBackgroundHighlighting] include `.---let a = 1;\n`, `\n` and `.---let b = a;`
 */
class DoctestInfo private constructor(
    private val docIndent: Int,
    private val fenceIndent: Int,
    private val contents: List<Content>,
    val text: String
) {
    val rangesForInjection: List<TextRange>
        get() = contents.mapNotNull { c ->
            val psi = (c as? Content.DocData)?.psi ?: return@mapNotNull null
            val range = psi.textRangeInParent
            val add = if (range.endOffset < text.length) 1 else 0
            val startOffset = CharArrayUtil.shiftForward(text, range.startOffset, range.startOffset + fenceIndent, " \t")
            if (startOffset < range.endOffset) TextRange(startOffset, range.endOffset + add) else null
        }

    val rangesForBackgroundHighlighting: List<TextRange>
        get() = contents.map { c ->
            when (c) {
                is Content.DocData -> {
                    val range = c.psi.textRangeInParent
                    val add = if (range.endOffset < text.length) 1 else 0
                    TextRange(range.startOffset - docIndent, range.endOffset + add)
                }
                is Content.EmptyLine -> c.range
            }
        }

    private sealed class Content {
        class DocData(val psi: PsiElement) : Content()
        class EmptyLine(val range: TextRange) : Content()
    }

    companion object {
        fun fromCodeFence(codeFence: RsDocCodeFence): DoctestInfo? {
            if (!codeFence.project.rustSettings.doctestInjectionEnabled) return null
            if (!codeFence.containingCrate.areDoctestsEnabled) return null
            if (hasUnbalancedCodeFencesBefore(codeFence)) return null

            val lang = codeFence.lang?.text ?: ""
            val parts = lang.split(LANG_SPLIT_REGEX).filter { it.isNotBlank() }
            if (parts.any { it !in RUST_LANG_ALIASES }) return null

            val start = codeFence.start
            val fenceIndent = start.text.indexOfFirst { it == '`' || it == '~' }
            val prevLeaf = PsiTreeUtil.prevLeaf(codeFence)
            val docIndent = if (prevLeaf is PsiWhiteSpace && PsiTreeUtil.prevLeaf(prevLeaf) is RsDocGap) {
                prevLeaf.textLength
            } else {
                0
            }

            var isAfterNewLine = false
            val contents = mutableListOf<Content>()

            for (element in codeFence.childrenWithLeaves) {
                when (element.elementType) {
                    DOC_DATA -> {
                        isAfterNewLine = false
                        contents += Content.DocData(element)
                    }
                    WHITE_SPACE -> {
                        for ((index, prevIndex) in element.text.indicesOf("\n").withPrevious()) {
                            if (isAfterNewLine) {
                                val startOffset = if (prevIndex != null) prevIndex + 1 else 0
                                val endOffset = index + 1
                                val range = TextRange(startOffset, endOffset).shiftRight(element.startOffsetInParent)
                                contents += Content.EmptyLine(range)
                            }
                            isAfterNewLine = true
                        }
                    }
                }
            }

            return DoctestInfo(docIndent, fenceIndent, contents, codeFence.text)
        }

        fun hasUnbalancedCodeFencesBefore(context: RsDocCodeFence): Boolean {
            val containingDoc = context.containingDoc
            val docOwner = containingDoc.owner ?: return false
            for (docElement in docOwner.docElements()) {
                if (docElement !is RsDocComment) continue // Ignore `#[doc = ""]` attributes for now
                if (docElement == containingDoc) return false

                if (docElement.codeFences.any { it.end == null }) {
                    Testmarks.UnbalancedCodeFence.hit()
                    return true
                }
            }

            return false
        }
    }

    object Testmarks {
        object UnbalancedCodeFence : Testmark()
    }
}

fun RsDocCodeFence.doctestInfo(): DoctestInfo? =
    DoctestInfo.fromCodeFence(this)

private fun String.indicesOf(s: String): Sequence<Int> =
    generateSequence(indexOf(s)) { indexOf(s, it + s.length) }.takeWhile { it != -1 }

private fun PsiBuilder.findTokenSequence(vararg seq: Any): Boolean {
    fun isTokenEq(t: Any): Boolean {
        return if (t is IElementType) {
            tokenType == t
        } else {
            tokenType == RsElementTypes.IDENTIFIER && tokenText == t
        }
    }

    while (!eof()) {
        if (isTokenEq(seq[0])) {
            val found = probe {
                for (i in 1 until seq.size) {
                    advanceLexer()
                    if (!isTokenEq(seq[i])) {
                        return@probe false
                    }
                }
                true
            }
            if (found) return true
        }
        advanceLexer()
    }
    return false
}

private enum class PartitionState {
    Attrs,
    Crates,
    Other,
}

// See https://github.com/rust-lang/rust/blob/f688ba608/src/librustdoc/test.rs#L518
private fun partitionSource(text: String, ranges: List<TextRange>): Pair<Int, Int> {
    var state = PartitionState.Attrs

    var attrsEndIndex = 0
    var cratesEndIndex = 0

    fun stateTransition(index: Int, newState: PartitionState) {
        when (state) {
            PartitionState.Attrs -> {
                attrsEndIndex = index
                cratesEndIndex = index
            }
            PartitionState.Crates -> cratesEndIndex = index
            PartitionState.Other -> error("unreachable")
        }
        state = newState
    }

    run {
        ranges.forEachIndexed { i, range ->
            val trimmedLine = text.substring(range.startOffset, range.endOffset).trim()

            when (state) {
                PartitionState.Attrs -> {
                    val isCrateAttr = trimmedLine.startsWith("#![") || trimmedLine.isBlank() ||
                        (trimmedLine.startsWith("//") && !trimmedLine.startsWith("///"))
                    if (!isCrateAttr) {
                        if (trimmedLine.startsWith("extern crate") || trimmedLine.startsWith("#[macro_use] extern crate")) {
                            stateTransition(i, PartitionState.Crates)
                        } else {
                            stateTransition(i, PartitionState.Other)
                            return@run
                        }
                    }
                }
                PartitionState.Crates -> {
                    val isCrate = trimmedLine.startsWith("extern crate") ||
                        trimmedLine.startsWith("#[macro_use] extern crate") ||
                        trimmedLine.isBlank() ||
                        (trimmedLine.startsWith("//") && !trimmedLine.startsWith("///"))
                    if (!isCrate) {
                        stateTransition(i, PartitionState.Other)
                        return@run
                    }
                }
                PartitionState.Other -> error("unreachable")
            }
        }
    }

    return attrsEndIndex to cratesEndIndex
}

fun VirtualFile.isDoctestInjection(project: Project): Boolean {
    val virtualFileWindow = this as? VirtualFileWindow ?: return false
    val hostFile = virtualFileWindow.delegate.toPsiFile(project) as? RsFile ?: return false
    val hostElement = hostFile.findElementAt(virtualFileWindow.documentWindow.injectedToHost(0)) ?: return false
    return hostElement.parent is RsDocCodeFence
}

val RsFile.isDoctestInjection: Boolean
    get() = virtualFile?.isDoctestInjection(project) == true

val RsElement.isDoctestInjection: Boolean
    get() = (contextualFile as? RsFile)?.isDoctestInjection == true

val RsElement.isInsideInjection: Boolean
    get() = (contextualFile as? RsFile)?.virtualFile is VirtualFileWindow
