package com.example.todo_list.model

import com.google.firebase.Timestamp


data class Task(
    val id: String,
    val title: String,
    val description: String,
    val expiryDate: Timestamp? = null,
    val userId: String
){
    constructor() : this("", "","", null,"")
}
