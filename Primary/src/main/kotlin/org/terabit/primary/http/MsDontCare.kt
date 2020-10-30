package org.terabit.primary.http

/**
 * This method has returned the result to the front end when it fails.
 * If there is no return value, it means that the result has
 * been returned to the front end without any processing.
 */
@Target(AnnotationTarget.FUNCTION)
@kotlin.annotation.Retention(AnnotationRetention.SOURCE)
annotation class MsDontCare()