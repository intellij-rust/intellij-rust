/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.stubs

import com.intellij.util.BitUtil
import org.rust.lang.core.psi.RS_BUILTIN_ATTRIBUTES
import org.rust.lang.core.psi.RS_BUILTIN_TOOL_ATTRIBUTES
import org.rust.lang.core.psi.RsMetaItem
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.KNOWN_DERIVABLE_TRAITS
import org.rust.lang.core.stubs.RsAttributeOwnerStub.CommonStubAttrFlags.HAS_ATTRS_MASK
import org.rust.lang.core.stubs.RsAttributeOwnerStub.CommonStubAttrFlags.HAS_CFG_ATTR_MASK
import org.rust.lang.core.stubs.RsAttributeOwnerStub.CommonStubAttrFlags.MAY_HAVE_CFG_MASK
import org.rust.lang.core.stubs.RsAttributeOwnerStub.CommonStubAttrFlags.MAY_HAVE_CUSTOM_ATTRS
import org.rust.lang.core.stubs.RsAttributeOwnerStub.CommonStubAttrFlags.MAY_HAVE_CUSTOM_DERIVE
import org.rust.lang.core.stubs.RsAttributeOwnerStub.FileStubAttrFlags.MAY_HAVE_RECURSION_LIMIT_MASK
import org.rust.lang.core.stubs.RsAttributeOwnerStub.FileStubAttrFlags.MAY_HAVE_STDLIB_ATTRIBUTES_MASK
import org.rust.lang.core.stubs.RsAttributeOwnerStub.FunctionStubAttrFlags.MAY_BE_PROC_MACRO_DEF
import org.rust.lang.core.stubs.RsAttributeOwnerStub.MacroStubAttrFlags.MAY_HAVE_MACRO_EXPORT
import org.rust.lang.core.stubs.RsAttributeOwnerStub.MacroStubAttrFlags.MAY_HAVE_MACRO_EXPORT_LOCAL_INNER_MACROS
import org.rust.lang.core.stubs.RsAttributeOwnerStub.ModStubAttrFlags.MAY_HAVE_MACRO_USE_MASK
import org.rust.lang.core.stubs.RsAttributeOwnerStub.UseItemStubAttrFlags.MAY_HAVE_PRELUDE_IMPORT_MASK
import org.rust.lang.core.stubs.common.RsAttrProcMacroOwnerPsiOrStub
import org.rust.lang.core.stubs.common.RsAttributeOwnerPsiOrStub
import org.rust.stdext.BitFlagsBuilder
import org.rust.stdext.HashCode

interface RsNamedStub {
    val name: String?
}

/**
 * These properties are stored in stubs for performance reasons: it's much cheaper to check
 * a flag in a stub then traverse a PSI
 */
interface RsAttributeOwnerStub : RsAttributeOwnerPsiOrStub<RsMetaItemStub> {
    val hasAttrs: Boolean

    // #[cfg()]
    val mayHaveCfg: Boolean

    // #[cfg_attr()]
    val hasCfgAttr: Boolean

    // #[derive(FooBar)]
    val mayHaveCustomDerive: Boolean

    // #[foobar]
    val mayHaveCustomAttrs: Boolean

    object CommonStubAttrFlags : BitFlagsBuilder(Limit.BYTE) {
        val HAS_ATTRS_MASK: Int = nextBitMask()
        val MAY_HAVE_CFG_MASK: Int = nextBitMask()
        val HAS_CFG_ATTR_MASK: Int = nextBitMask()
        val MAY_HAVE_CUSTOM_DERIVE: Int = nextBitMask()
        val MAY_HAVE_CUSTOM_ATTRS: Int = nextBitMask()
    }

    object ModStubAttrFlags : BitFlagsBuilder(CommonStubAttrFlags, Limit.BYTE) {
        val MAY_HAVE_MACRO_USE_MASK: Int = nextBitMask()
    }

    object FileStubAttrFlags : BitFlagsBuilder(ModStubAttrFlags, Limit.BYTE) {
        val MAY_HAVE_STDLIB_ATTRIBUTES_MASK: Int = nextBitMask()
        val MAY_HAVE_RECURSION_LIMIT_MASK: Int = nextBitMask()
    }

    object FunctionStubAttrFlags : BitFlagsBuilder(CommonStubAttrFlags, Limit.BYTE) {
        val MAY_BE_PROC_MACRO_DEF: Int = nextBitMask()
    }

    object UseItemStubAttrFlags : BitFlagsBuilder(CommonStubAttrFlags, Limit.BYTE) {
        val MAY_HAVE_PRELUDE_IMPORT_MASK: Int = nextBitMask()
    }

    object MacroStubAttrFlags : BitFlagsBuilder(CommonStubAttrFlags, Limit.BYTE) {
        val MAY_HAVE_MACRO_EXPORT: Int = nextBitMask()
        val MAY_HAVE_MACRO_EXPORT_LOCAL_INNER_MACROS: Int = nextBitMask()
        val MAY_HAVE_RUSTC_BUILTIN_MACRO: Int = nextBitMask()
    }

    object Macro2StubAttrFlags : BitFlagsBuilder(CommonStubAttrFlags, Limit.BYTE) {
        val MAY_HAVE_RUSTC_BUILTIN_MACRO: Int = nextBitMask()
    }

    companion object {
        fun extractFlags(element: RsDocAndAttributeOwner, bitflagsKind: Any = CommonStubAttrFlags): Int =
            extractFlags(element.getTraversedRawAttributes(withCfgAttrAttribute = true), bitflagsKind)

        fun extractFlags(attrs: QueryAttributes<*>, bitflagsKind: Any): Int {
            var hasAttrs = false
            var hasCfg = false
            var hasCfgAttr = false
            var hasCustomDerive = false
            var hasCustomAttrs = false
            var hasMacroUse = false
            var hasStdlibAttrs = false
            var hasRecursionLimit = false
            var isProcMacroDef = false
            var isPreludeImport = false
            var hasMacroExport = false
            var hasMacroExportLocalInnerMacros = false
            var hasRustcBuiltinMacro = false

            for (meta in attrs.metaItems) {
                hasAttrs = true
                val path = meta.path ?: continue
                if (path.hasColonColon) {
                    // `foo::bar` or `::foo`
                    val basePath = path.basePath()
                    if (basePath != path && basePath.referenceName !in RS_BUILTIN_TOOL_ATTRIBUTES) {
                        hasCustomAttrs = true
                    }
                } else {
                    when (path.referenceName) {
                        null -> Unit
                        "cfg" -> hasCfg = true
                        "cfg_attr" -> hasCfgAttr = true
                        "derive" -> {
                            hasCustomDerive = hasCustomDerive || meta.metaItemArgs?.metaItemList.orEmpty()
                                .any { KNOWN_DERIVABLE_TRAITS[it.name]?.isStd != true }
                        }
                        "macro_use" -> hasMacroUse = true
                        "no_std", "no_core" -> hasStdlibAttrs = true
                        "recursion_limit" -> hasRecursionLimit = true
                        "proc_macro", "proc_macro_attribute", "proc_macro_derive" -> isProcMacroDef = true
                        "prelude_import" -> isPreludeImport = true
                        "macro_export" -> {
                            hasMacroExport = true
                            hasMacroExportLocalInnerMacros = hasMacroExportLocalInnerMacros
                                || meta.metaItemArgsList.any { item -> item.name == "local_inner_macros" }
                        }
                        "rustc_builtin_macro" -> hasRustcBuiltinMacro = true
                        !in RS_BUILTIN_ATTRIBUTES -> hasCustomAttrs = true
                    }
                }
            }
            var flags = 0
            flags = BitUtil.set(flags, HAS_ATTRS_MASK, hasAttrs)
            flags = BitUtil.set(flags, MAY_HAVE_CFG_MASK, hasCfg)
            flags = BitUtil.set(flags, HAS_CFG_ATTR_MASK, hasCfgAttr)
            flags = BitUtil.set(flags, MAY_HAVE_CUSTOM_DERIVE, hasCustomDerive)
            flags = BitUtil.set(flags, MAY_HAVE_CUSTOM_ATTRS, hasCustomAttrs)

            when (bitflagsKind) {
                CommonStubAttrFlags -> Unit
                ModStubAttrFlags -> flags = BitUtil.set(flags, MAY_HAVE_MACRO_USE_MASK, hasMacroUse)
                FileStubAttrFlags -> {
                    flags = BitUtil.set(flags, MAY_HAVE_MACRO_USE_MASK, hasMacroUse)
                    flags = BitUtil.set(flags, MAY_HAVE_STDLIB_ATTRIBUTES_MASK, hasStdlibAttrs)
                    flags = BitUtil.set(flags, MAY_HAVE_RECURSION_LIMIT_MASK, hasRecursionLimit)
                }
                FunctionStubAttrFlags -> flags = BitUtil.set(flags, MAY_BE_PROC_MACRO_DEF, isProcMacroDef)
                UseItemStubAttrFlags -> flags = BitUtil.set(flags, MAY_HAVE_PRELUDE_IMPORT_MASK, isPreludeImport)
                MacroStubAttrFlags -> {
                    flags = BitUtil.set(flags, MAY_HAVE_MACRO_EXPORT, hasMacroExport)
                    flags = BitUtil.set(flags, MAY_HAVE_MACRO_EXPORT_LOCAL_INNER_MACROS, hasMacroExportLocalInnerMacros)
                    flags = BitUtil.set(flags, MacroStubAttrFlags.MAY_HAVE_RUSTC_BUILTIN_MACRO, hasRustcBuiltinMacro)
                }
                Macro2StubAttrFlags -> {
                    flags = BitUtil.set(flags, Macro2StubAttrFlags.MAY_HAVE_RUSTC_BUILTIN_MACRO, hasRustcBuiltinMacro)
                }
                else -> error("Unknown bitflags holder: $bitflagsKind")
            }

            return flags
        }
    }
}

/**
 * A common interface for stub for elements that can hold attribute or derive procedural macro attributes.
 *
 * @see RsAttrProcMacroOwner
 */
interface RsAttrProcMacroOwnerStub : RsAttributeOwnerStub, RsAttrProcMacroOwnerPsiOrStub<RsMetaItemStub> {
    /**
     * A text of the item ([com.intellij.psi.PsiElement.getText]). Used for proc macro expansion.
     * Non-null if [mayHaveCustomDerive] or [mayHaveCustomAttrs] is `true`
     */
    val stubbedText: String?

    /**
     * A [HashCode] of [stubbedText]. Non-null if [stubbedText] is not `null` and [hasCfgAttr] is `false`.
     * @see RsMetaItem.bodyHash
     */
    val stubbedTextHash: HashCode?

    /**
     * Text offset in parent ([com.intellij.psi.PsiElement.getStartOffsetInParent]) of the first keyword
     * after outer attributes. `0` if [stubbedText] is `null`.
     *
     * ```
     *     #[foobar]
     *     // comment
     *     /// docs
     *     #[baz]
     *     pub const fn a(){}
     *   //^ this offset
     * ```
     */
    val endOfAttrsOffset: Int

    /** Absolute text offset [com.intellij.psi.PsiElement.startOffset] of the element */
    val startOffset: Int

    companion object {
        fun extractTextAndOffset(flags: Int, psi: RsAttrProcMacroOwner): RsProcMacroStubInfo? {
            val isProcMacro = BitUtil.isSet(flags, MAY_HAVE_CUSTOM_DERIVE)
                || BitUtil.isSet(flags, MAY_HAVE_CUSTOM_ATTRS)
            return if (isProcMacro) {
                val stubbedText = psi.text // psi.stubbedText
                val hash = if (!BitUtil.isSet(flags, HAS_CFG_ATTR_MASK)) {
                    HashCode.compute(stubbedText)
                } else {
                    // We can calculate the hash during stub building only if the item does not contain
                    // `cfg_attr` attributes. Otherwise, the hash depends on cfg configuration.
                    null
                }
                RsProcMacroStubInfo(stubbedText, hash, psi.endOfAttrsOffset, psi.startOffset)
            } else {
                null
            }
        }
    }
}
