package com.github.search5.yona.domain.enumeration

enum class SearchType(val type: String) {
    AUTO("auto"),
    NA("not available"),
    USER("user"),
    PROJECT("project"),
    ISSUE("issue"),
    POST("post"),
    MILESTONE("milestone"),
    ISSUE_COMMENT("issue_comment"),
    POST_COMMENT("post_comment"),
    REVIEW("review");

    companion object {
        fun getValue(value: String): SearchType {
            return values().find { it.type == value } ?: NA
        }
    }
}
