package org.rust.utils

/**
 * Common-use utility to designate whether particular operation was successful or not
 * without resort to the nullable-types
 */
interface Result<in T> {

    object Failure : Result<Any>
    data class Ok<T>(val result: T) : Result<T>

}


