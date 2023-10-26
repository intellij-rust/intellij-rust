/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.feedback

import com.intellij.openapi.project.Project
import com.intellij.platform.feedback.dialog.BlockBasedFeedbackDialogWithEmail

@Suppress("UnstableApiUsage")
abstract class RsFeedbackDialogBase(
    project: Project?,
    forTest: Boolean
) : BlockBasedFeedbackDialogWithEmail<CommonFeedbackSystemData>(project, forTest)
