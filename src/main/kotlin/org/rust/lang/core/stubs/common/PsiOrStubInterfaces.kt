/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.stubs.common

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.StubElement
import org.rust.lang.core.crate.Crate
import org.rust.lang.core.psi.ext.PathKind
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.containingCrate

interface RsPathPsiOrStub {
    val path: RsPathPsiOrStub?
    val referenceName: String?
    val hasColonColon: Boolean
    val kind: PathKind
}

interface RsMetaItemPsiOrStub {
    val path: RsPathPsiOrStub?
    val metaItemArgs: RsMetaItemArgsPsiOrStub?

    @JvmDefault
    val metaItemArgsList: List<RsMetaItemPsiOrStub>
        get() = metaItemArgs?.metaItemList.orEmpty()

    val hasEq: Boolean
    val value: String?
}

interface RsMetaItemArgsPsiOrStub {
    // The list is `Mutable` in order to match types with java implementation
    val metaItemList: MutableList<out RsMetaItemPsiOrStub>
}

interface RsAttributeOwnerPsiOrStub<T : RsMetaItemPsiOrStub> {
    val rawMetaItems: Sequence<T>

    @JvmDefault
    val rawMetaItemsFromOuterAttrs: Sequence<T>
        get() = emptySequence()

    @JvmDefault
    val containingCrate: Crate?
        get() = when (this) {
            is PsiElement -> (this as RsElement).containingCrate
            is StubElement<*> -> (psi as RsElement).containingCrate
            else -> error("unreachable")
        }
}
