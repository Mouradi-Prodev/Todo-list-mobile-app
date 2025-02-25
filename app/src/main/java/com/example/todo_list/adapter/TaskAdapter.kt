package com.example.todo_list.adapter

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.todo_list.R
import com.example.todo_list.model.Task
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Collections

class TaskAdapter(
    private val tasks: MutableList<Task>,
    private val onDeleteClick: (Task) -> Unit,
    private val onEditClick: (Task) -> Unit,
    private val onTaskStatusChanged: (Task, Boolean) -> Unit,
    private val onTaskReordered: (List<Task>) -> Unit
) : RecyclerView.Adapter<TaskAdapter.TaskViewHolder>() {

    inner class TaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val checkbox: CheckBox = itemView.findViewById(R.id.taskCheckbox)
        val taskTitle: TextView = itemView.findViewById(R.id.taskTitle)
        val taskDescription: TextView = itemView.findViewById(R.id.taskDescriptionText)
        val expiryDate: TextView = itemView.findViewById(R.id.expiryDate)
        val editButton: MaterialButton = itemView.findViewById(R.id.editButton)
        val deleteButton: MaterialButton = itemView.findViewById(R.id.deleteButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_task, parent, false)
        return TaskViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val task = tasks[position]

        holder.apply {
            // 1. Remove the listener to prevent incorrect updates
            checkbox.setOnCheckedChangeListener(null)

            // 2. Set the initial state of the checkbox
            checkbox.isChecked = task.isCompleted

            // 3. Set the task title and description
            taskTitle.text = task.title
            taskDescription.text = task.description

            // 4. Update the text appearance based on completion status
            updateTextAppearance(taskTitle, taskDescription, task.isCompleted)

            // 5. Format and set the expiry date
            task.expiryDate?.let {
                val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                expiryDate.text = dateFormat.format(it.toDate())
            }

            // 6. Set the listener AFTER setting the initial state
            checkbox.setOnCheckedChangeListener { _, isChecked ->
                // 7. Check if the state has actually changed
                if (task.isCompleted != isChecked) {
                    // 8. Update the task status
                    onTaskStatusChanged(task, isChecked)

                    // 9. Update the text appearance
                    updateTextAppearance(taskTitle, taskDescription, isChecked)
                }
            }

            // 10. Set the edit and delete button listeners
            editButton.setOnClickListener { onEditClick(task) }
            deleteButton.setOnClickListener { onDeleteClick(task) }
        }
    }

    private fun updateTextAppearance(taskTitle: TextView, taskDescription: TextView, isCompleted: Boolean) {
        if (isCompleted) {
            taskTitle.paintFlags = taskTitle.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            taskDescription.paintFlags = taskDescription.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        } else {
            taskTitle.paintFlags = taskTitle.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            taskDescription.paintFlags = taskDescription.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        }
    }

    override fun getItemCount(): Int = tasks.size

    fun updateTasks(newTasks: List<Task>) {
        val oldSize = tasks.size
        tasks.clear()
        tasks.addAll(newTasks)
        
        if (oldSize < tasks.size) {
            notifyItemRangeInserted(oldSize, tasks.size - oldSize)
        } else if (oldSize > tasks.size) {
            notifyItemRangeRemoved(tasks.size, oldSize - tasks.size)
        }
        notifyItemRangeChanged(0, tasks.size)
    }

    fun addTask(task: Task) {
        tasks.add(task)
        notifyItemInserted(tasks.size - 1)
    }

    fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(tasks, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(tasks, i, i - 1)
            }
        }
        notifyItemMoved(fromPosition, toPosition)
        return true
    }

    fun onDragComplete() {
        // Update the order property of each task
        tasks.forEachIndexed { index, task ->
            tasks[index] = task.copy(order = index)
        }
        onTaskReordered(tasks.toList())
    }

    // Add sorting methods
    fun sortByDate() {
        tasks.sortBy { it.expiryDate }
        notifyDataSetChanged()
    }

    fun sortByTitle() {
        tasks.sortBy { it.title }
        notifyDataSetChanged()
    }

    fun sortByCompletion() {
        tasks.sortBy { it.isCompleted }
        notifyDataSetChanged()
    }

    // Add these new methods for swipe actions
    fun onItemDismiss(position: Int) {
        val task = tasks[position]
        onDeleteClick(task)
    }

    fun onItemComplete(position: Int) {
        val task = tasks[position]
        onTaskStatusChanged(task, !task.isCompleted)
        notifyItemChanged(position)
    }
}
