package org.toml.lang

private val CAMEL_TO_SNAKE_REGEX = "(?=[A-Z])".toRegex()

fun camelToSnake(camelCaseName: String): String =
        camelCaseName
                .split(CAMEL_TO_SNAKE_REGEX)
                .map(String::toLowerCase)
                .joinToString("_")

