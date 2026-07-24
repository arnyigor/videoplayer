package com.arny.mobilecinema.presentation.compose

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

class ComposeTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ComposeTestScreen()
        }
    }
}

@Composable
private fun ComposeTestScreen(
    initialClicks: Int = 0,
) {
    // В режиме инспекции (Preview) состояние не мутирует — фиксируем значение.
    // Это позволяет отрисовать несколько статичных stateful-превью без мутаций.
    val clicks = if (LocalInspectionMode.current) initialClicks else {
        var c by remember { mutableIntStateOf(initialClicks) }
        c
    }

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "Compose работает",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Тестовый debug-only экран",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = { /* clicks++ — только в рантайме */ }) {
                    Text(text = "Проверить recomposition: $clicks")
                }
            }
        }
    }
}

// ─── Preview: Stateless (начальное состояние) ──────────────────────────────
@Preview(
    name = "Compact",
    widthDp = 700,
    heightDp = 1000,
    showBackground = true,
)
@Preview(
    name = "Medium",
    widthDp = 840,
    heightDp = 1000,
    showBackground = true,
)
@Preview(
    name = "Expanded",
    widthDp = 1200,
    heightDp = 1000,
    showBackground = true,
)
@Preview(
    name = "Dark Theme",
    widthDp = 700,
    heightDp = 1000,
    showBackground = true,
    uiMode =  UI_MODE_NIGHT_YES,
)
@Preview(
    name = "Font Large",
    widthDp = 700,
    heightDp = 1000,
    showBackground = true,
    fontScale = 1.5f,
)
@Preview(
    name = "System UI",
    widthDp = 700,
    heightDp = 1000,
    showSystemUi = true,
)
@Preview(
    name = "Locale RU",
    widthDp = 700,
    heightDp = 1000,
    showBackground = true,
    locale = "ru",
)
@Composable
private fun ComposeTestScreen_Dark() {
    ComposeTestScreen(initialClicks = 0)
}
