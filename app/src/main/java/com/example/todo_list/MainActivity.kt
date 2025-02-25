package com.example.todo_list

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.applandeo.materialcalendarview.CalendarDay
import com.applandeo.materialcalendarview.CalendarView
import com.applandeo.materialcalendarview.listeners.OnCalendarDayClickListener
import com.bumptech.glide.Glide
import com.example.todo_list.adapter.TaskAdapter
import com.example.todo_list.helper.TaskItemTouchHelper
import com.example.todo_list.model.Task
import com.example.todo_list.notification.TaskNotificationReceiver
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var imageView: ShapeableImageView
    private lateinit var name: TextView
    private lateinit var mail: TextView
    private lateinit var taskAdapter: TaskAdapter
    private val tasks = mutableListOf<Task>()
    private lateinit var calendarView: CalendarView
    private val taskDates = mutableSetOf<String>()
    private lateinit var taskSortButton: MaterialButton
    private lateinit var toggleCalendarButton: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        FirebaseApp.initializeApp(this)

        imageView = findViewById(R.id.profileImage)
        name = findViewById(R.id.nameTV)
        mail = findViewById(R.id.mailTV)
        val recyclerView: RecyclerView = findViewById(R.id.tasksRecyclerView)

        auth = FirebaseAuth.getInstance()
        val signOutButton: MaterialButton = findViewById(R.id.signout)

        signOutButton.setOnClickListener {
            auth.signOut()
            Toast.makeText(this, "Signed out successfully", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, SignInActivity::class.java))
            finish()
        }

        // Get the CalendarView reference
        calendarView = findViewById(R.id.calendarView)

        // Show tasks for current day on startup
        showTasksForCurrentDay(isFirstSignIn = true)

        calendarView.setOnCalendarDayClickListener(object : OnCalendarDayClickListener {
            override fun onClick(calendarDay: CalendarDay) {
                showTasksForDate(calendarDay)
            }
        })

        val currentUser = auth.currentUser
        updateUI(currentUser)

        taskAdapter = TaskAdapter(
            tasks,
            onDeleteClick = { task -> deleteTask(task) },
            onEditClick = { task -> showEditTaskDialog(task) },
            onTaskStatusChanged = { task, isCompleted -> updateTaskStatus(task, isCompleted) },
            onTaskReordered = { updatedTasks -> updateTasksOrder(updatedTasks) }
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = taskAdapter

        val itemTouchHelper = ItemTouchHelper(TaskItemTouchHelper(taskAdapter))
        itemTouchHelper.attachToRecyclerView(recyclerView)

        // Change FloatingActionButton to ExtendedFloatingActionButton
        val addTaskButton: ExtendedFloatingActionButton = findViewById(R.id.addTaskButton)
        addTaskButton.setOnClickListener {
            showAddTaskDialog()
        }

        taskSortButton = findViewById(R.id.taskSortButton)
        taskSortButton.setOnClickListener {
            showSortOptionsDialog()
        }

        // Calendar toggle button
        toggleCalendarButton = findViewById(R.id.toggleCalendarButton)
        toggleCalendarButton.setOnClickListener {
            toggleCalendarVisibility()
        }
    }

    private fun updateUI(user: FirebaseUser?) {
        val signOutButton: MaterialButton = findViewById(R.id.signout)
        val taskLayout: LinearLayout = findViewById(R.id.tasklayout)

        if (user != null) {
            signOutButton.visibility = View.VISIBLE
            taskLayout.visibility = View.VISIBLE
            Glide.with(this).load(user.photoUrl).into(imageView)
            name.text = user.displayName ?: "No Name"
            mail.text = user.email ?: "No Email"

            getTasksFromFirestore()
        } else {
            signOutButton.visibility = View.GONE
            taskLayout.visibility = View.GONE
        }
    }

    private fun showTasksForCurrentDay(isFirstSignIn: Boolean = false) {
        val calendar = Calendar.getInstance()
        val currentDay = CalendarDay(calendar)
        showTasksForDate(currentDay, isFirstSignIn)
    }

    private fun showTasksForDate(selectedDate: CalendarDay, isFirstSignIn: Boolean = false) {
        val calendar = selectedDate.calendar
        val formattedDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(calendar.time)

        val tasksOnDate = tasks.filter { task ->
            val taskDate = task.expiryDate?.toDate()
            taskDate?.let {
                SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(it) == formattedDate
            } ?: false
        }

        if (isFirstSignIn && tasksOnDate.isEmpty()) {
            // Do not show the dialog on first sign-in if there are no tasks
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_date_tasks, null)
        val recyclerView: RecyclerView = dialogView.findViewById(R.id.dateTasksRecyclerView)
        val dateText: TextView = dialogView.findViewById(R.id.dateText)

        dateText.text = if (tasksOnDate.isNotEmpty()) {
            "Tasks for $formattedDate"
        } else {
            "No tasks created on this day"
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        val dialogAdapter = TaskAdapter(
            tasksOnDate.toMutableList(),
            onDeleteClick = { task ->
                deleteTask(task)
            },
            onEditClick = { task -> showEditTaskDialog(task) },
            onTaskStatusChanged = { task, isCompleted -> updateTaskStatus(task, isCompleted) },
            onTaskReordered = { /* Not needed for dialog */ }
        )
        recyclerView.adapter = dialogAdapter
        val builder = AlertDialog.Builder(this)
        builder.setView(dialogView)
        builder.setPositiveButton("Close", null)
        builder.show()
    }

    private fun addTaskToFirestore(taskTitle: String, taskDescription: String, expiryDate: Calendar) {
        val db = FirebaseFirestore.getInstance()
        val currentUser = FirebaseAuth.getInstance().currentUser

        if (currentUser == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show()
            return
        }

        val userId = currentUser.uid
        val taskId = db.collection("tasks").document().id
        val expiryDateTimestamp = Timestamp(expiryDate.time)

        val task = Task(
            id = taskId,
            title = taskTitle,
            description = taskDescription,
            expiryDate = expiryDateTimestamp,
            userId = userId,
            isCompleted = false,
            order = tasks.size
        )

        db.collection("tasks").document(taskId)
            .set(task)
            .addOnSuccessListener {
                tasks.add(task)
                taskAdapter.notifyDataSetChanged()
                markDatesOnCalendar()
                Toast.makeText(this, "Task added successfully", Toast.LENGTH_SHORT).show()
                scheduleNotification(task)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error adding task: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    @SuppressLint("ScheduleExactAlarm")
    private fun scheduleNotification(task: Task) {
        // Ensure the task expiryDate is not null
        val expiryDate = task.expiryDate?.toDate() ?: return

        // Calculate the time  before the expiryDate
        val notificationTime = Calendar.getInstance().apply {
            time = expiryDate
            add(Calendar.MINUTE, -2) // Set 30 minutes before the task expiry
        }

        // Create a PendingIntent to show the notification
        val intent = Intent(this, TaskNotificationReceiver::class.java).apply {
            putExtra("taskTitle", task.title)
            putExtra("taskDescription", task.description)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            this,
            task.id.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        // Set up AlarmManager to trigger the notification at the calculated time
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setExact(
            AlarmManager.RTC_WAKEUP,
            notificationTime.timeInMillis,
            pendingIntent
        )
    }

    private fun getTasksFromFirestore() {
        val db = FirebaseFirestore.getInstance()
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return

        db.collection("tasks")
            .whereEqualTo("userId", currentUser.uid)
            .orderBy("order")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("Firestore", "Listen failed", error)
                    return@addSnapshotListener
                }

                if (snapshot != null && !snapshot.isEmpty) {
                    tasks.clear()
                    taskDates.clear()
                    for (document in snapshot.documents) {
                        val task = document.toObject(Task::class.java)
                        if (task != null) {
                            // Ensure the ID is set correctly
                            tasks.add(task.copy(id = document.id))
                        }
                    }
                    taskAdapter.notifyDataSetChanged()
                    markDatesOnCalendar()
                } else {
                    Log.d("Firestore", "Current data: null or empty")
                    tasks.clear()
                    taskAdapter.notifyDataSetChanged()
                    markDatesOnCalendar()
                }
            }
    }

    private fun markDatesOnCalendar() {
        val taskDates = mutableListOf<CalendarDay>()

        tasks.forEach { task ->
            val taskDate = task.expiryDate?.toDate()
            taskDate?.let {
                val calendar = Calendar.getInstance().apply { time = it }
                val calendarDay = CalendarDay(calendar)
                calendarDay.backgroundResource = R.drawable.unamed
                taskDates.add(calendarDay)
                Log.d("CalendarMark", "Added task for date: $calendarDay")
            } ?: Log.d("CalendarMark", "No valid expiry date for task: ${task.title}")
        }

        if (taskDates.isNotEmpty()) {
            calendarView.setCalendarDays(taskDates)
            Log.d("CalendarMark", "Marked ${taskDates.size} dates on the calendar.")
        } else {
            Log.d("CalendarMark", "No dates to mark on the calendar.")
        }
    }

    private fun showAddTaskDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_task, null)
        val taskTitleEditText: TextInputEditText = dialogView.findViewById(R.id.taskTitleEditText)
        val taskDescriptionEditText: TextInputEditText = dialogView.findViewById(R.id.taskDescriptionText)
        val expiryDateEditText: TextInputEditText = dialogView.findViewById(R.id.expiryDateEditText)

        expiryDateEditText.setOnClickListener {
            val calendar = Calendar.getInstance()
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH)
            val day = calendar.get(Calendar.DAY_OF_MONTH)

            // Show DatePickerDialog first
            val datePickerDialog = DatePickerDialog(this, { _, selectedYear, selectedMonth, selectedDay ->
                val selectedDate = Calendar.getInstance()
                selectedDate.set(selectedYear, selectedMonth, selectedDay)

                // Now show TimePickerDialog
                val hour = selectedDate.get(Calendar.HOUR_OF_DAY)
                val minute = selectedDate.get(Calendar.MINUTE)

                val timePickerDialog = TimePickerDialog(this, { _, selectedHour, selectedMinute ->
                    selectedDate.set(Calendar.HOUR_OF_DAY, selectedHour)
                    selectedDate.set(Calendar.MINUTE, selectedMinute)

                    // Format and display the selected date and time
                    val dateTimeFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                    expiryDateEditText.setText(dateTimeFormat.format(selectedDate.time))

                }, hour, minute, true) // true for 24-hour format

                timePickerDialog.show()
            }, year, month, day)

            datePickerDialog.show()
        }

        val builder = AlertDialog.Builder(this)
        builder.setTitle("Add Task")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val taskTitle = taskTitleEditText.text.toString()
                val taskDescription = taskDescriptionEditText.text.toString()
                val expiryDateText = expiryDateEditText.text.toString()
                if (taskTitle.isNotEmpty() && expiryDateText.isNotEmpty()) {
                    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                    val parsedDate = dateFormat.parse(expiryDateText)
                    val calendarExpiryDate = Calendar.getInstance()
                    calendarExpiryDate.time = parsedDate

                    addTaskToFirestore(taskTitle, taskDescription, calendarExpiryDate)
                } else {
                    Toast.makeText(this, "Task title and expire date required", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun deleteTask(task: Task) {
        val db = FirebaseFirestore.getInstance()

        db.collection("tasks").document(task.id)
            .delete()
            .addOnSuccessListener {
                tasks.remove(task)
                taskAdapter.notifyDataSetChanged()
                markDatesOnCalendar() // Refresh calendar
                Toast.makeText(this, "Task deleted successfully", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error deleting task: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateTaskStatus(task: Task, isCompleted: Boolean) {
        val db = FirebaseFirestore.getInstance()

        db.collection("tasks").document(task.id)
            .update("isCompleted", isCompleted)
            .addOnSuccessListener {
                val index = tasks.indexOfFirst { it.id == task.id }
                if (index != -1) {
                    val updatedTask = task.copy(isCompleted = isCompleted)
                    tasks[index] = updatedTask
                    taskAdapter.notifyItemChanged(index)
                    Toast.makeText(this, "Task status updated", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error updating task status: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showEditTaskDialog(task: Task) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_task, null)
        val taskTitleEditText: TextInputEditText = dialogView.findViewById(R.id.taskTitleEditText)
        val taskDescriptionEditText: TextInputEditText = dialogView.findViewById(R.id.taskDescriptionText)
        val expiryDateEditText: TextInputEditText = dialogView.findViewById(R.id.expiryDateEditText)

        // Pre-fill the existing task data
        taskTitleEditText.setText(task.title)
        taskDescriptionEditText.setText(task.description)
        task.expiryDate?.let {
            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            expiryDateEditText.setText(dateFormat.format(it.toDate()))
        }

        // Reuse the date/time picker logic
        expiryDateEditText.setOnClickListener {
            showDateTimePicker(expiryDateEditText)
        }

        AlertDialog.Builder(this)
            .setTitle("Edit Task")
            .setView(dialogView)
            .setPositiveButton("Update") { _, _ ->
                val updatedTitle = taskTitleEditText.text.toString()
                val updatedDescription = taskDescriptionEditText.text.toString()
                val updatedExpiryDateText = expiryDateEditText.text.toString()

                if (updatedTitle.isNotEmpty() && updatedExpiryDateText.isNotEmpty()) {
                    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                    val parsedDate = dateFormat.parse(updatedExpiryDateText)
                    val updatedExpiryDate = Timestamp(parsedDate)

                    updateTaskInFirestore(
                        task.copy(
                            title = updatedTitle,
                            description = updatedDescription,
                            expiryDate = updatedExpiryDate
                        )
                    )
                }
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun showDateTimePicker(editText: TextInputEditText) {
        val calendar = Calendar.getInstance()

        DatePickerDialog(this, { _, year, month, day ->
            TimePickerDialog(this, { _, hour, minute ->
                calendar.set(year, month, day, hour, minute)
                val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                editText.setText(dateFormat.format(calendar.time))
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show()
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun updateTaskInFirestore(updatedTask: Task) {
        val db = FirebaseFirestore.getInstance()

        db.collection("tasks").document(updatedTask.id)
            .set(updatedTask)
            .addOnSuccessListener {
                val position = tasks.indexOfFirst { it.id == updatedTask.id }
                if (position != -1) {
                    tasks[position] = updatedTask
                    taskAdapter.notifyItemChanged(position)
                    markDatesOnCalendar() // Refresh calendar
                    Toast.makeText(this, "Task updated successfully", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error updating task: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showSortOptionsDialog() {
        val options = arrayOf("By Date", "By Title", "By Completion Status")
        AlertDialog.Builder(this)
            .setTitle("Sort Tasks")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> taskAdapter.sortByDate()
                    1 -> taskAdapter.sortByTitle()
                    2 -> taskAdapter.sortByCompletion()
                }
                saveTasksOrder() // Replace updateTasksInFirestore with saveTasksOrder
            }
            .show()
    }

    private fun saveTasksOrder() {
        val db = FirebaseFirestore.getInstance()
        val batch = db.batch()

        tasks.forEachIndexed { index, task ->
            val taskRef = db.collection("tasks").document(task.id)
            batch.update(taskRef, mapOf(
                "order" to index,
                "completed" to task.isCompleted,
                "title" to task.title,
                "description" to task.description,
                "expiryDate" to task.expiryDate
            ))
        }

        batch.commit()
            .addOnSuccessListener {
                Toast.makeText(this, "Tasks order updated", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error updating tasks order: ${e.message}", Toast.LENGTH_SHORT).show()
                getTasksFromFirestore() // Refresh the list in case of failure
            }
    }

    private fun updateTasksOrder(updatedTasks: List<Task>) {
        val db = FirebaseFirestore.getInstance()
        val batch = db.batch()

        updatedTasks.forEachIndexed { index, task ->
            val taskRef = db.collection("tasks").document(task.id)
            batch.update(taskRef, "order", index)
        }

        batch.commit()
            .addOnSuccessListener {
                tasks.clear()
                tasks.addAll(updatedTasks)
                taskAdapter.notifyDataSetChanged()
                Toast.makeText(this, "Task order updated", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error updating task order: ${e.message}", Toast.LENGTH_SHORT).show()
                getTasksFromFirestore() // Refresh the list in case of failure
            }
    }

    private fun toggleCalendarVisibility() {
        if (calendarView.visibility == View.VISIBLE) {
            calendarView.visibility = View.GONE
            toggleCalendarButton.text = "Show Calendar"
        } else {
            calendarView.visibility = View.VISIBLE
            toggleCalendarButton.text = "Hide Calendar"
        }
    }
}