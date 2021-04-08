/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml

import com.intellij.patterns.PsiElementPattern
import com.intellij.patterns.StandardPatterns
import com.intellij.patterns.VirtualFilePattern
import com.intellij.psi.PsiElement
import org.rust.cargo.CargoConstants
import org.rust.lang.core.or
import org.rust.lang.core.psiElement
import org.rust.lang.core.with
import org.toml.lang.psi.*
import org.toml.lang.psi.ext.TomlLiteralKind
import org.toml.lang.psi.ext.kind
import org.toml.lang.psi.ext.name

object CargoTomlPsiPattern {
    private const val TOML_KEY_CONTEXT_NAME = "key"
    private const val TOML_KEY_VALUE_CONTEXT_NAME = "keyValue"
    private val PACKAGE_URL_ATTRIBUTES = setOf("homepage", "repository", "documentation")

    private inline fun <reified I : PsiElement> cargoTomlPsiElement(): PsiElementPattern.Capture<I> {
        return psiElement<I>().inVirtualFile(
            StandardPatterns.or(
                VirtualFilePattern().withName(CargoConstants.MANIFEST_FILE),
                VirtualFilePattern().withName(CargoConstants.XARGO_MANIFEST_FILE)
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
            .withParent(TomlKeySegment::class.java)

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
    val onDependencyKey: PsiElementPattern.Capture<TomlKeySegment> =
        cargoTomlPsiElement<TomlKeySegment>()
            .withSuperParent(
                3,
                onDependencyTable
            )

    private val onSpecificDependencyTable: PsiElementPattern.Capture<TomlTable> =
        cargoTomlPsiElement<TomlTable>()
            .withChild(
                psiElement<TomlTableHeader>()
                    .with("specificDependencyCondition") { header, _ ->
                        header.isSpecificDependencyTableHeader
                    }
            )

    /**
     * ```
     * [dependencies.regex]
     *                 ^
     * ```
     */
    val onSpecificDependencyHeaderKey: PsiElementPattern.Capture<TomlKeySegment> =
        cargoTomlPsiElement<TomlKeySegment>(TOML_KEY_CONTEXT_NAME)
            .withSuperParent(
                2,
                psiElement<TomlTableHeader>()
                    .with("specificDependencyCondition") { header, context ->
                        val key = context?.get(TOML_KEY_CONTEXT_NAME) ?: return@with false
                        val names = header.key?.segments.orEmpty()
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
    val path: PsiElementPattern.Capture<TomlLiteral> = cargoTomlStringLiteral()
        .withParent(tomlKeyValue("path"))

    /**
     * ```
     * [package]
     * build = "build.rs"
     *           #^
     * ```
     */
    val buildPath: PsiElementPattern.Capture<TomlLiteral> = cargoTomlStringLiteral().withParent(
        tomlKeyValue("build").withParent(tomlTable("package"))
    )

    /**
     * ```
     * [features]
     * foo = []
     *      #^
     * ```
     */
    private val onFeatureDependencyArray: PsiElementPattern.Capture<TomlArray> = psiElement<TomlArray>()
        .withSuperParent(1, psiElement<TomlKeyValue>())
        .withSuperParent(2, tomlTable("features"))

    val inFeatureDependencyArray: PsiElementPattern.Capture<PsiElement> = cargoTomlPsiElement<PsiElement>()
        .inside(onFeatureDependencyArray)

    /**
     * ```
     * [features]
     * foo = []
     * bar = [ "foo" ]
     *         #^
     * ```
     */
    val onFeatureDependencyLiteral: PsiElementPattern.Capture<TomlLiteral> = cargoTomlStringLiteral()
        .withParent(onFeatureDependencyArray)


    /**
     * ```
     * [dependencies]
     * foo = { bar = [] }
     *         #^
     * ```
     *
     * ```
     * [dependencies.foo]
     * bar = []
     * #^
     * ```
     */
    fun dependencyProperty(name: String): PsiElementPattern.Capture<TomlKeyValue> = psiElement<TomlKeyValue>()
        .with("name") { e, _ -> e.key.name == name }
        .withParent(
            psiElement<TomlInlineTable>().withSuperParent(2, onDependencyTable)
                or onSpecificDependencyTable
        )

    /**
     * ```
     * [dependencies]
     * foo = { version = "*", features = [] }
     *                                  #^
     * ```
     *
     * ```
     * [dependencies.foo]
     * features = []
     *           #^
     * ```
     */
    private val onDependencyPackageFeatureArray = psiElement<TomlArray>()
        .withParent(dependencyProperty("features"))

    val inDependencyPackageFeatureArray: PsiElementPattern.Capture<PsiElement> = cargoTomlPsiElement<PsiElement>()
        .inside(onDependencyPackageFeatureArray)

    /**
     * ```
     * [dependencies]
     * foo = { version = "" }
     *                  #^
     * ```
     */
    val inDependencyInlineTableVersion: PsiElementPattern.Capture<PsiElement> = cargoTomlPsiElement<PsiElement>()
        .inside(cargoTomlStringLiteral().withParent(dependencyProperty("version")))

    /**
     * ```
     * [dependencies]
     * foo = { version = "*", features = ["bar"] }
     *                                    #^
     * ```
     *
     * ```
     * [dependencies.foo]
     * features = ["bar"]
     *             #^
     * ```
     */
    val onDependencyPackageFeature: PsiElementPattern.Capture<TomlLiteral> = cargoTomlStringLiteral()
        .withParent(
            onDependencyPackageFeatureArray
        )

    val dependencyGitUrl: PsiElementPattern.Capture<TomlLiteral> = cargoTomlStringLiteral()
        .withParent(dependencyProperty("git"))

    val packageUrl: PsiElementPattern.Capture<TomlLiteral> = cargoTomlStringLiteral()
        .withParent(
            psiElement<TomlKeyValue>()
                .withParent(tomlTable("package"))
                .with("name") { e, _ -> e.key.name in PACKAGE_URL_ATTRIBUTES }
        )

    private fun cargoTomlStringLiteral() = cargoTomlPsiElement<TomlLiteral>()
        .with("stringLiteral") { e, _ -> e.kind is TomlLiteralKind.String }

    private fun tomlKeyValue(key: String): PsiElementPattern.Capture<TomlKeyValue> =
        psiElement<TomlKeyValue>().withChild(
            psiElement<TomlKey>().withText(key)
        )

    private fun tomlTable(key: String): PsiElementPattern.Capture<TomlTable> {
        return psiElement<TomlTable>().with("WithName ($key)") { e ->
            val names = e.header.key?.segments.orEmpty()
            names.singleOrNull()?.textMatches(key) == true
        }
    }
}
