package com.example.todo_list.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button

import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.todo_list.R
import com.example.todo_list.model.Task
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class TaskAdapter(private val tasks: MutableList<Task>, private val deleteListener: (Task) -> Unit) :
    RecyclerView.Adapter<TaskAdapter.TaskViewHolder>() {

    inner class TaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val taskTitle: TextView = itemView.findViewById(R.id.taskTitle)
        val taskDescription : TextView = itemView.findViewById(R.id.taskDescriptionText)
        val expiryDate: TextView = itemView.findViewById(R.id.expiryDate)
        val deleteButton: Button = itemView.findViewById(R.id.deleteButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_task, parent, false)
        return TaskViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val task = tasks[position]

        val expiryDateTimestamp = task.expiryDate

        if (expiryDateTimestamp != null) {
            val calendar = Calendar.getInstance()
            calendar.time = expiryDateTimestamp.toDate() // Convert Timestamp to Date
            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) // Customize your format as needed
            val formattedDate = dateFormat.format(calendar.time) // Format the date to a string
            holder.expiryDate.text = formattedDate // Display formatted date in the TextView
        } else {
            holder.expiryDate.text = "No expiry date set"
        }

        holder.taskTitle.text = task.title
        holder.taskDescription.text = task.description

        holder.deleteButton.setOnClickListener {
            deleteListener(task)
        }
    }

    override fun getItemCount(): Int = tasks.size

    fun addTask(task: Task) {
        tasks.add(task)
        notifyItemInserted(tasks.size - 1)
    }

    fun deleteTask(task: Task) {
        val position = tasks.indexOf(task)
        if (position != -1) {
            tasks.removeAt(position)
            notifyItemRemoved(position)
        }
    }
}
