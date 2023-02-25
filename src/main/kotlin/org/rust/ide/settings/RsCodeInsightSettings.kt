/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.util.xmlb.XmlSerializerUtil

@State(name = "RsCodeInsightSettings", storages = [Storage("rust.xml")])
class RsCodeInsightSettings : PersistentStateComponent<RsCodeInsightSettings>, Disposable {

    var showImportPopup: Boolean = false
    var importOutOfScopeItems: Boolean = true
    var suggestOutOfScopeItems: Boolean = true
    var addUnambiguousImportsOnTheFly: Boolean = false
    var importOnPaste: Boolean = false
    private var excludedPaths: Array<ExcludedPath>? = null

    fun getExcludedPaths(): Array<ExcludedPath> = excludedPaths ?: DEFAULT_EXCLUDED_PATHS

    fun setExcludedPaths(value: Array<ExcludedPath>) {
        excludedPaths = if (DEFAULT_EXCLUDED_PATHS.contentEquals(value)) null else value
    }

    override fun getState(): RsCodeInsightSettings = this

    override fun loadState(state: RsCodeInsightSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    override fun dispose() {}

    companion object {
        fun getInstance(): RsCodeInsightSettings = service()

        private val DEFAULT_EXCLUDED_PATHS: Array<ExcludedPath> = arrayOf(
            // These imports interfere with `RefCell::borrow` & `RefCell::borrow_mut` and methods from
            // them are very rarely needed (mostly inside a `HashMap` implementations).
            // See https://github.com/intellij-rust/intellij-rust/issues/5805
            ExcludedPath("std::borrow::Borrow", ExclusionType.Methods),
            ExcludedPath("std::borrow::BorrowMut", ExclusionType.Methods),
            ExcludedPath("core::borrow::Borrow", ExclusionType.Methods),
            ExcludedPath("core::borrow::BorrowMut", ExclusionType.Methods),
            ExcludedPath("alloc::borrow::Borrow", ExclusionType.Methods),
            ExcludedPath("alloc::borrow::BorrowMut", ExclusionType.Methods),
            // Functions from this module are often suggested instead of `panic!()` macro, also
            // it is always unstable (with a stable alternative - `panic!()` macro).
            // See https://github.com/intellij-rust/intellij-rust/issues/9157
            ExcludedPath("core::panicking::*"),
            // This method is often suggested in completion instead of `unreachable!()` macro, also
            // it is always unstable (with a stable alternative - `core::hint::unreachable_unchecked`)
            ExcludedPath("std::intrinsics::unreachable"),
        )
    }
}

// must have default constructor and mutable fields for deserialization
class ExcludedPath(var path: String = "", var type: ExclusionType = ExclusionType.ItemsAndMethods)
enum class ExclusionType { ItemsAndMethods, Methods }
