package com.example.chatcoach.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "quick_templates")
data class QuickTemplate(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val category: String,
    val title: String,
    val content: String,
    val isBuiltin: Boolean = false,
    val usageCount: Int = 0
) {
    companion object {
        const val CAT_GREETING = "问候"
        const val CAT_THANKS = "感谢"
        const val CAT_APOLOGY = "道歉"
        const val CAT_DECLINE = "拒绝"
        const val CAT_BLESSING = "祝福"
        const val CAT_APPOINTMENT = "约定"
        const val CAT_CUSTOM = "自定义"

        fun allCategories() = listOf(
            CAT_GREETING, CAT_THANKS, CAT_APOLOGY,
            CAT_DECLINE, CAT_BLESSING, CAT_APPOINTMENT, CAT_CUSTOM
        )
    }
}
