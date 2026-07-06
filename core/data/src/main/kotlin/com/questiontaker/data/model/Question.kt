package com.questiontaker.data.model

data class Question(
    val id: Int,
    val question: String,
    val options: List<Option>,
    val answer: String,
    val explanation: String,
    val sources: List<String>
)

data class Option(
    val key: String,
    val text: String
)
