package org.rust.remote

interface RsCredentialsContribution<T> {
    val isPackageManagementEnabled: Boolean
    val isSpecificCoverageAttach: Boolean
    val isSpecificCoveragePatch: Boolean
    val isRemoteProcessStartSupported: Boolean
    fun isValid(credentials: T?): Boolean
}
