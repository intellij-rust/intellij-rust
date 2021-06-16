/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.doc.psi

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.impl.source.tree.injected.InjectionBackgroundSuppressor
import org.rust.ide.annotator.RsDoctestAnnotator
import org.rust.ide.injected.RsDoctestLanguageInjector
import org.rust.lang.core.psi.ext.RsElement

interface RsDocElement : RsElement {
    val containingDoc: RsDocComment

    val markdownValue: String
}

/**
 * A skipped `///`, `//!` or `*` (or other kind of documentation comment decorations)
 * is treated as a comment leaf in the markdown tree
 */
interface RsDocGap : PsiComment

/**
 * [RsDocAtxHeading] or [RsDocSetextHeading]
 */
interface RsDocHeading : RsDocElement

/**
 * A [markdown ATX headings](https://spec.commonmark.org/0.29/#atx-heading)
 * ```
 * /// # Header 1
 * /// ## Header 2
 * /// ### Header 3
 * /// #### Header 4
 * /// ##### Header 5
 * /// ###### Header 6
 * ```
 */
interface RsDocAtxHeading : RsDocHeading

/**
 * A [markdown Setext headings](https://spec.commonmark.org/0.29/#setext-heading)
 * ```
 * /// Header 1
 * /// ========
 * ///
 * /// Header 2
 * /// --------
 * ```
 */
interface RsDocSetextHeading : RsDocHeading

/** *an emphasis span* or _an emphasis span_ */
interface RsDocEmphasis : RsDocElement

/** **a strong span** or __a strong span__ */
interface RsDocStrong : RsDocElement

/** `a code span` */
interface RsDocCodeSpan : RsDocElement

/** <http://example.com> */
interface RsDocAutoLink : RsDocElement

interface RsDocLink : RsDocElement

/**
 * ```
 * /// [link text](link_destination)
 * /// [link text](link_destination "link title")
 * ```
 */
interface RsDocInlineLink : RsDocLink {
    val linkText: RsDocLinkText
    val linkDestination: RsDocLinkDestination
}

/**
 * ```
 * /// [link label]
 * ```
 *
 * Then, the link should be defined with [RsDocLinkDefinition]
 */
interface RsDocLinkReferenceShort : RsDocLink {
    val linkLabel: RsDocLinkLabel
}

/**
 * ```
 * /// [link text][link label]
 * ```
 *
 * Then, the link should be defined with [RsDocLinkDefinition] (identified by [linkLabel])
 */
interface RsDocLinkReferenceFull : RsDocLink {
    val linkText: RsDocLinkText
    val linkLabel: RsDocLinkLabel
}

/**
 * ```
 * /// [link label]: link_destination
 * ```
 */
interface RsDocLinkDefinition : RsDocLink {
    val linkLabel: RsDocLinkLabel
    val linkDestination: RsDocLinkDestination
}

/**
 * A `[LINK TEXT]` part of such links:
 * ```
 * /// [LINK TEXT](link_destination)
 * /// [LINK TEXT][link label]
 * ```
 * Includes brackets (`[`, `]`).
 * A child of [RsDocInlineLink] or [RsDocLinkReferenceFull]
 */
interface RsDocLinkText : RsDocElement

/**
 * A `[LINK LABEL]` part in these contexts:
 * ```
 * /// [LINK LABEL]
 * /// [link text][LINK LABEL]
 * /// [LINK LABEL]: link_destination
 * ```
 *
 * A link label is used to match *a link reference* with *a link definition*.
 *
 * A child of [RsDocLinkReferenceShort], [RsDocLinkReferenceFull] or [RsDocLinkDefinition]
 */
interface RsDocLinkLabel : RsDocElement

/**
 * A `LINK TITLE` (with quotes and parentheses) part in these contexts:
 * ```
 * /// [inline link](http://example.com "LINK TITLE")
 * /// [inline link](http://example.com 'LINK TITLE')
 * /// [inline link](http://example.com (LINK TITLE))
 * ```
 *
 * A child of [RsDocInlineLink]
 */
interface RsDocLinkTitle : RsDocElement

/**
 * A `LINK DESTINATION` part in these contexts:
 * ```
 * /// [link text](LINK DESTINATION)
 * /// [link label]: LINK DESTINATION
 * ```
 *
 * A child of [RsDocInlineLink] or [RsDocLinkDefinition]
 */
interface RsDocLinkDestination : RsDocElement

//interface RsDocLinkDestination : RsDocElement
//interface RsDocLinkDefinitionPathDisambiguator : RsDocElement
//interface RsDocLinkDefinitionPathExtra : RsDocElement

/**
 * A [markdown code fence](https://spec.commonmark.org/0.29/#fenced-code-blocks).
 *
 * Provides specific behavior for language injections (see [RsDoctestLanguageInjector]).
 *
 * [InjectionBackgroundSuppressor] is used to disable builtin background highlighting for injection.
 * We create such background manually by [RsDoctestAnnotator] (see the class docs)
 */
interface RsDocCodeFence : RsDocElement, PsiLanguageInjectionHost, InjectionBackgroundSuppressor

// TODO should be `PsiLanguageInjectionHost` too
interface RsDocCodeBlock : RsDocElement

/**
 * See [markdown HTML blocks](https://spec.commonmark.org/0.29/#html-blocks)
 */
interface RsDocHtmlBlock : RsDocElement

/**
 * Leading and trailing backtick or tilda sequences of [RsDocCodeFence].
 *
 * `````
 * /// ```
 * ///  ^ this
 * /// ```
 *      ^ and this
 * `````
 */
interface RsDocCodeFenceStartEnd : RsDocElement

/**
 * A child of [RsDocCodeFence].
 *
 * `````
 * /// ```rust, should_panic, edition2018
 *        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ this text
 * `````
 */
interface RsDocCodeFenceLang : RsDocElement
