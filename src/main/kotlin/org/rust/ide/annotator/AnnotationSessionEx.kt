/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.AnnotationSession
import com.intellij.openapi.util.Key
import com.intellij.util.containers.orNull
import org.rust.lang.core.crate.Crate
import org.rust.lang.core.crate.asNotFake
import org.rust.lang.core.psi.AttrCache
import org.rust.lang.core.psi.RsFile
import org.rust.openapiext.getOrPut
import java.util.*

private val CRATE_KEY = Key<Optional<Crate>>("org.rust.ide.annotator.currentCrate")
private val ATTR_CACHE_KEY = Key<AttrCache>("org.rust.ide.annotator.attrCache")

fun AnnotationSession.currentCrate(): Crate? {
    return getOrPut(CRATE_KEY) {
        Optional.ofNullable((file as? RsFile)?.crate?.asNotFake)
    }.orNull()
}

fun AnnotationSession.setCurrentCrate(crate: Crate?) {
    putUserData(CRATE_KEY, Optional.ofNullable(crate))
}

fun AnnotationHolder.currentCrate(): Crate? = currentAnnotationSession.currentCrate()

fun AnnotationHolder.attrCache(): AttrCache {
    val session = currentAnnotationSession
    return session.getOrPut(ATTR_CACHE_KEY) {
        AttrCache.HashMapCache(session.currentCrate())
    }
}
