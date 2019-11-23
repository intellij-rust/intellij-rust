/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml

import com.intellij.patterns.PsiElementPattern
import com.intellij.patterns.StandardPatterns
import com.intellij.patterns.VirtualFilePattern
import com.intellij.psi.PsiElement
import org.rust.cargo.toolchain.RustToolchain
import org.rust.lang.core.psiElement
import org.rust.lang.core.with
import org.toml.lang.psi.*

object CargoTomlPsiPattern {
    private const val TOML_KEY_CONTEXT_NAME = "key"
    private const val TOML_KEY_VALUE_CONTEXT_NAME = "keyValue"

    private inline fun <reified I : PsiElement> cargoTomlPsiElement(): PsiElementPattern.Capture<I> {
        return psiElement<I>().inVirtualFile(
            StandardPatterns.or(
                VirtualFilePattern().withName(RustToolchain.CARGO_TOML),
                VirtualFilePattern().withName(RustToolchain.XARGO_TOML)
            )
        )
    }

    private inline fun <reified I : PsiElement> cargoTomlPsiElement(contextName: String): PsiElementPattern.Capture<I> {
        return cargoTomlPsiElement<I>().with("putIntoContext") { e, context ->
            context?.put(contextName, e)
            true
        }
    }

    /** Any element inside any TomlKey in Cargo.toml */
    val inKey: PsiElementPattern.Capture<PsiElement> =
        cargoTomlPsiElement<PsiElement>()
            .withParent(TomlKey::class.java)

    fun inValueWithKey(key: String): PsiElementPattern.Capture<PsiElement> {
        return cargoTomlPsiElement<PsiElement>().inside(tomlKeyValue(key))
    }

    private val onDependencyTableHeader: PsiElementPattern.Capture<TomlTableHeader> =
        cargoTomlPsiElement<TomlTableHeader>()
            .with("dependenciesCondition") { header ->
                header.isDependencyListHeader
            }

    private val onDependencyTable: PsiElementPattern.Capture<TomlTable> =
        cargoTomlPsiElement<TomlTable>()
            .withChild(onDependencyTableHeader)

    /**
     * ```
     * [dependencies]
     * regex = "1"
     *   ^
     * ```
     */
    val onDependencyKey: PsiElementPattern.Capture<TomlKey> =
        cargoTomlPsiElement<TomlKey>()
            .withSuperParent(
                2,
                onDependencyTable
            )

    private val onSpecificDependencyTable: PsiElementPattern.Capture<TomlTable> =
        cargoTomlPsiElement<TomlTable>()
            .withChild(
                psiElement<TomlTableHeader>()
                    .with("specificDependencyCondition") { header, _ ->
                        val names = header.names
                        names.getOrNull(names.size - 2)?.isDependencyKey == true
                    }
            )

    /**
     * ```
     * [dependencies.regex]
     *                 ^
     * ```
     */
    val onSpecificDependencyHeaderKey: PsiElementPattern.Capture<TomlKey> =
        cargoTomlPsiElement<TomlKey>(TOML_KEY_CONTEXT_NAME)
            .withParent(
                psiElement<TomlTableHeader>()
                    .with("specificDependencyCondition") { header, context ->
                        val key = context?.get(TOML_KEY_CONTEXT_NAME) ?: return@with false
                        val names = header.names
                        names.getOrNull(names.size - 2)?.isDependencyKey == true && names.lastOrNull() == key
                    }
            )

    /** Any element inside [onSpecificDependencyHeaderKey] */
    val inSpecificDependencyHeaderKey: PsiElementPattern.Capture<PsiElement> =
        cargoTomlPsiElement<PsiElement>()
            .withParent(onSpecificDependencyHeaderKey)

    /**
     * Any element inside [onDependencyKeyValue].
     * This pattern doesn't permit nested keyValues, e.g. false for
     * ```
     * [dependencies]
     * regex = { version = "1" }
     *                   ^
     * ```
     */
    val inDependencyKeyValue: PsiElementPattern.Capture<PsiElement> =
        cargoTomlPsiElement<PsiElement>()
            .inside(psiElement<TomlKeyValue>(TOML_KEY_VALUE_CONTEXT_NAME))
            .with("dependencyKeyValueCondition") { _, context ->
                val keyValue = context?.get(TOML_KEY_VALUE_CONTEXT_NAME) ?: return@with false
                onDependencyKeyValue.accepts(keyValue)
            }

    /**
     * ```
     * [dependencies]
     * regex = "1"
     *       ^
     * ```
     */
    private val onDependencyKeyValue: PsiElementPattern.Capture<TomlKeyValue> =
        cargoTomlPsiElement<TomlKeyValue>()
            .withParent(onDependencyTable)

    /** Any element inside [onSpecificDependencyKeyValue] */
    val inSpecificDependencyKeyValue: PsiElementPattern.Capture<PsiElement> =
        cargoTomlPsiElement<PsiElement>()
            .inside(psiElement<TomlKeyValue>(TOML_KEY_VALUE_CONTEXT_NAME))
            .with("specificDependencyKeyValueCondition") { _, context ->
                val keyValue = context?.get(TOML_KEY_VALUE_CONTEXT_NAME) ?: return@with false
                onSpecificDependencyKeyValue.accepts(keyValue)
            }


    /**
     * ```
     * [dependencies.regex]
     * version = "1"
     *         ^
     * ```
     */
    private val onSpecificDependencyKeyValue: PsiElementPattern.Capture<TomlKeyValue> =
        cargoTomlPsiElement<TomlKeyValue>()
            .withParent(onSpecificDependencyTable)

    /**
     * ```
     * [workspace]
     * members = [ "path/to/crate" ]
     *               #^
     * ```
     */
    val workspacePath: PsiElementPattern.Capture<TomlLiteral> = cargoTomlPsiElement<TomlLiteral>().withParent(
        psiElement<TomlArray>().withParent(
            psiElement<TomlKeyValue>().withParent(
                tomlTable("workspace")
            )
        )
    )

    /**
     * ```
     * [package]
     * workspace = "path/to/root/crate"
     *                #^
     * ```
     */
    val packageWorkspacePath: PsiElementPattern.Capture<TomlLiteral> = cargoTomlPsiElement<TomlLiteral>().withParent(
        tomlKeyValue("workspace").withParent(tomlTable("package"))
    )

    /**
     * ```
     * [dependencies]
     * dependency_name = { path = "path/to/dependency" }
     *                                 #^
     * ```
     * or
     * ```
     * [package]
     * path = "path/to/file.rs"
     *             #^
     * ```
     */
    val path: PsiElementPattern.Capture<TomlLiteral> = cargoTomlPsiElement<TomlLiteral>()
        .withParent(tomlKeyValue("path"))

    /**
     * ```
     * [package]
     * build = "build.rs"
     *           #^
     * ```
     */
    val buildPath: PsiElementPattern.Capture<TomlLiteral> = cargoTomlPsiElement<TomlLiteral>().withParent(
        tomlKeyValue("build").withParent(tomlTable("package"))
    )

    private fun tomlKeyValue(key: String): PsiElementPattern.Capture<TomlKeyValue> =
        psiElement<TomlKeyValue>().withChild(
            psiElement<TomlKey>().withText(key)
        )

    private fun tomlTable(key: String): PsiElementPattern.Capture<TomlTable> {
        return psiElement<TomlTable>().with("WithName ($key)") { e ->
            val names = e.header.names
            names.singleOrNull()?.textMatches(key) == true
        }
    }
}
