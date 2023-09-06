/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package intellij_rust

enum class IntelliJPlatform(
    /**
     * IDE versions can be found in the following repos:
     * * https://www.jetbrains.com/intellij-repository/releases/
     * * https://www.jetbrains.com/intellij-repository/snapshots/
     */
    val ideaVersion: String,
    val clionVersion: String,

    /** https://plugins.jetbrains.com/plugin/12775-native-debugging-support/versions */
    val nativeDebugPlugin: String,
    /** https://plugins.jetbrains.com/plugin/227-psiviewer/versions */
    val psiViewerPlugin: String,

    /** please see https://www.jetbrains.org/intellij/sdk/docs/basics/getting_started/build_number_ranges.html for description */
    val sinceBuild: String,
    val untilBuild: String,
) {

    Version231(
        ideaVersion = "IU-2023.1",
        clionVersion = "CL-2023.1",
        nativeDebugPlugin = "com.intellij.nativeDebug:231.8109.91",
        psiViewerPlugin = "PsiViewer:231-SNAPSHOT",
        sinceBuild = "231.7515",
        untilBuild = "231.*",
    ),

    Version232(
        ideaVersion = "IU-2023.2",
        clionVersion = "CL-2023.2",
        nativeDebugPlugin = "com.intellij.nativeDebug:232.8660.142",
        psiViewerPlugin = "PsiViewer:232.2",
        sinceBuild = "232.8296",
        untilBuild = "233.*",
    ),
    ;

    val versionNumber: Int get() = name.substringAfter("Version").toInt()

    companion object {
        fun fromVersionNumber(value: Int): IntelliJPlatform =
            values().firstOrNull { it.versionNumber == value }
                ?: error("no IntelliJPlatform found for version '$value'")
    }
}
