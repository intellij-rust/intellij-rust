/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml

import com.intellij.patterns.PsiElementPattern
import com.intellij.patterns.StandardPatterns
import com.intellij.patterns.VirtualFilePattern
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
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
        return cargoTomlPsiElement<PsiElement>().inside(
            cargoTomlPsiElement<TomlKeyValue>()
                .withChild(
                    cargoTomlPsiElement<TomlKey>().withText(key)
                )
        )
    }

    private val onWorkspaceTableHeader: PsiElementPattern.Capture<TomlTableHeader> =
        cargoTomlPsiElement<TomlTableHeader>()
            .with("workspaceCondition") { header ->
                header.names.lastOrNull()?.text == "workspace"
            }

    private val onWorkspaceTable: PsiElementPattern.Capture<TomlTable> =
        cargoTomlPsiElement<TomlTable>()
            .withChild(onWorkspaceTableHeader)

    val inWorkspaceKeyWithPathValue: PsiElementPattern.Capture<TomlValue> =
        cargoTomlPsiElement<TomlValue>()
            .inside(psiElement<TomlKeyValue>(TOML_KEY_VALUE_CONTEXT_NAME))
            .withParent(TomlArray::class.java)
            .with("workspaceCondition") { tomlValue, context ->
                onWorkspaceKeyWithPathValue.accepts(tomlValue.parentOfType<TomlKeyValue>()?.key)
            }


    private val onWorkspaceKeyWithPathValue: PsiElementPattern.Capture<TomlKey> =
        cargoTomlPsiElement<TomlKey>()
            .withSuperParent(
                2,
                onWorkspaceTable
            )
            .with("workspaceKeyNameWithPathValue") { key ->
                key.text == "members" || key.text == "default-members" || key.text == "exclude"
            }


    private val onPackageTableHeader: PsiElementPattern.Capture<TomlTableHeader> =
        cargoTomlPsiElement<TomlTableHeader>()
            .with("packageCondition") { header ->
                header.names.lastOrNull()?.text == "package"
            }

    private val onPackageTable: PsiElementPattern.Capture<TomlTable> =
        cargoTomlPsiElement<TomlTable>()
            .withChild(onPackageTableHeader)

    val inLicenseFileKeyValue: PsiElementPattern.Capture<PsiElement> =
        cargoTomlPsiElement<PsiElement>()
            .inside(psiElement<TomlKeyValue>(TOML_KEY_VALUE_CONTEXT_NAME))
            .with("licenseFileKeyValueCondition") { _, context ->
                val keyValue = context?.get(TOML_KEY_VALUE_CONTEXT_NAME) as? TomlKeyValue ?: return@with false
                onLicenseFileKey.accepts(keyValue.key)
            }

    private val onLicenseFileKey: PsiElementPattern.Capture<TomlKey> =
        cargoTomlPsiElement<TomlKey>()
            .withSuperParent(2, onPackageTable)
            .with("licenseFileCondition") { key ->
                key.text == "license-file"
            }

    val inBuildKeyValue: PsiElementPattern.Capture<PsiElement> =
        cargoTomlPsiElement<PsiElement>()
            .inside(psiElement<TomlKeyValue>(TOML_KEY_VALUE_CONTEXT_NAME))
            .with("buildKeyValueCondition") { _, context ->
                val keyValue = context?.get(TOML_KEY_VALUE_CONTEXT_NAME) as? TomlKeyValue ?: return@with false
                inBuildKey.accepts(keyValue.key)
            }

    private val inBuildKey: PsiElementPattern.Capture<TomlKey> =
        cargoTomlPsiElement<TomlKey>()
            .withSuperParent(2, onPackageTable)
            .with("buildKeyCondition") { key ->
                key.text == "build"
            }

    val inWorkspaceKeyValue: PsiElementPattern.Capture<PsiElement> =
        cargoTomlPsiElement<PsiElement>()
            .inside(psiElement<TomlKeyValue>(TOML_KEY_VALUE_CONTEXT_NAME))
            .with("workspaceKeyValueCondition") { _, context ->
                val keyValue = context?.get(TOML_KEY_VALUE_CONTEXT_NAME) as? TomlKeyValue ?: return@with false
                onWorkspaceKey.accepts(keyValue.key)
            }

    private val onWorkspaceKey: PsiElementPattern.Capture<TomlKey> =
        cargoTomlPsiElement<TomlKey>()
            .inside(onWorkspaceTable)

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
}
