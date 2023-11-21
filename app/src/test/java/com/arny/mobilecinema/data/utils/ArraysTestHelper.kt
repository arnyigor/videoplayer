package com.arny.mobilecinema.data.utils

data class UserA(override val id: Int, val name: String) : User
data class UserB(override val id: Int, val surname: String) : User

interface User {
    val id: Int
}
