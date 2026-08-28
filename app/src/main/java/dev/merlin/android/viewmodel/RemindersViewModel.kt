package dev.merlin.android.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.merlin.android.data.ReminderService
import dev.merlin.android.models.Reminder
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Äquivalent zur Lade-/Löschlogik aus `RemindersView.swift`. Anders als
 * `ArticlesViewModel` braucht dieser Screen kein Undo/Offline-Queue-Pattern:
 * [ReminderService.cancel] ist rein lokal (Room + `AlarmManager.cancel`), es
 * gibt keinen Server-Request, der fehlschlagen könnte – daher reicht ein
 * einfaches optimistisches Entfernen aus der Liste ohne Rollback-Pfad.
 */
@HiltViewModel
class RemindersViewModel @Inject constructor(
    private val reminderService: ReminderService,
) : ViewModel() {

    private val _reminders = MutableStateFlow<List<Reminder>>(emptyList())
    val reminders: StateFlow<List<Reminder>> = _reminders.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            _reminders.value = reminderService.all()
            _isLoading.value = false
        }
    }

    /** Storniert den Reminder (Alarm + Room-Status) und entfernt ihn sofort aus der Liste. */
    fun delete(reminder: Reminder) {
        _reminders.value = _reminders.value.filterNot { it.id == reminder.id }
        viewModelScope.launch {
            reminderService.cancel(reminder.articleId)
        }
    }
}
