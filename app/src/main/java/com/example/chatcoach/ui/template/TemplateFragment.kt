package com.example.chatcoach.ui.template

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.chatcoach.R
import com.example.chatcoach.data.db.entity.QuickTemplate
import com.example.chatcoach.databinding.FragmentTemplatesBinding
import com.example.chatcoach.util.copyToClipboard
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class TemplateFragment : Fragment() {

    private var _binding: FragmentTemplatesBinding? = null
    private val binding get() = _binding!!
    private val viewModel: TemplateViewModel by viewModels()
    private lateinit var adapter: TemplateAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTemplatesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupTabs()
        setupRecyclerView()
        setupClickListeners()
        observeData()
    }

    private fun setupTabs() {
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("全部"))
        QuickTemplate.allCategories().forEach { cat ->
            binding.tabLayout.addTab(binding.tabLayout.newTab().setText(cat))
        }
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val category = if (tab?.position == 0) null else tab?.text?.toString()
                viewModel.selectCategory(category)
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupRecyclerView() {
        adapter = TemplateAdapter(
            onUse = { template ->
                viewModel.useTemplate(template.id)
                requireContext().copyToClipboard(template.content)
            },
            onDelete = { template ->
                if (!template.isBuiltin) viewModel.deleteTemplate(template)
                else Toast.makeText(requireContext(), "内置模板不可删除", Toast.LENGTH_SHORT).show()
            }
        )
        binding.rvTemplates.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@TemplateFragment.adapter
        }
    }

    private fun setupClickListeners() {
        binding.fabAddTemplate.setOnClickListener { showAddDialog() }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.templates.collectLatest { templates ->
                adapter.submitList(templates)
            }
        }
    }

    private fun showAddDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_template, null)
        val etTitle = dialogView.findViewById<EditText>(R.id.et_title)
        val etContent = dialogView.findViewById<EditText>(R.id.et_content)
        val spinnerCategory = dialogView.findViewById<Spinner>(R.id.spinner_category)

        val categories = QuickTemplate.allCategories()
        spinnerCategory.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, categories)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.add_template))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val title = etTitle.text.toString().trim()
                val content = etContent.text.toString().trim()
                val category = categories[spinnerCategory.selectedItemPosition]
                if (title.isNotBlank() && content.isNotBlank()) {
                    viewModel.addTemplate(QuickTemplate(
                        category = category,
                        title = title,
                        content = content
                    ))
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
