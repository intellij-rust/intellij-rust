/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.stubs.index

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.IndexSink
import com.intellij.psi.stubs.StringStubIndexExtension
import com.intellij.psi.stubs.StubIndexKey
import org.rust.lang.core.psi.RsInnerAttr
import org.rust.lang.core.psi.ext.name
import org.rust.lang.core.stubs.RsFileStub
import org.rust.lang.core.stubs.RsInnerAttrStub
import org.rust.openapiext.checkCommitIsNotInProgress
import org.rust.openapiext.getElements

class RsFeatureIndex : StringStubIndexExtension<RsInnerAttr>() {

    override fun getKey(): StubIndexKey<String, RsInnerAttr> = KEY
    override fun getVersion(): Int = RsFileStub.Type.stubVersion

    companion object {
        private val KEY: StubIndexKey<String, RsInnerAttr> =
            StubIndexKey.createIndexKey("org.rust.lang.core.stubs.index.RsFeatureIndex")

        fun index(stub: RsInnerAttrStub, sink: IndexSink) {
            val metaItem = stub.psi.metaItem
            if (metaItem.name == "feature") {
                val features = metaItem.metaItemArgs?.metaItemList.orEmpty()
                for (feature in features) {
                    val featureName = feature.name ?: continue
                    sink.occurrence(KEY, featureName)
                }
            }
        }

        fun getFeatureAttributes(project: Project, featureName: String): Collection<RsInnerAttr> {
            checkCommitIsNotInProgress(project)
            return getElements(KEY, featureName, project, GlobalSearchScope.allScope(project))
        }
    }
}
