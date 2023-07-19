/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.navigation.goto

import com.intellij.lang.Language
import org.rust.RsBundle
import org.rust.lang.RsLanguage
import org.rust.lang.core.psi.ext.RsNamedElement
import org.rust.lang.core.stubs.index.RsGotoClassIndex

class RsClassNavigationContributor
    : RsNavigationContributorBase<RsNamedElement>(RsGotoClassIndex.KEY, RsNamedElement::class.java) {
    override fun getElementKind(): String = RsBundle.message("go.to.class.kind.text")
    override fun getElementKindsPluralized(): List<String> = listOf(RsBundle.message("go.to.class.kind.text.pluralized"))
    override fun getElementLanguage(): Language = RsLanguage
}
