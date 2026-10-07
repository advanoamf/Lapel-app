package com.lapel.app.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lapel.app.data.repository.ImportCounts
import com.lapel.app.data.repository.ImportRepository
import com.lapel.domain.importer.SpreadsheetImport
import com.lapel.domain.importer.SpreadsheetImporter
import com.lapel.domain.importer.XlsxReader
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface ImportState {
    data object Idle : ImportState
    data object Working : ImportState
    data class Preview(val data: SpreadsheetImport, val databaseHasOrders: Boolean) : ImportState
    data class Done(val counts: ImportCounts) : ImportState
    data class Failed(val message: String) : ImportState
}

@HiltViewModel
class ImportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ImportRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<ImportState>(ImportState.Idle)
    val state: StateFlow<ImportState> = _state.asStateFlow()

    fun onFilePicked(uri: Uri) {
        _state.value = ImportState.Working
        viewModelScope.launch {
            _state.value = runCatching {
                val data = withContext(Dispatchers.IO) {
                    val workbook = context.contentResolver.openInputStream(uri)?.use(XlsxReader::read)
                        ?: error("Cannot open file")
                    SpreadsheetImporter().import(workbook)
                }
                ImportState.Preview(data, repository.hasOrders())
            }.getOrElse { ImportState.Failed(it.message ?: it.javaClass.simpleName) }
        }
    }

    fun confirm() {
        val preview = _state.value as? ImportState.Preview ?: return
        if (preview.databaseHasOrders) return
        _state.value = ImportState.Working
        viewModelScope.launch {
            _state.value = runCatching { ImportState.Done(repository.save(preview.data)) }
                .getOrElse { ImportState.Failed(it.message ?: it.javaClass.simpleName) }
        }
    }

    fun reset() {
        _state.value = ImportState.Idle
    }
}
