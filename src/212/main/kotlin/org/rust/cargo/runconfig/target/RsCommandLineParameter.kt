/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.target

import com.intellij.execution.target.value.TargetValue
import com.intellij.openapi.util.text.StringUtil
import java.nio.file.Path

interface RsCommandLineParameter {

    fun prepare(setup: RsCommandLineSetup) {}

    fun toValue(): TargetValue<String>

    fun toPresentableString(): String

    class StringParameter(private val value: String) : RsCommandLineParameter {

        override fun toValue(): TargetValue<String> = TargetValue.fixed(value)

        override fun toPresentableString(): String = value
    }

    class PathParameter(private val path: String) : RsCommandLineParameter {
        @Volatile
        private lateinit var value: TargetValue<String>

        override fun prepare(setup: RsCommandLineSetup) {
            value = setup.requestUploadIntoTarget(path)
        }

        override fun toValue(): TargetValue<String> = value

        override fun toPresentableString(): String = path
    }

    class DownloadFromTargetParameter(
        private val pathInVolume: String,
        volumeRoot: String?,
        private val localPath: Path?
    ) : RsCommandLineParameter {
        private val volumeRoot: String? = StringUtil.nullize(volumeRoot)

        @Volatile
        private lateinit var targetValue: TargetValue<String>

        override fun prepare(setup: RsCommandLineSetup) {
            targetValue = setup.requestDownloadFromTarget(volumeRoot, pathInVolume, localPath)
        }

        override fun toValue(): TargetValue<String> = targetValue

        override fun toPresentableString(): String = pathInVolume
    }
}
