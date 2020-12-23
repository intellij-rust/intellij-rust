/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve2

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.StubElement
import com.intellij.util.io.IOUtil
import org.rust.lang.core.crate.Crate
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.Namespace
import org.rust.lang.core.resolve.namespaces
import org.rust.lang.core.resolve2.util.forEachLeafSpeck
import org.rust.lang.core.resolve2.util.getPathWithAdjustedDollarCrate
import org.rust.lang.core.resolve2.util.getRestrictedPath
import org.rust.lang.core.stubs.*
import org.rust.stdext.*
import java.io.DataOutput

/**
 * This class is used:
 * - When collecting explicit items: filling ModData + calculating hash
 * - When collecting expanded items: filling ModData
 * - When checking if file was changed: calculating hash
 */
class ModCollectorBase private constructor(
    private val visitor: ModVisitor,
    private val crate: Crate,
    private val isDeeplyEnabledByCfg: Boolean,
) {

    /** [itemsOwner] - [RsMod] or [RsForeignModItem] */
    private fun collectElements(itemsOwner: StubElement<out RsElement>) {
        val items = itemsOwner.childrenStubs

        // This should be processed eagerly instead of deferred to resolving.
        // `#[macro_use] extern crate` is hoisted to import macros before collecting any other items.
        for (item in items) {
            if (item is RsExternCrateItemStub) {
                collectExternCrate(item)
            }
        }

        var macroIndexInParent = 0
        for (item in items) {
            if (item !is RsExternCrateItemStub) {
                collectElement(item, macroIndexInParent)
                if (item.hasMacroIndex()) {
                    ++macroIndexInParent
                }
            }
        }
    }

    private fun collectElement(element: StubElement<out PsiElement>, macroIndexInParent: Int) {
        when (element) {
            // impls are not named elements, so we don't need them for name resolution
            is RsImplItemStub -> Unit

            is RsForeignModStub -> collectElements(element)

            is RsUseItemStub -> collectUseItem(element)
            is RsExternCrateItemStub -> error("extern crates are processed eagerly")

            is RsMacroCallStub -> collectMacroCall(element, macroIndexInParent)
            is RsMacroStub -> collectMacroDef(element, macroIndexInParent)

            // Should be after macro stubs (they are [RsNamedStub])
            is RsNamedStub -> collectItem(element, macroIndexInParent)

            // `RsOuterAttr`, `RsInnerAttr` or `RsVis` when `itemsOwner` is `RsModItem`
            // `RsExternAbi` when `itemsOwner` is `RsForeignModItem`
            // etc
            else -> Unit
        }
    }

    private fun collectUseItem(useItem: RsUseItemStub) {
        val visibility = VisibilityLight.from(useItem)
        val hasPreludeImport = useItem.hasPreludeImport
        useItem.forEachLeafSpeck { usePath, alias, isStarImport ->
            // Ignore `use self;`
            if (alias == null && usePath.singleOrNull() == "self") return@forEachLeafSpeck

            val import = ImportLight(
                usePath = usePath,
                nameInScope = alias ?: usePath.last(),
                visibility = visibility,
                isDeeplyEnabledByCfg = isDeeplyEnabledByCfg && useItem.isEnabledByCfgSelf(crate),
                isGlob = isStarImport,
                isPrelude = hasPreludeImport
            )
            visitor.collectImport(import)
        }
    }

    private fun collectExternCrate(externCrate: RsExternCrateItemStub) {
        val import = ImportLight(
            usePath = arrayOf(externCrate.name),
            nameInScope = externCrate.nameWithAlias,
            visibility = VisibilityLight.from(externCrate),
            isDeeplyEnabledByCfg = isDeeplyEnabledByCfg && externCrate.isEnabledByCfgSelf(crate),
            isExternCrate = true,
            isMacroUse = externCrate.hasMacroUse
        )
        if (externCrate.name == "self" && import.nameInScope == "self") return
        visitor.collectImport(import)
    }

    private fun collectItem(item: RsNamedStub, macroIndexInParent: Int) {
        val procMacroName = if (item is RsFunctionStub && item.isProcMacroDef) item.psi.procMacroName else null
        val name = procMacroName ?: item.name ?: return
        if (item !is RsAttributeOwnerStub) return

        val (hasMacroUse, pathAttribute) = when (item) {
            is RsModItemStub -> item.hasMacroUse to item.pathAttribute
            is RsModDeclItemStub -> item.hasMacroUse to item.pathAttribute
            else -> false to null
        }
        @Suppress("UNCHECKED_CAST")
        val itemLight = ItemLight(
            name = name,
            visibility = VisibilityLight.from(item as StubElement<out RsVisibilityOwner>),
            isDeeplyEnabledByCfg = isDeeplyEnabledByCfg && item.isEnabledByCfgSelf(crate),
            namespaces = item.namespaces,
            isProcMacroDef = procMacroName != null,
            macroIndexInParent = macroIndexInParent,
            isModItem = item is RsModItemStub,
            isModDecl = item is RsModDeclItemStub,
            hasMacroUse = hasMacroUse,
            pathAttribute = pathAttribute
        )
        visitor.collectItem(itemLight, item)
    }

    private fun collectMacroCall(call: RsMacroCallStub, macroIndexInParent: Int) {
        val isCallDeeplyEnabledByCfg = isDeeplyEnabledByCfg && call.isEnabledByCfgSelf(crate)
        if (!isCallDeeplyEnabledByCfg) return
        val body = call.getIncludeMacroArgument(crate) ?: call.macroBody ?: return
        val path = call.getPathWithAdjustedDollarCrate() ?: return
        val callLight = MacroCallLight(path, body, call.bodyHash, macroIndexInParent)
        visitor.collectMacroCall(callLight, call)
    }

    private fun collectMacroDef(def: RsMacroStub, macroIndexInParent: Int) {
        val isDefDeeplyEnabledByCfg = isDeeplyEnabledByCfg && def.isEnabledByCfgSelf(crate)
        if (!isDefDeeplyEnabledByCfg) return
        val defLight = MacroDefLight(
            name = def.name ?: return,
            body = def.macroBody ?: return,
            bodyHash = def.bodyHash,
            hasMacroExport = def.hasMacroExport,
            hasLocalInnerMacros = def.hasMacroExportLocalInnerMacros,
            macroIndexInParent = macroIndexInParent
        )
        visitor.collectMacroDef(defLight)
    }

    companion object {
        fun collectMod(mod: StubElement<out RsMod>, isDeeplyEnabledByCfg: Boolean, visitor: ModVisitor, crate: Crate) {
            val collector = ModCollectorBase(visitor, crate, isDeeplyEnabledByCfg)
            collector.collectElements(mod)
            collector.visitor.afterCollectMod()
        }
    }
}

interface ModVisitor {
    fun collectItem(item: ItemLight, stub: RsNamedStub)
    fun collectImport(import: ImportLight)
    fun collectMacroCall(call: MacroCallLight, stub: RsMacroCallStub)
    fun collectMacroDef(def: MacroDefLight)
    fun afterCollectMod() {}
}

class CompositeModVisitor(
    private val visitor1: ModVisitor,
    private val visitor2: ModVisitor,
) : ModVisitor {
    override fun collectItem(item: ItemLight, stub: RsNamedStub) {
        visitor1.collectItem(item, stub)
        visitor2.collectItem(item, stub)
    }

    override fun collectImport(import: ImportLight) {
        visitor1.collectImport(import)
        visitor2.collectImport(import)
    }

    override fun collectMacroCall(call: MacroCallLight, stub: RsMacroCallStub) {
        visitor1.collectMacroCall(call, stub)
        visitor2.collectMacroCall(call, stub)
    }

    override fun collectMacroDef(def: MacroDefLight) {
        visitor1.collectMacroDef(def)
        visitor2.collectMacroDef(def)
    }

    override fun afterCollectMod() {
        visitor1.afterCollectMod()
        visitor2.afterCollectMod()
    }
}

sealed class VisibilityLight : Writeable {
    object Public : VisibilityLight()

    /** `pub(crate)` */
    object RestrictedCrate : VisibilityLight()

    class Restricted(val inPath: Array<String>) : VisibilityLight()

    object Private : VisibilityLight()

    override fun writeTo(data: DataOutput) {
        when (this) {
            Public -> data.writeByte(0)
            RestrictedCrate -> data.writeByte(1)
            Private -> data.writeByte(2)
            is Restricted -> {
                data.writeByte(3)
                data.writePath(inPath)
            }
        }.exhaustive
    }

    companion object {
        fun from(item: StubElement<out RsVisibilityOwner>): VisibilityLight {
            val vis = item.findChildStubByType(RsVisStub.Type) ?: return Private
            return when (vis.kind) {
                RsVisStubKind.PUB -> Public
                RsVisStubKind.CRATE -> RestrictedCrate
                RsVisStubKind.RESTRICTED -> {
                    val path = vis.getRestrictedPath() ?: return RestrictedCrate
                    val pathSingle = path.singleOrNull()
                    if (path.isEmpty() || pathSingle == "crate") return RestrictedCrate
                    if (pathSingle == "self") return Private
                    Restricted(path)
                }
            }
        }
    }
}

data class ItemLight(
    val name: String,
    val visibility: VisibilityLight,
    val isDeeplyEnabledByCfg: Boolean,
    val namespaces: Set<Namespace>,
    val isProcMacroDef: Boolean,
    /** See [MacroIndex] */
    val macroIndexInParent: Int,

    val isModItem: Boolean,
    val isModDecl: Boolean,
    /** Only for [RsModItem] or [RsModDeclItem] */
    val hasMacroUse: Boolean,
    val pathAttribute: String?,
) : Writeable {
    override fun writeTo(data: DataOutput) {
        IOUtil.writeUTF(data, name)
        visibility.writeTo(data)
        data.writeVarInt(macroIndexInParent)

        var flags = 0
        if (Namespace.Types in namespaces) flags += NAMESPACE_TYPES_MASK
        if (Namespace.Values in namespaces) flags += NAMESPACE_VALUES_MASK
        if (Namespace.Macros in namespaces) flags += NAMESPACE_MACROS_MASK
        if (isDeeplyEnabledByCfg) flags += IS_DEEPLY_ENABLED_BY_CFG_MASK
        if (isModItem) flags += IS_MOD_ITEM_MASK
        if (isModDecl) flags += IS_MOD_DECL_MASK
        if (hasMacroUse) flags += HAS_MACRO_USE_MASK
        if (pathAttribute != null) flags += PATH_ATTRIBUTE_MASK
        data.writeByte(flags)

        if (pathAttribute != null) data.writeUTF(pathAttribute)
    }

    companion object {
        private val NAMESPACE_TYPES_MASK: Int = makeBitMask(0)
        private val NAMESPACE_VALUES_MASK: Int = makeBitMask(1)
        private val NAMESPACE_MACROS_MASK: Int = makeBitMask(2)
        private val IS_DEEPLY_ENABLED_BY_CFG_MASK: Int = makeBitMask(3)
        private val IS_MOD_ITEM_MASK: Int = makeBitMask(4)
        private val IS_MOD_DECL_MASK: Int = makeBitMask(5)
        private val HAS_MACRO_USE_MASK: Int = makeBitMask(6)
        private val PATH_ATTRIBUTE_MASK: Int = makeBitMask(7)
    }
}

class ImportLight(
    val usePath: Array<String>,
    val nameInScope: String,
    val visibility: VisibilityLight,
    val isDeeplyEnabledByCfg: Boolean,
    val isGlob: Boolean = false,
    val isExternCrate: Boolean = false,
    val isMacroUse: Boolean = false,
    val isPrelude: Boolean = false,  // #[prelude_import]
) : Writeable {

    override fun writeTo(data: DataOutput) {
        data.writePath(usePath)
        IOUtil.writeUTF(data, nameInScope)
        visibility.writeTo(data)

        var flags = 0
        if (isDeeplyEnabledByCfg) flags += IS_DEEPLY_ENABLED_BY_CFG_MASK
        if (isGlob) flags += IS_GLOB_MASK
        if (isExternCrate) flags += IS_EXTERN_CRATE_MASK
        if (isMacroUse) flags += IS_MACRO_USE_MASK
        if (isPrelude) flags += IS_PRELUDE_MASK
        data.writeByte(flags)
    }

    companion object {
        private val IS_DEEPLY_ENABLED_BY_CFG_MASK: Int = makeBitMask(0)
        private val IS_GLOB_MASK: Int = makeBitMask(1)
        private val IS_EXTERN_CRATE_MASK: Int = makeBitMask(2)
        private val IS_MACRO_USE_MASK: Int = makeBitMask(3)
        private val IS_PRELUDE_MASK: Int = makeBitMask(4)
    }
}

class MacroCallLight(
    val path: Array<String>,
    val body: String,
    val bodyHash: HashCode?,
    /** See [MacroIndex] */
    val macroIndexInParent: Int
) : Writeable {

    override fun writeTo(data: DataOutput) {
        data.writePath(path)
        data.writeHashCodeNullable(bodyHash)
        data.writeVarInt(macroIndexInParent)
    }
}

data class MacroDefLight(
    val name: String,
    val body: String,
    val bodyHash: HashCode?,
    val hasMacroExport: Boolean,
    val hasLocalInnerMacros: Boolean,
    /** See [MacroIndex] */
    val macroIndexInParent: Int,
) : Writeable {

    override fun writeTo(data: DataOutput) {
        IOUtil.writeUTF(data, name)
        data.writeHashCodeNullable(bodyHash)
        data.writeVarInt(macroIndexInParent)

        var flags = 0
        if (hasMacroExport) flags += HAS_MACRO_EXPORT_MASK
        if (hasLocalInnerMacros) flags += HAS_LOCAL_INNER_MACROS_MASK
        data.writeByte(flags)
    }

    companion object {
        private val HAS_MACRO_EXPORT_MASK: Int = makeBitMask(0)
        private val HAS_LOCAL_INNER_MACROS_MASK: Int = makeBitMask(1)
    }
}

private fun DataOutput.writePath(path: Array<String>) {
    for (segment in path) {
        IOUtil.writeUTF(this, segment)
        writeChar(':'.toInt())
    }
}

fun StubElement<*>.hasMacroIndex(): Boolean =
    this is RsMacroCallStub || this is RsMacroStub || this is RsModItemStub || this is RsModDeclItemStub

fun PsiElement.hasMacroIndex(): Boolean =
    this is RsMacroCall || this is RsMacro || this is RsModItem || this is RsModDeclItem
