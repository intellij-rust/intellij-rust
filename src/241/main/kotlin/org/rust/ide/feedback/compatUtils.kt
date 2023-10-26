/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

@file:Suppress("UnstableApiUsage")

package org.rust.ide.feedback

import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.Panel


typealias CommonFeedbackSystemData = com.intellij.platform.feedback.dialog.CommonFeedbackSystemData
typealias ThanksForFeedbackNotification = com.intellij.platform.feedback.impl.notification.ThanksForFeedbackNotification

typealias DescriptionBlock = com.intellij.platform.feedback.dialog.uiBlocks.DescriptionBlock
typealias FeedbackBlock = com.intellij.platform.feedback.dialog.uiBlocks.FeedbackBlock
typealias JsonDataProvider = com.intellij.platform.feedback.dialog.uiBlocks.JsonDataProvider
typealias RatingBlock = com.intellij.platform.feedback.dialog.uiBlocks.RatingBlock
typealias TextAreaBlock = com.intellij.platform.feedback.dialog.uiBlocks.TextAreaBlock
typealias TextDescriptionProvider = com.intellij.platform.feedback.dialog.uiBlocks.TextDescriptionProvider
typealias TopLabelBlock = com.intellij.platform.feedback.dialog.uiBlocks.TopLabelBlock

fun showFeedbackSystemInfoDialog(
    project: Project?,
    systemInfoData: CommonFeedbackSystemData,
    addSpecificRows: Panel.() -> Unit = {}
) = com.intellij.platform.feedback.dialog.showFeedbackSystemInfoDialog(project, systemInfoData, addSpecificRows)
