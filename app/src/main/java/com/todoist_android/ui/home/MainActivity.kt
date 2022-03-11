package com.todoist_android.ui.home

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.todoist_android.R
import com.todoist_android.data.network.APIResource
import com.todoist_android.data.repository.UserPreferences
import com.todoist_android.data.responses.TasksResponseItem
import com.todoist_android.databinding.ActivityMainBinding
import com.todoist_android.ui.SplashActivity
import com.todoist_android.ui.handleApiError
import com.todoist_android.ui.profile.ProfileActivity
import com.todoist_android.ui.profile.ProfileViewModel
import com.todoist_android.ui.settings.SettingsActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), SwipeRefreshLayout.OnRefreshListener {
    private lateinit var binding: ActivityMainBinding

    @Inject
    lateinit var userPreferences: UserPreferences

    var loggedInUserId: Int = 0

    private val objects = arrayListOf<Any>()
    private val finalObjects = arrayListOf<Any>()

    private val viewModel by viewModels<MainViewModel>()

    private val profileViewModel by viewModels<ProfileViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userPreferences = UserPreferences(this)

        setSupportActionBar(binding.toolbar)
        title = "Hi"

        var currentStatus = ""

        //Receiving data from the previous activity
        val loggedInUserId = intent.getIntExtra("userId", 0)

        //if userId is 0, then the user is not logged in
        if (loggedInUserId == 0) {
            val intent = Intent(this, SplashActivity::class.java)
            startActivity(intent)
            finish()
        }else {
            this.loggedInUserId = loggedInUserId
        }

        binding.swipeContainer.setOnRefreshListener(this)
        binding.swipeContainer.setColorSchemeResources(
            R.color.colorPrimary,
            android.R.color.holo_green_dark,
            android.R.color.holo_orange_dark,
            android.R.color.holo_blue_dark
        )

        binding.swipeContainer.post {
            binding.swipeContainer.isRefreshing = true
            finalObjects.clear()
            objects.clear()
            currentStatus = ""
        }

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        viewModel.task.observe(this, Observer { it ->
            when (it) {
                is APIResource.Success -> {
                    Log.d("MainActivity", "Tasks: ${it}")

                    // if objects anf final objects is not empty clear the list
                    if ( objects.isNotEmpty() ) objects.clear()
                    if ( finalObjects.isNotEmpty() ) finalObjects.clear()

                    binding.swipeContainer.isRefreshing = false
                    binding.recyclerView.removeItemDecoration(StickyHeaderItemDecoration())

                    it.value.forEach {
                        objects.add(it)
                    }

                    if(objects.size == 0) {
                        showEmptyState(VISIBLE)

                    } else {
                        showEmptyState(GONE)
                    }
                    //sort objects by it.status (progress, created, completed)
                    objects.sortBy {
                        when (it) {
                            is TasksResponseItem -> it.status
                            else -> "progress"
                        }
                    }
                    objects.reverse()

                    //loop through the objects, if the item's status changes from current status
                    //add it to the finalObjects then add string to finalObjects
                    //then set the current status to the new status
                    currentStatus = ""
                    objects.forEach {
                        if (it is TasksResponseItem) {
                            if (it.status != currentStatus) {
                                if(it.status!!.isBlank()){
                                    finalObjects.add("Unknown Status")
                                }else {
                                    finalObjects.add(it.status.replaceFirstChar { if (it.isLowerCase()) it.titlecase(
                                        Locale.getDefault()) else it.toString() })
                                }
                                currentStatus = it.status
                                finalObjects.add(it)
                                scheduleAlert(binding.root, it as TasksResponseItem)
                            }
                            else {
                                finalObjects.add(it)
                                currentStatus = it.status
                            }
                        }
                    }

                    //Setup RecyclerView
                    var todoAdapter = ToDoAdapter(finalObjects)
                    todoAdapter.onEditTaskCallback = {
                        fetchTasks()
                    }
                    binding.recyclerView.adapter = todoAdapter
                    binding.recyclerView.addItemDecoration(StickyHeaderItemDecoration())
                }
                is APIResource.Loading -> {
                    Log.d("MainActivity", "Loading...")
                    binding.swipeContainer.isRefreshing = true

                    //clear finalObjects and objects
                    finalObjects.clear()
                    objects.clear()
                    currentStatus = ""

                    binding.recyclerView.adapter?.notifyDataSetChanged()
                }
                is APIResource.Error -> {
                    currentStatus = ""
                    binding.swipeContainer.isRefreshing = false
                    binding.root.handleApiError(it)
                    showEmptyState(VISIBLE)
                    Log.d("MainActivity", "Error: ${it.toString()}")
                }
            }
        })

        binding.buttonNewTask.setOnClickListener {
            val modalBottomSheet = BottomSheetFragment{
                fetchTasks()
            }
            modalBottomSheet.show(supportFragmentManager, BottomSheetFragment.TAG)
        }

        //get user details
        profileViewModel.getUser(loggedInUserId.toString())

        profileViewModel.user.observe(this, Observer {
            when (it) {
                is APIResource.Success -> {
                    title = "Hi ${it.value.username}"
                    Log.d("MainActivity", "User: ${it}")
                }
                is APIResource.Loading -> {
                    Log.d("ProfileActivity", "Loading...")
                }
                is APIResource.Error -> {
                    binding.root.handleApiError(it)
                    Log.d("ProfileActivity", "Error: ${it.toString()}")
                }
            }
        })

    }

    private fun showEmptyState(visible: Int) {
        binding.emptyTag.visibility = visible
        binding.emptyIcon.visibility = visible
    }

    override fun onResume() {
        super.onResume()
        fetchTasks()
    }

    override fun onRefresh() {
        fetchTasks()
    }

    private fun fetchTasks() {
        Log.d("MainActivity", "Fetching tasks...")
        //TODO: Remove this, for testing purposes only
        viewModel.getTasks("")

        //TODO: remove above code above and use the below code once API is ready
        //viewModel.getTasks(userId.toString())

    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.profile -> {
                Intent(this, ProfileActivity::class.java).also {
                    it.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    it.putExtra("userId", loggedInUserId)
                    startActivity(it)
                }
                true
            }

            R.id.action_settings -> {
                Intent(this, SettingsActivity::class.java).also {
                    it.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    it.putExtra("userId", loggedInUserId)
                    startActivity(it)
                }
                true
            }

            R.id.action_logout -> {
                val alertDialog = AlertDialog.Builder(this)
                alertDialog.apply {
                    setTitle("Logout?")
                    setMessage("Are you sure you want to logout?")
                    setPositiveButton("Yes") { _, _ ->
                        performLogout()
                    }
                    setNegativeButton("No") { dialog, which ->
                        dialog.dismiss()
                    }
                }.create().show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    fun performLogout() = lifecycleScope.launch {
        userPreferences.clearToken()
        Intent(this@MainActivity, SplashActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }.also {
            startActivity(it)
        }
    }

    //get time difference between now(current date time) to due date of format 12/03/2022 5:10 PM
    private fun getTimeDifference(date: String): Array<Int> {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault())
        val currentDate = Date()
        val dueDate = dateFormat.parse(date)
        val diff = dueDate.time - currentDate.time
        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        //position 0 is days, position 1 is hours, position 2 is minutes
        return arrayOf(days.toInt() ,hours.toInt(), minutes.toInt())

    }

    fun scheduleAlert(view: View, tasksResponseItem: TasksResponseItem){
        //Pass a taskresponseitem, compute due time and schedule a notification

        var content = tasksResponseItem.description
        var hours = getTimeDifference(tasksResponseItem.due_date!!)[1]
        var mins = getTimeDifference(tasksResponseItem.due_date!!)[2]
        var title = tasksResponseItem.title

        if(hours == 0 && mins > 0){
            title = "${tasksResponseItem.title} is due in $mins minutes"
        }
        else if(hours > 0 && mins == 0){
            title = "${tasksResponseItem.title} is due in $hours hours"
        }
        else if(hours > 0 && mins > 0){
            title = "${tasksResponseItem.title} is due in $hours hours and $mins minutes"
        } else {
            title = "${tasksResponseItem.title} is due"
        }

        var delay = (hours * 60) + mins
        val intent = Intent(this, NotificationService::class.java)

        intent.putExtra("title",title)
        intent.putExtra("content",content)
        intent.putExtra("delay", delay)

        if(tasksResponseItem.status == "progress" || tasksResponseItem.status == "created" && delay >= 0){
            startService(intent)
        }

    }
}
