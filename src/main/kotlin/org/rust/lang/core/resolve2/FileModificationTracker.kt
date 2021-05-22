/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve2

import com.intellij.openapiext.isUnitTestMode
import com.intellij.util.io.DigestUtil
import com.intellij.util.io.IOUtil
import org.rust.lang.core.crate.Crate
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.ext.isEnabledByCfgSelf
import org.rust.lang.core.psi.ext.variants
import org.rust.lang.core.stubs.RsEnumItemStub
import org.rust.lang.core.stubs.RsMacroCallStub
import org.rust.lang.core.stubs.RsModItemStub
import org.rust.lang.core.stubs.RsNamedStub
import org.rust.openapiext.fileId
import org.rust.stdext.BitFlagsBuilder
import org.rust.stdext.HashCode
import org.rust.stdext.writeVarInt
import java.io.DataOutput
import java.io.DataOutputStream
import java.io.OutputStream
import java.security.DigestOutputStream
import java.util.*

interface Writeable {
    fun writeTo(data: DataOutput)
}

val RsFile.modificationStampForResolve: Long get() = viewProvider.modificationStamp

fun isFileChanged(file: RsFile, defMap: CrateDefMap, crate: Crate): Boolean {
    val fileInfo = defMap.fileInfos[file.virtualFile.fileId] ?: run {
        if (isUnitTestMode) error("Can't find fileInfo for ${file.virtualFile} in $defMap")
        return false
    }

    val isEnabledByCfgInner = file.isEnabledByCfgSelf(crate)
    // Don't use `file.isDeeplyEnabledByCfg` - it can trigger resolve (and cause infinite recursion)
    val isDeeplyEnabledByCfg = fileInfo.modData.isDeeplyEnabledByCfgOuter && isEnabledByCfgInner
    val hashCalculator = HashCalculator(isEnabledByCfgInner)
    val visitor = ModLightCollector(
        crate,
        hashCalculator,
        fileRelativePath = "",
        collectChildModules = true
    )
    val fileStub = file.getOrBuildStub() ?: return false
    ModCollectorBase.collectMod(fileStub, isDeeplyEnabledByCfg, visitor, crate)
    if (file.virtualFile == crate.rootModFile) {
        visitor.modData.attributes = file.getStdlibAttributes(crate)
    }
    return hashCalculator.getFileHash() != fileInfo.hash
}

private fun calculateModHash(modData: ModDataLight): HashCode {
    val digest = DigestUtil.sha1()
    val data = DataOutputStream(DigestOutputStream(OutputStream.nullOutputStream(), digest))

    modData.sort()
    data.writeElements(modData.items)
    data.writeElements(modData.enums)
    data.writeElements(modData.imports)
    data.writeElements(modData.macroCalls)
    data.writeElements(modData.procMacroCalls)
    data.writeElements(modData.macroDefs)
    data.writeElements(modData.macro2Defs)
    data.writeByte(modData.attributes?.ordinal ?: RsFile.Attributes.values().size)

    return HashCode.fromByteArray(digest.digest())
}

private class ModDataLight {
    val items: MutableList<ItemLight> = mutableListOf()
    val enums: MutableList<EnumLight> = mutableListOf()
    val imports: MutableList<ImportLight> = mutableListOf()
    val macroCalls: MutableList<MacroCallLight> = mutableListOf()
    val procMacroCalls: MutableList<ProcMacroCallLight> = mutableListOf()
    val macroDefs: MutableList<MacroDefLight> = mutableListOf()
    val macro2Defs: MutableList<Macro2DefLight> = mutableListOf()
    var attributes: RsFile.Attributes? = null  // not null only for crate root

    fun sort() {
        items.sortBy { it.name }
        enums.sortBy { it.item.name }
        imports.sortWith(compareBy(Arrays::compare) { it.usePath })
        macro2Defs.sortBy { it.name }
    }
}

/** Calculates hash of single file (including all inline modules) */
class HashCalculator(private val isEnabledByCfgInner: Boolean) {
    // We can't use `Map<String /* fileRelativePath */, HashCode>`,
    // because two modules with different cfg attributes can have same `fileRelativePath`
    private val modulesHash: MutableList<ModHash> = mutableListOf()

    private data class ModHash(val fileRelativePath: String, val hash: HashCode)

    fun getVisitor(crate: Crate, fileRelativePath: String): ModVisitor =
        ModLightCollector(crate, this, fileRelativePath)

    fun onCollectMod(fileRelativePath: String, hash: HashCode) {
        modulesHash += ModHash(fileRelativePath, hash)
    }

    /** Called after visiting all inline submodules */
    fun getFileHash(): HashCode {
        modulesHash.sortBy { it.fileRelativePath }
        val digest = DigestUtil.sha1()
        for ((fileRelativePath, modHash) in modulesHash) {
            digest.update(fileRelativePath.toByteArray())
            digest.update(modHash.toByteArray())
        }
        digest.update(if (isEnabledByCfgInner) 1 else 0)
        return HashCode.fromByteArray(digest.digest())
    }
}

private class ModLightCollector(
    private val crate: Crate,
    private val hashCalculator: HashCalculator,
    private val fileRelativePath: String,
    private val collectChildModules: Boolean = false,
) : ModVisitor {

    val modData: ModDataLight = ModDataLight()

    override fun collectSimpleItem(item: SimpleItemLight) {
        modData.items += item
    }

    override fun collectModOrEnumItem(item: ModOrEnumItemLight, stub: RsNamedStub) {
        if (stub is RsEnumItemStub) {
            collectEnum(item, stub)
            return
        }

        modData.items += item
        if (collectChildModules && stub is RsModItemStub) {
            collectMod(stub, item.name, item.isDeeplyEnabledByCfg)
        }
    }

    private fun collectEnum(enum: ModOrEnumItemLight, enumStub: RsEnumItemStub) {
        val variants = enumStub.variants.mapNotNullTo(mutableListOf()) {
            EnumVariantLight(
                name = it.name ?: return@mapNotNullTo null,
                isDeeplyEnabledByCfg = enum.isDeeplyEnabledByCfg && it.isEnabledByCfgSelf(crate),
                hasBlockFields = it.blockFields != null
            )
        }
        variants.sortBy { it.name }
        modData.enums += EnumLight(enum, variants)
    }

    override fun collectImport(import: ImportLight) {
        modData.imports += import
    }

    override fun collectMacroCall(call: MacroCallLight, stub: RsMacroCallStub) {
        modData.macroCalls += call
    }

    override fun collectProcMacroCall(call: ProcMacroCallLight) {
        modData.procMacroCalls += call
    }

    override fun collectMacroDef(def: MacroDefLight) {
        modData.macroDefs += def
    }

    override fun collectMacro2Def(def: Macro2DefLight) {
        modData.macro2Defs += def
    }

    override fun afterCollectMod() {
        val fileHash = calculateModHash(modData)
        hashCalculator.onCollectMod(fileRelativePath, fileHash)
    }

    private fun collectMod(mod: RsModItemStub, modName: String, isDeeplyEnabledByCfg: Boolean) {
        val fileRelativePath = "$fileRelativePath::$modName"
        val visitor = ModLightCollector(
            crate,
            hashCalculator,
            fileRelativePath,
            collectChildModules = true
        )
        ModCollectorBase.collectMod(mod, isDeeplyEnabledByCfg, visitor, crate)
    }
}

private class EnumVariantLight(
    val name: String,
    val isDeeplyEnabledByCfg: Boolean,
    val hasBlockFields: Boolean,
) : Writeable {
    override fun writeTo(data: DataOutput) {
        IOUtil.writeUTF(data, name)

        var flags = 0
        if (isDeeplyEnabledByCfg) flags += IS_DEEPLY_ENABLED_BY_CFG_MASK
        if (hasBlockFields) flags += HAS_BLOCK_FIELDS
        data.writeByte(flags)
    }

    companion object : BitFlagsBuilder(Limit.BYTE) {
        private val IS_DEEPLY_ENABLED_BY_CFG_MASK: Int = nextBitMask()
        private val HAS_BLOCK_FIELDS: Int = nextBitMask()
    }
}

private class EnumLight(
    val item: ModOrEnumItemLight,
    val variants: List<EnumVariantLight>,
) : Writeable {
    override fun writeTo(data: DataOutput) {
        item.writeTo(data)
        data.writeElements(variants)
    }
}

private fun DataOutput.writeElements(elements: List<Writeable>) {
    writeVarInt(elements.size)
    for (element in elements) {
        element.writeTo(this)
    }
}
