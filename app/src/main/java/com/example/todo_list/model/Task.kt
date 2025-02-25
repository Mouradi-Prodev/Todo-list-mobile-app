package com.example.todo_list.model

import com.google.firebase.Timestamp

data class Task(
    val id: String,
    val title: String,
    val description: String,
    val expiryDate: Timestamp? = null,
    val userId: String,
    var isCompleted: Boolean = false,
    val order: Int = 0
) {
    constructor() : this("", "", "", null, "", false, 0)
    // Remove the custom copy method as data classes already have one
}
