package com.example.todo_list

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.todo_list.adapter.TaskAdapter
import com.example.todo_list.model.Task
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var imageView: ShapeableImageView
    private lateinit var name: TextView
    private lateinit var mail: TextView
    private lateinit var taskAdapter: TaskAdapter
    private val tasks = mutableListOf<Task>()

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
        val taskLayout: RelativeLayout = findViewById(R.id.tasklayout)

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

    private fun addTaskToFirestore(taskTitle: String) {
        val db = FirebaseFirestore.getInstance()
        val currentUser = FirebaseAuth.getInstance().currentUser

        if (currentUser == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show()
            return
        }

        val userId = currentUser.uid
        val taskId = db.collection("tasks").document().id

        val task = Task(
            id = taskId,
            title = taskTitle,
            userId = userId
        )

        db.collection("tasks").document(taskId)
            .set(task)
            .addOnSuccessListener {
                Toast.makeText(this, "Task added successfully", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error adding task: ${e.message}", Toast.LENGTH_SHORT).show()
            }
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
                snapshot?.documents?.forEach { document ->
                    val task = document.toObject(Task::class.java)
                    task?.let { tasks.add(it) }
                }
                taskAdapter.notifyDataSetChanged()
            }
    }

    private fun showAddTaskDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_task, null)
        val taskTitleEditText: TextInputEditText = dialogView.findViewById(R.id.taskTitleEditText)

        val builder = AlertDialog.Builder(this)
        builder.setTitle("Add Task")
            .setView(dialogView)
            .setPositiveButton("Add") { dialog, _ ->
                val taskTitle = taskTitleEditText.text.toString()
                if (taskTitle.isNotEmpty()) {
                    addTaskToFirestore(taskTitle)
                } else {
                    Toast.makeText(this, "Please enter a task title", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this, "Task deleted successfully", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error deleting task: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
