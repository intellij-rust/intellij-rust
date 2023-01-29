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
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import icons.JavaScriptPsiIcons
import org.rust.RsBundle
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import java.nio.file.InvalidPathException
import java.nio.file.Paths
import javax.swing.Icon

@Suppress("NAME_SHADOWING")
class RsWasmBindgenLineMarkerProvider : RelatedItemLineMarkerProvider() {

    override fun getName(): String = RsBundle.message("gutter.rust.generated.typescript.declarations.name")
    override fun getIcon(): Icon = JavaScriptPsiIcons.FileTypes.TypeScriptFile

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
                val reference = (element.typeReference as? RsPathType) ?: return null
                val struct = (reference.path.reference?.resolve() as? RsStructItem) ?: return null
                findRelatedTsElement(struct, tsPsiFile)
            }
            else -> findRelatedTsElement(element, tsPsiFile)
        } ?: return null

        return RelatedItemLineMarkerInfo(
            attr,
            attr.textRange,
            icon,
            { RsBundle.message("gutter.rust.generated.typescript.declarations.tooltip") },
            DefaultGutterIconNavigationHandler(listOf(destination), RsBundle.message("gutter.rust.generated.typescript.declarations.popup.title")),
            GutterIconRenderer.Alignment.RIGHT,
            { listOf(GotoRelatedItem(destination)) }
        )
    }

    private fun findRelatedTsElement(element: RsOuterAttributeOwner, tsPsiFile: PsiFile): JSElement? =
        when (element) {
            is RsFunction -> tsPsiFile.descendantOfType<TypeScriptFunction> { it.name == element.name }
            is RsEnumItem -> tsPsiFile.descendantOfType<TypeScriptEnum> { it.name == element.name }
            is RsStructItem -> tsPsiFile.descendantOfType<TypeScriptClass> { it.name == element.name }
            else -> null
        }

    companion object {
        private val LOG: Logger = logger<RsWasmBindgenLineMarkerProvider>()
    }
}
