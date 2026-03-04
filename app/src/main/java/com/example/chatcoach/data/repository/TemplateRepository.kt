package com.example.chatcoach.data.repository

import com.example.chatcoach.data.db.dao.QuickTemplateDao
import com.example.chatcoach.data.db.entity.QuickTemplate
import kotlinx.coroutines.flow.Flow

class TemplateRepository(private val dao: QuickTemplateDao) {

    fun getAllTemplates(): Flow<List<QuickTemplate>> = dao.getAllTemplates()

    fun getByCategory(category: String): Flow<List<QuickTemplate>> = dao.getByCategory(category)

    suspend fun addTemplate(template: QuickTemplate): Long = dao.insert(template)

    suspend fun updateTemplate(template: QuickTemplate) = dao.update(template)

    suspend fun deleteTemplate(template: QuickTemplate) = dao.delete(template)

    suspend fun incrementUsage(id: Long) = dao.incrementUsage(id)

    suspend fun initBuiltinTemplates() {
        if (dao.getCount() > 0) return
        val builtins = listOf(
            QuickTemplate(category = "问候", title = "早安问候", content = "早上好呀，今天也要元气满满哦！", isBuiltin = true),
            QuickTemplate(category = "问候", title = "日常问好", content = "嗨，最近怎么样？", isBuiltin = true),
            QuickTemplate(category = "问候", title = "正式问候", content = "您好，近来一切可好？", isBuiltin = true),
            QuickTemplate(category = "感谢", title = "真诚感谢", content = "非常感谢您的帮助，真的很感激！", isBuiltin = true),
            QuickTemplate(category = "感谢", title = "轻松感谢", content = "谢谢啦，你真是太好了！", isBuiltin = true),
            QuickTemplate(category = "道歉", title = "诚恳道歉", content = "真的很抱歉，是我考虑不周，给您添麻烦了。", isBuiltin = true),
            QuickTemplate(category = "道歉", title = "轻松道歉", content = "不好意思啊，下次一定注意！", isBuiltin = true),
            QuickTemplate(category = "拒绝", title = "委婉拒绝", content = "感谢您的邀请，不过这次恐怕不太方便，下次一定。", isBuiltin = true),
            QuickTemplate(category = "拒绝", title = "直接拒绝", content = "抱歉，这次实在抽不开身，谢谢理解。", isBuiltin = true),
            QuickTemplate(category = "祝福", title = "生日祝福", content = "生日快乐！祝你新的一年心想事成，万事顺遂！", isBuiltin = true),
            QuickTemplate(category = "祝福", title = "节日祝福", content = "节日快乐！愿你和家人幸福安康！", isBuiltin = true),
            QuickTemplate(category = "约定", title = "约饭", content = "好久不见，有空一起吃个饭呀？", isBuiltin = true),
            QuickTemplate(category = "约定", title = "约会面", content = "方便的话我们约个时间见一面聊聊？", isBuiltin = true)
        )
        builtins.forEach { dao.insert(it) }
    }
}
