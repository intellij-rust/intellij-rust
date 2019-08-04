/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

// BACKCOMPAT: 2019.1
@file:Suppress("DEPRECATION")

package org.rust.lang

import com.intellij.openapi.fileTypes.FileTypeConsumer
import com.intellij.openapi.fileTypes.FileTypeFactory

// BACKCOMPAT: 2019.1 (use `fileType` extension point instead)
class RsFileTypeFactory : FileTypeFactory() {

    override fun createFileTypes(consumer: FileTypeConsumer) {
        consumer.consume(RsFileType, RsFileType.DEFAULTS.EXTENSION)
    }

}
