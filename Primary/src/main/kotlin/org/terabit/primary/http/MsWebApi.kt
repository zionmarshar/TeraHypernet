package org.terabit.primary.http

@Target(AnnotationTarget.CLASS, AnnotationTarget.FILE)
@kotlin.annotation.Retention(AnnotationRetention.RUNTIME)
annotation class MsWebApi(
    val name: String = ""
    , val method: String = ""
    , val right: Int = 0
)
