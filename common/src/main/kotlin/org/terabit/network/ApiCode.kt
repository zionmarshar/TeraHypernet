package org.terabit.network

enum class ApiCode(val code: Int, val desc: String) {
    SUCCESS(0, "Success"),
    ERROR(1, "Error"),
    PARAM_ERROR(2, "Parameter error"),
    NOT_FOUND(3, "Not found"),
    NOT_ALLOWED(4, "Not allowed"),
    EMPTY(5, "Empty"),
    USER_LOCKED(6, "User locked"),
    FETCH_ERROR(7, "Origin error"),
    NOT_INIT(8, "Not init"),
    NOT_JSON(9, "Not json string"),
    NOT_STARTED(10, "Not started yet"),
    DATA_ERROR(11, "Data error"),
    NUMBER_LIMITED(12, "Number limited"),
    SERVER_ERROR(500, "Server error");
}
