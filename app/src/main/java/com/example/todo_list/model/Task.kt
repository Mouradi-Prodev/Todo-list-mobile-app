package com.example.todo_list.model

data class Task(
    val id: String,
    val title: String,
    val userId: String
){
    constructor() : this("", "","")
}
