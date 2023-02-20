/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EnumEventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsMacroDefinitionBase

@Suppress("UnstableApiUsage")
class RsCodeVisionUsageCollector : CounterUsagesCollector() {
    override fun getGroup(): EventLogGroup = GROUP

    companion object {
        fun logAuthorClicked(element: PsiElement) {
            val location = Location.from(element)
            AUTHOR_CLICKED_EVENT.log(element.project, location)
        }

        fun logUsagesClicked(element: PsiElement) {
            val location = Location.from(element)
            USAGES_CLICKED_EVENT.log(element.project, location)
        }

        private val GROUP = EventLogGroup("rust.code.vision", 1)

        private enum class Location {
            FUNCTION,
            STRUCT_ITEM,
            ENUM_ITEM,
            ENUM_VARIANT,
            NAMED_FIELD_DECL,
            TRAIT_ITEM,
            IMPL_ITEM,
            TYPE_ALIAS,
            CONSTANT,
            MACRO_DEF,
            MOD_ITEM,
            UNKNOWN;

            companion object {
                fun from(element: PsiElement): Location {
                    return when (element) {
                        is RsFunction -> FUNCTION
                        is RsStructItem -> STRUCT_ITEM
                        is RsEnumItem -> ENUM_ITEM
                        is RsEnumVariant -> ENUM_VARIANT
                        is RsNamedFieldDecl -> NAMED_FIELD_DECL
                        is RsTraitItem -> TRAIT_ITEM
                        is RsImplItem -> IMPL_ITEM
                        is RsTypeAlias -> TYPE_ALIAS
                        is RsConstant -> CONSTANT
                        is RsMacroDefinitionBase -> MACRO_DEF
                        is RsModItem -> MOD_ITEM
                        else -> UNKNOWN
                    }
                }
            }
        }

        private val LOCATION_FIELD = EventFields.Enum<Location>("location") { it.name.lowercase() }

        private val AUTHOR_CLICKED_EVENT = GROUP.registerEvent("author.clicked", LOCATION_FIELD)
        private val USAGES_CLICKED_EVENT = GROUP.registerEvent("usages.clicked", LOCATION_FIELD)
    }
}
