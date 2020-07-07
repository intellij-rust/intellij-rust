/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.doc.psi

import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.impl.source.tree.injected.InjectionBackgroundSuppressor
import org.rust.ide.annotator.RsDoctestAnnotator
import org.rust.ide.injected.RsDoctestLanguageInjector
import org.rust.lang.core.psi.ext.RsDocAndAttributeOwner
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.ancestorStrict

interface RsDocElement : RsElement {
    @JvmDefault
    val containingDoc: RsDocComment
        get() = ancestorStrict<RsDocComment>()
            ?: error("RsDocElement cannot leave outside of the doc comment! `${text}`")

    @JvmDefault
    val owner: RsDocAndAttributeOwner?
        get() = containingDoc.owner
}

interface RsDocLink : RsDocElement {
    val linkTextOrLabel: RsDocElement
}

/**
 * ```
 * [link text](link_destination)
 * ```
 */
interface RsDocInlineLink : RsDocLink {
    val linkText: RsDocLinkText
    val linkDestination: RsDocLinkDestination

    @JvmDefault
    override val linkTextOrLabel: RsDocElement
        get() = linkText
}

/**
 * ```
 * [link label]
 * ```
 *
 * Then, the link should be defined with [RsDocLinkReferenceDef]
 */
interface RsDocLinkReferenceShort : RsDocLink {
    val linkLabel: RsDocLinkLabel

    @JvmDefault
    override val linkTextOrLabel: RsDocElement
        get() = linkLabel
}

/**
 * ```
 * [link text][link label]
 * ```
 *
 * Then, the link should be defined with [RsDocLinkReferenceDef] (identified by [linkLabel])
 */
interface RsDocLinkReferenceFull : RsDocLink {
    val linkText: RsDocLinkText
    val linkLabel: RsDocLinkLabel

    @JvmDefault
    override val linkTextOrLabel: RsDocElement
        get() = linkText
}

/**
 * ```
 * [link label]: link_destination
 * ```
 */
interface RsDocLinkReferenceDef : RsDocLink {
    val linkLabel: RsDocLinkLabel
    val linkDestination: RsDocLinkDestination

    @JvmDefault
    override val linkTextOrLabel: RsDocElement
        get() = linkLabel
}

interface RsDocLinkText : RsDocElement
interface RsDocLinkLabel : RsDocElement
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
