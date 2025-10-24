package de.rogallab.mobile.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.outlined.People
import androidx.compose.ui.graphics.vector.ImageVector

// Navigation destinations as NavKeys
@Serializable
data object PeopleList: NavKey
@Serializable
data object PersonInput : NavKey
@Serializable
data class PersonDetail(val id: String) : NavKey