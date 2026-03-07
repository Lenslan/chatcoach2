package com.example.chatcoach.service

import com.example.chatcoach.data.db.entity.ChatMessage
import com.example.chatcoach.data.db.entity.Friend

object PromptBuilder {

    fun buildReplyPrompt(friend: Friend, messages: List<ChatMessage>, summary: String? = null): String {
        return buildString {
            appendLine("[系统角色设定]")
            appendLine("你是一位社交沟通助手，帮助用户在微信聊天中给出合适的回复建议。")
            appendLine()

            appendLine("[好友画像]")
            if (friend.relationship.isNotBlank()) appendLine("- 我与对方的关系：${friend.relationship}")
            if (friend.tone.isNotBlank()) appendLine("- 我应该使用的语气：${friend.tone}")
            if (friend.attitude.isNotBlank()) appendLine("- 我的沟通态度：${friend.attitude}")
            if (!friend.notes.isNullOrBlank()) appendLine("- 补充说明：${friend.notes}")
            appendLine()

            if (!friend.customPrompt.isNullOrBlank()) {
                appendLine("[用户自定义 Prompt]")
                appendLine(friend.customPrompt)
                appendLine()
            }

            appendLine("[输出要求]")
            appendLine("请根据以下聊天记录，生成 3 条不同风格的回复建议。")
            appendLine("每条回复需包含：")
            appendLine("- 风格标签（如：正式/幽默/暖心/简洁/俏皮/专业，选择最贴切的一个）")
            appendLine("- 回复内容（控制在 50 字以内）")
            appendLine()
            appendLine("输出格式（严格遵守）：")
            appendLine("【风格标签】回复内容")
            appendLine()
            appendLine("直接给出 3 条回复，不要解释，不要编号。")
            appendLine()

            appendLine("[聊天记录]")
            if (!summary.isNullOrBlank()) {
                appendLine("[前情摘要] $summary")
                appendLine()
            }
            messages.sortedBy { it.timestamp }.forEach { msg ->
                val sender = if (msg.sender == ChatMessage.SENDER_ME) "我" else friend.wechatName
                appendLine("$sender：${msg.getDisplayContent()}")
            }
        }
    }

    fun buildSummaryPrompt(messages: List<ChatMessage>, friendName: String): String {
        return buildString {
            appendLine("请将以下聊天记录压缩为一段简要摘要（200字以内），")
            appendLine("保留关键话题、双方立场和情绪变化，省略寒暄和重复内容：")
            appendLine()
            messages.sortedBy { it.timestamp }.forEach { msg ->
                val sender = if (msg.sender == ChatMessage.SENDER_ME) "我" else friendName
                appendLine("$sender：${msg.getDisplayContent()}")
            }
        }
    }

    fun buildReviewPrompt(friend: Friend, messages: List<ChatMessage>): String {
        return buildString {
            appendLine("[系统角色设定]")
            appendLine("你是一位沟通教练，擅长分析人际对话并给出改进建议。")
            appendLine()

            appendLine("[好友画像]")
            if (friend.relationship.isNotBlank()) appendLine("- 我与对方的关系：${friend.relationship}")
            if (friend.tone.isNotBlank()) appendLine("- 我期望的语气：${friend.tone}")
            if (friend.attitude.isNotBlank()) appendLine("- 我的沟通态度：${friend.attitude}")
            appendLine()

            appendLine("[分析要求]")
            appendLine("请对以下完整对话进行复盘分析，严格按照以下 JSON 格式输出，不要输出其他任何内容：")
            appendLine()
            appendLine("""
{
  "clarityScore": 8,
  "toneScore": 7,
  "emotionScore": 8,
  "topicScore": 7,
  "highlights": [
    {"index": 3, "content": "原消息内容", "reason": "亮点原因"}
  ],
  "improvements": [
    {"index": 5, "original": "原句", "suggested": "建议改写", "reason": "改进原因"}
  ],
  "strategies": [
    "策略建议1",
    "策略建议2"
  ]
}
            """.trimIndent())
            appendLine()

            appendLine("[完整对话记录]")
            messages.sortedBy { it.timestamp }.forEachIndexed { index, msg ->
                val sender = if (msg.sender == ChatMessage.SENDER_ME) "我" else friend.wechatName
                appendLine("[${index + 1}] $sender：${msg.getDisplayContent()}")
            }
        }
    }

    fun buildPolishPrompt(
        friend: Friend,
        messages: List<ChatMessage>,
        draftReply: String,
        summary: String? = null
    ): String {
        return buildString {
            appendLine("[系统角色设定]")
            appendLine("你是一位社交沟通助手，帮助用户润色微信聊天中的回复内容。")
            appendLine("用户会提供一段草稿回复，请根据当前聊天上下文和好友画像对其进行润色优化。")
            appendLine()

            appendLine("[好友画像]")
            if (friend.relationship.isNotBlank()) appendLine("- 我与对方的关系：${friend.relationship}")
            if (friend.tone.isNotBlank()) appendLine("- 我应该使用的语气：${friend.tone}")
            if (friend.attitude.isNotBlank()) appendLine("- 我的沟通态度：${friend.attitude}")
            if (!friend.notes.isNullOrBlank()) appendLine("- 补充说明：${friend.notes}")
            appendLine()

            if (!friend.customPrompt.isNullOrBlank()) {
                appendLine("[用户自定义 Prompt]")
                appendLine(friend.customPrompt)
                appendLine()
            }

            appendLine("[输出要求]")
            appendLine("请根据聊天上下文，对用户草稿进行润色，生成 3 条不同风格的润色版本。")
            appendLine("保留用户原意，优化表达方式。")
            appendLine("每条润色需包含：")
            appendLine("- 风格标签（如：正式/幽默/暖心/简洁/俏皮/专业，选择最贴切的一个）")
            appendLine("- 润色后的内容")
            appendLine()
            appendLine("输出格式（严格遵守）：")
            appendLine("【风格标签】润色后的内容")
            appendLine()
            appendLine("直接给出 3 条润色结果，不要解释，不要编号。")
            appendLine()

            appendLine("[聊天记录]")
            if (!summary.isNullOrBlank()) {
                appendLine("[前情摘要] $summary")
                appendLine()
            }
            messages.sortedBy { it.timestamp }.forEach { msg ->
                val sender = if (msg.sender == ChatMessage.SENDER_ME) "我" else friend.wechatName
                appendLine("$sender：${msg.getDisplayContent()}")
            }
            appendLine()

            appendLine("[用户草稿]")
            appendLine(draftReply)
        }
    }

    fun buildAnalysisPrompt(friend: Friend, messages: List<ChatMessage>): String {
        return buildString {
            appendLine("请分析以下 ${friend.wechatName} 的聊天记录，输出以下内容的 JSON：")
            appendLine("""
{
  "chatStyle": "对方的聊天风格描述",
  "emotionTrend": "积极/消极/中性",
  "topicPreferences": ["话题1", "话题2"],
  "communicationTips": ["建议1", "建议2"]
}
            """.trimIndent())
            appendLine()
            appendLine("[聊天记录]")
            messages.sortedBy { it.timestamp }.forEach { msg ->
                val sender = if (msg.sender == ChatMessage.SENDER_ME) "我" else friend.wechatName
                appendLine("$sender：${msg.getDisplayContent()}")
            }
        }
    }
}
