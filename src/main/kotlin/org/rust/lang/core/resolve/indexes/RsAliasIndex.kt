/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve.indexes

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.psi.stubs.StubTree
import com.intellij.psi.stubs.StubTreeBuilder
import com.intellij.util.indexing.*
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.KeyDescriptor
import com.intellij.util.io.externalizer.StringCollectionExternalizer
import org.rust.ide.search.RsWithMacrosProjectScope
import org.rust.lang.RsFileType
import org.rust.lang.core.macros.MacroExpansionStubsProvider
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.ext.PathKind
import org.rust.lang.core.psi.ext.RsAbstractableOwner
import org.rust.lang.core.psi.ext.ownerBySyntaxOnly
import org.rust.lang.core.stubs.RsAliasStub
import org.rust.lang.core.stubs.RsFileStub
import org.rust.lang.core.stubs.RsTypeAliasStub
import org.rust.lang.core.stubs.RsUseSpeckStub
import org.rust.lang.core.types.TyFingerprint
import org.rust.openapiext.toPsiFile

class RsAliasIndex : FileBasedIndexExtension<TyFingerprint, List<String>>() {
    override fun getName(): ID<TyFingerprint, List<String>> = KEY

    override fun getIndexer() = object : DataIndexer<TyFingerprint, List<String>, FileContent> {
        override fun map(inputData: FileContent): Map<TyFingerprint, List<String>> {
            val stubTree = getStubTree(inputData) ?: return emptyMap()
            val map = hashMapOf<TyFingerprint, MutableList<String>>()
            for (stub in stubTree.plainList) {
                when (stub) {
                    is RsTypeAliasStub -> {
                        val psi = stub.psi
                        if (psi.ownerBySyntaxOnly !is RsAbstractableOwner.Impl) {
                            val aliasedName = stub.name ?: continue
                            val typeRef = psi.typeReference ?: continue
                            for (tyf in TyFingerprint.create(typeRef, emptyList())) {
                                map.getOrPut(tyf) { mutableListOf() } += aliasedName
                            }
                        }
                    }

                    is RsAliasStub -> {
                        val aliasedName = stub.name ?: continue

                        // `use foo::bar as baz`
                        // `       //~~~ this name`:
                        val parentUseSpeckName = (stub.parentStub as? RsUseSpeckStub)
                            ?.takeIf { !it.isStarImport }
                            ?.path
                            ?.takeIf { it.kind == PathKind.IDENTIFIER }
                            ?.referenceName
                            ?: continue

                        map.getOrPut(TyFingerprint(parentUseSpeckName)) { mutableListOf() } += aliasedName
                    }
                }
            }
            return map
        }
    }

    override fun getKeyDescriptor(): KeyDescriptor<TyFingerprint> = TyFingerprint.KeyDescriptor

    override fun getValueExternalizer(): DataExternalizer<List<String>> = StringCollectionExternalizer.STRING_LIST_EXTERNALIZER

    override fun getVersion(): Int = RsFileStub.Type.stubVersion

    override fun getInputFilter(): FileBasedIndex.InputFilter = DefaultFileTypeSpecificInputFilter(RsFileType)

    override fun dependsOnFileContent(): Boolean = true

    /**
     * Hacky adjust the file limit for Rust file.
     * Coupled with [org.rust.lang.core.psi.RsFileViewProviderFactory]
     */
    override fun getFileTypesWithSizeLimitNotApplicable(): Collection<FileType> {
        return listOf(RsFileType)
    }

    companion object {
        fun findPotentialAliases(
            project: Project,
            tyf: TyFingerprint,
        ): List<String> {
            val result = hashSetOf<String>()
            FileBasedIndex.getInstance().processValues(
                KEY,
                tyf,
                null,
                { file, value ->
                    val psi = file.toPsiFile(project) as? RsFile
                    if (psi != null) {
                        val crates = psi.crates
                        if (crates.isNotEmpty()) {
                            result += value
                        }
                    }
                    true
                },
                RsWithMacrosProjectScope(project)
            )
            return result.toList()
        }

        private fun getStubTree(inputData: FileContent): StubTree? {
            val rootStub = MacroExpansionStubsProvider.findStubForMacroExpansionFile(inputData)
                ?: StubTreeBuilder.buildStubTree(inputData)
            return if (rootStub is RsFileStub) StubTree(rootStub) else null
        }

        private val KEY: ID<TyFingerprint, List<String>> = ID.create("org.rust.lang.core.resolve.indexes.RsAliasIndex")
    }
}
