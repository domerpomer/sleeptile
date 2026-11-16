package com.example.sleeptile

/** Урок, выбранный для конкретного ученика (с учётом класса и подгруппы), с точным временем. */
data class ResolvedLesson(
    val n: Int,
    val subject: String,
    val room: String,
    val start: Long, // мс, Unix-время
    val end: Long
)

enum class EvType { PRE, START, END }

data class SchoolEvent(
    val at: Long,
    val type: EvType,
    val lesson: ResolvedLesson,
    val next: ResolvedLesson?
)
