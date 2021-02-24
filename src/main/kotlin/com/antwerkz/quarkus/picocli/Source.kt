package com.antwerkz.quarkus.picocli

enum class Source {
    JAVA {
        override fun ext(): String {
            return "java"
        }
    },
    KOTLIN {
        override fun ext(): String {
            return "kt"
        }
    };

    abstract fun ext(): String
    fun dir(type: String): String {
        return "src/$type/${name.toLowerCase()}"
    }
}
