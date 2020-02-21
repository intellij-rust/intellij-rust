/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.remote.sdk

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import org.rust.ide.sdk.remote.RsRemoteSdkAdditionalData

fun createAndInitRemoteSdk(
    project: Project? = null,
    data: RsRemoteSdkAdditionalData,
    existingSdks: Collection<Sdk>,
    suggestedName: String? = null
): Sdk {
  val remoteSdkFactory = RsRemoteToolchainFactory.getInstance()
  // we do not pass `sdkName` so that `createRemoteSdk` generates it by itself
  val remoteSdk = remoteSdkFactory.createRemoteSdk(project, data, suggestedName, existingSdks)
  remoteSdkFactory.initSdk(remoteSdk, project, null)
  return remoteSdk
}
