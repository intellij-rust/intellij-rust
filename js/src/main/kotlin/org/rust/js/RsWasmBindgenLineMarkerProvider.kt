/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.js

import com.intellij.codeInsight.daemon.DefaultGutterIconNavigationHandler
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.lang.javascript.psi.JSElement
import com.intellij.lang.javascript.psi.ecma6.TypeScriptClass
import com.intellij.lang.javascript.psi.ecma6.TypeScriptEnum
import com.intellij.lang.javascript.psi.ecma6.TypeScriptFunction
import com.intellij.navigation.GotoRelatedItem
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.findDescendantOfType
import icons.JavaScriptPsiIcons
import org.rust.lang.core.psi.RsEnumItem
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsImplItem
import org.rust.lang.core.psi.RsStructItem
import org.rust.lang.core.psi.ext.RsOuterAttributeOwner
import org.rust.lang.core.psi.ext.RsVisibilityOwner
import org.rust.lang.core.psi.ext.containingCargoPackage
import org.rust.lang.core.psi.ext.findOuterAttr
import org.rust.lang.core.psi.impl.RsBaseTypeImpl
import java.nio.file.InvalidPathException
import java.nio.file.Paths

@Suppress("NAME_SHADOWING")
class RsWasmBindgenLineMarkerProvider : RelatedItemLineMarkerProvider() {
    override fun getLineMarkerInfo(element: PsiElement): RelatedItemLineMarkerInfo<*>? {
        val element = element as? RsOuterAttributeOwner ?: return null
        if (element !is RsFunction && element !is RsEnumItem && element !is RsStructItem && element !is RsImplItem) {
            return null
        }

        // Check the package has wasm-bindgen as dependency
        val cargoPackage = element.containingCargoPackage ?: return null
        if (cargoPackage.dependencies.none { it.pkg.name == "wasm-bindgen" }) {
            return null
        }

        // Only public elements are exposed in bindings
        if (element is RsVisibilityOwner && element !is RsImplItem && !element.isPublic) {
            return null
        }

        val attr = element.findOuterAttr("wasm_bindgen") ?: return null

        val basePath = cargoPackage.contentRoot?.path ?: return null
        val expectedName = cargoPackage.normName

        // Expecting to find related generated typescript declarations in pkg/ folder of current package
        val expectedPath = try {
            Paths.get(basePath, "pkg/$expectedName.d.ts").toString()
        } catch (e: InvalidPathException) {
            LOG.error(e)
            return null
        }
        val tsFile = LocalFileSystem.getInstance().findFileByPath(expectedPath) ?: return null
        val tsPsiFile = PsiManager.getInstance(element.project).findFile(tsFile) ?: return null

        // Trying to find the correlating element with the same name
        val destination = when (element) {
            // If it is impl, then resolving to the related struct and using its name
            is RsImplItem -> {
                val reference = (element.typeReference as? RsBaseTypeImpl) ?: return null
                val struct = (reference.path?.reference?.resolve() as? RsStructItem) ?: return null
                findRelatedTsElement(struct, tsPsiFile)
            }
            else -> findRelatedTsElement(element, tsPsiFile)
        } ?: return null

        return RelatedItemLineMarkerInfo(
            attr,
            attr.textRange,
            JavaScriptPsiIcons.FileTypes.TypeScriptFile,
            { "Go to generated declaration" },
            DefaultGutterIconNavigationHandler(listOf(destination), "Generated declarations"),
            GutterIconRenderer.Alignment.RIGHT,
            { listOf(GotoRelatedItem(destination)) }
        )
    }

    private fun findRelatedTsElement(element: RsOuterAttributeOwner, tsPsiFile: PsiFile): JSElement? =
        when (element) {
            is RsFunction -> tsPsiFile.findDescendantOfType<TypeScriptFunction> { it.name == element.name }
            is RsEnumItem -> tsPsiFile.findDescendantOfType<TypeScriptEnum> { it.name == element.name }
            is RsStructItem -> tsPsiFile.findDescendantOfType<TypeScriptClass> { it.name == element.name }
            else -> null
        }

    companion object {
        private val LOG: Logger = Logger.getInstance(RsWasmBindgenLineMarkerProvider::class.java)
    }
}
