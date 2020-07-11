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
 * A skipped `///` (or other kind of documentation comment decorations)
 * is treated as a comment leaf in the markdown tree
 */
interface RsDocGap : PsiComment

/**
 * ```
 * /// Header 1
 * /// ========
 * ///
 * /// Header 2
 * /// --------
 * ///
 * /// # Header 1
 * /// ## Header 2
 * /// ### Header 3
 * /// #### Header 4
 * /// ##### Header 5
 * /// ###### Header 6
 * ```
 */
interface RsDocHeading : RsDocElement

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
 * Then, the link should be defined with [RsDocLinkReferenceDef]
 */
interface RsDocLinkReferenceShort : RsDocLink {
    val linkLabel: RsDocLinkLabel
}

/**
 * ```
 * /// [link text][link label]
 * ```
 *
 * Then, the link should be defined with [RsDocLinkReferenceDef] (identified by [linkLabel])
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
interface RsDocLinkReferenceDef : RsDocLink {
    val linkLabel: RsDocLinkLabel
    val linkDestination: RsDocLinkDestination
}

interface RsDocLinkText : RsDocElement
interface RsDocLinkLabel : RsDocElement
interface RsDocLinkTitle : RsDocElement
interface RsDocLinkDestination : RsDocElement

/**
 * Psi element for [markdown code fences](https://spec.commonmark.org/0.29/#fenced-code-blocks)
 * in rust documentation comments.
 *
 * Provides specific behavior for language injections (see [RsDoctestLanguageInjector]).
 *
 * [InjectionBackgroundSuppressor] is used to disable builtin background highlighting for injection.
 * We create such background manually by [RsDoctestAnnotator] (see the class docs)
 */
interface RsDocCodeFence : RsDocElement, PsiLanguageInjectionHost, InjectionBackgroundSuppressor

interface RsDocCodeBlock : RsDocElement
interface RsDocQuoteBlock : RsDocElement
interface RsDocHtmlBlock : RsDocElement
