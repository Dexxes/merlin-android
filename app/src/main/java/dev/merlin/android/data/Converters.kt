package dev.merlin.android.data

import androidx.room.TypeConverter
import dev.merlin.android.models.Reminder

/** Room kennt keine Enums nativ – Speicherung als String über den Enum-Namen. */
class Converters {

    @TypeConverter
    fun kindToString(kind: PendingMutationKind): String = kind.name

    @TypeConverter
    fun stringToKind(value: String): PendingMutationKind = PendingMutationKind.valueOf(value)

    @TypeConverter
    fun reminderStatusToString(status: Reminder.Status): String = status.name

    @TypeConverter
    fun stringToReminderStatus(value: String): Reminder.Status = Reminder.Status.valueOf(value)

    @TypeConverter
    fun highlightMutationKindToString(kind: PendingHighlightMutationKind): String = kind.name

    @TypeConverter
    fun stringToHighlightMutationKind(value: String): PendingHighlightMutationKind = PendingHighlightMutationKind.valueOf(value)
}
