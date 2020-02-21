/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk.add

enum class RsAddSdkDialogFlowAction {
    PREVIOUS, NEXT, FINISH, OK;

    fun enabled(): Pair<RsAddSdkDialogFlowAction, Boolean> = this to true
    fun disabled(): Pair<RsAddSdkDialogFlowAction, Boolean> = this to false
}
