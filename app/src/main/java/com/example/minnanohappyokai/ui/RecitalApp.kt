package com.example.minnanohappyokai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Deliberately small root while the current-recital foundations are being completed. It owns no
 * data access: all state comes from [RecitalViewModel] and it never presents a recital history.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun RecitalApp(viewModel: RecitalViewModel) {
    val state by viewModel.state.collectAsState()
    var newRecitalName by rememberSaveable { mutableStateOf("") }

    Scaffold(topBar = { TopAppBar(title = { Text("みんなの発表会") }) }) { innerPadding ->
        when {
            !state.loaded && !state.readError -> LoadingContent(Modifier.padding(innerPadding))
            state.readError -> RetryContent(Modifier.padding(innerPadding), viewModel::retry)
            state.program?.activeRecital == null -> StartRecitalContent(
                name = newRecitalName,
                onNameChange = { newRecitalName = it },
                busy = state.busy,
                error = state.error,
                onStart = { viewModel.perform { startRecital(newRecitalName) } },
                modifier = Modifier.padding(innerPadding),
            )
            else -> CurrentRecitalFoundationContent(
                name = state.program!!.activeRecital!!.name,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator()
            Text("読み込んでいます")
        }
    }
}

@Composable
private fun RetryContent(modifier: Modifier, onRetry: () -> Unit) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("読み込めませんでした")
        Button(onClick = onRetry) { Text("もう一度試す") }
    }
}

@Composable
private fun StartRecitalContent(
    name: String,
    onNameChange: (String) -> Unit,
    busy: Boolean,
    error: String?,
    onStart: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("発表会を登録しましょう", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Text("この端末で準備する、現在の発表会を始めます。")
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("発表会名（必須）") },
            singleLine = true,
            enabled = !busy,
            isError = error != null,
        )
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
        Button(onClick = onStart, enabled = name.isNotBlank() && !busy, modifier = Modifier.fillMaxWidth()) {
            Text(if (busy) "保存しています" else "新しい発表会を始める")
        }
    }
}

@Composable
private fun CurrentRecitalFoundationContent(name: String, modifier: Modifier) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Text("現在の発表会を準備中です。")
        Text(
            "発表会情報、部、今年の参加者、出演者名簿、演奏・曲、プログラム確認、年度終了の画面は、" +
                "この単一発表会の基盤に沿って順に追加します。",
        )
    }
}
