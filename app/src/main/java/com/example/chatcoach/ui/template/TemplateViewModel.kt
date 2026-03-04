package com.example.chatcoach.ui.template

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.chatcoach.ChatCoachApp
import com.example.chatcoach.data.db.entity.QuickTemplate
import com.example.chatcoach.data.repository.TemplateRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class TemplateViewModel(app: Application) : AndroidViewModel(app) {
    private val templateRepo = TemplateRepository((app as ChatCoachApp).database.quickTemplateDao())

    private val _selectedCategory = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val templates: StateFlow<List<QuickTemplate>> = _selectedCategory.flatMapLatest { category ->
        if (category == null) templateRepo.getAllTemplates()
        else templateRepo.getByCategory(category)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch { templateRepo.initBuiltinTemplates() }
    }

    fun selectCategory(category: String?) {
        _selectedCategory.value = category
    }

    fun addTemplate(template: QuickTemplate) {
        viewModelScope.launch { templateRepo.addTemplate(template) }
    }

    fun deleteTemplate(template: QuickTemplate) {
        viewModelScope.launch { templateRepo.deleteTemplate(template) }
    }

    fun useTemplate(id: Long) {
        viewModelScope.launch { templateRepo.incrementUsage(id) }
    }
}
