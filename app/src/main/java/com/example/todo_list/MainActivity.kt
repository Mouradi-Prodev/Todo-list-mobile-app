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

import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AlertDialog
import androidx.compose.ui.graphics.Color
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.applandeo.materialcalendarview.CalendarDay
import com.applandeo.materialcalendarview.CalendarView
import com.applandeo.materialcalendarview.EventDay
import com.applandeo.materialcalendarview.listeners.OnCalendarDayClickListener
import com.applandeo.materialcalendarview.listeners.OnCalendarDayLongClickListener
import com.bumptech.glide.Glide
import com.example.todo_list.adapter.TaskAdapter
import com.example.todo_list.model.Task
import com.example.todo_list.notification.TaskNotificationReceiver
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
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

        calendarView.setOnCalendarDayClickListener(object : OnCalendarDayClickListener {
            override fun onClick(calendarDay: CalendarDay) {
                showTasksForDate(calendarDay)
            }
            
        })



        val currentUser = auth.currentUser
        updateUI(currentUser)

        taskAdapter = TaskAdapter(tasks) { task ->
            deleteTask(task)
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = taskAdapter

        val addTaskButton: FloatingActionButton = findViewById(R.id.addTaskButton)
        addTaskButton.setOnClickListener {
            showAddTaskDialog()
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

    private fun showTasksForDate(selectedDate: CalendarDay) {
        // Extract the Date from CalendarDay
        val calendar = selectedDate.calendar
        val formattedDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(calendar.time)

        val tasksOnDate = tasks.filter {
            val taskDate = it.expiryDate?.toDate() // Assuming expiryDate is a Date
            val taskCalendar = Calendar.getInstance()
            taskCalendar.time = taskDate
            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(taskCalendar.time) == formattedDate
        }

        if (tasksOnDate.isNotEmpty()) {
            // Show the tasks on the selected date in a dialog or Toast
            val taskDescriptions = tasksOnDate.joinToString("\n") {
                "Title: ${it.title}\nDescription: ${it.description}"
            }
            showTasksDialog(taskDescriptions)
        } else {
            Toast.makeText(this, "No tasks on this date", Toast.LENGTH_SHORT).show()
        }
    }

    // Display tasks in a dialog
    private fun showTasksDialog(taskDescriptions: String) {
        AlertDialog.Builder(this)
            .setTitle("Tasks for the selected date")
            .setMessage(taskDescriptions)
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .show()
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

        // Convert expiryDate to Firestore Timestamp
        val expiryDateTimestamp = Timestamp(expiryDate.time)

        val task = Task(
            id = taskId,
            title = taskTitle,
            description = taskDescription,
            expiryDate = expiryDateTimestamp,
            userId = userId
        )

        db.collection("tasks").document(taskId)
            .set(task)
            .addOnSuccessListener {
                Toast.makeText(this, "Task added successfully", Toast.LENGTH_SHORT).show()

                markDatesOnCalendar()
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
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show()
            return
        }

        val userId = currentUser.uid

        db.collection("tasks")
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, exception ->
                if (exception != null) {
                    Toast.makeText(this, "Error fetching tasks: ${exception.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                tasks.clear()
                taskDates.clear()
                snapshot?.documents?.forEach { document ->
                    val task = document.toObject(Task::class.java)
                    task?.let { tasks.add(it) }
                }
                taskAdapter.notifyDataSetChanged()
                markDatesOnCalendar()
            }
    }

    private fun markDatesOnCalendar() {
        val taskDates = mutableListOf<CalendarDay>()

        tasks.forEach { task ->
            val taskDate = task.expiryDate?.toDate()
            taskDate?.let {
                val calendar = Calendar.getInstance().apply { time = taskDate }
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
            .setPositiveButton("Add") { dialog, _ ->
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
                dialog.dismiss()
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
                taskAdapter.deleteTask(task)
                markDatesOnCalendar()
                Toast.makeText(this, "Task deleted successfully", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error deleting task: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
