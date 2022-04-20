package com.todoist_android.data.repository

import com.todoist_android.data.models.UserModel
import com.todoist_android.data.network.UserApi
import javax.inject.Inject

class UserRepo @Inject constructor(
    private val api: UserApi,
    private val userPrefs: UserPreferences
) : BaseRepo() {

    suspend fun getUser(id: String) = safeApiCall { api.getUser(id) }

    suspend fun updateUser(updateUserRequest: UserModel) = safeApiCall {
        api.editUser(
            updateUserRequest.id!!,
            updateUserRequest.username!!,
            updateUserRequest.email!!,
            updateUserRequest.photo!!,
            updateUserRequest.password ?: "")
    }

    suspend fun deleteUser(id: String) = safeApiCall { api.deleteUser(id) }

    suspend fun deleteAllTasks(id: String) = safeApiCall { api.deleteAllTasks(id) }

    suspend fun saveNotificationState(state: Boolean) {
        userPrefs.saveNotificationState(state)
    }

}