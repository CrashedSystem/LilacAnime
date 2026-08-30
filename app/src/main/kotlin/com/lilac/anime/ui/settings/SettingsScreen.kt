package com.lilac.anime

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment

@Composable
fun SettingsScreen(
    vm: AnimeViewModel,
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val settings = vm.playerSettings

    AppScaffold(selected = "settings", onSelect = onNavigate) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("설정", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            
            Spacer(Modifier.height(24.dp))
            Text("테마", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(12.dp))

            ThemeOption("시스템 설정", themeMode == ThemeMode.SYSTEM) { onThemeChange(ThemeMode.SYSTEM) }
            ThemeOption("라이트 모드", themeMode == ThemeMode.LIGHT) { onThemeChange(ThemeMode.LIGHT) }
            ThemeOption("다크 모드", themeMode == ThemeMode.DARK) { onThemeChange(ThemeMode.DARK) }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(20.dp))

            Text("재생 및 자막 설정", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(16.dp))

            Text("기본 화질", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Auto", "720p", "1080p").forEach { quality ->
                    FilterChip(
                        selected = settings.defaultQuality == quality,
                        onClick = { vm.updatePlayerSettings(context, settings.copy(defaultQuality = quality)) },
                        label = { Text(quality) }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Text("기본 자막 크기 (${settings.subtitleSize.toInt()}%)", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground)
            Slider(
                value = settings.subtitleSize,
                onValueChange = { vm.updatePlayerSettings(context, settings.copy(subtitleSize = it)) },
                valueRange = 50f..300f,
                steps = 10
            )

            Spacer(Modifier.height(16.dp))

            Text(
                "기본 VTT 자막 위치 (${(settings.subtitleBottomPaddingFraction * 100).toInt()}%)",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Slider(
                value = settings.subtitleBottomPaddingFraction,
                onValueChange = {
                    vm.updatePlayerSettings(
                        context,
                        settings.copy(subtitleBottomPaddingFraction = it)
                    )
                },
                valueRange = 0.03f..0.30f,
                steps = 26
            )

            Spacer(Modifier.height(16.dp))

            Text("자막 싱크 미세 조정 (${settings.syncOffsetMs} ms)", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = { 
                    vm.updatePlayerSettings(context, settings.copy(syncOffsetMs = settings.syncOffsetMs - 250L)) 
                }) {
                    Text("-250ms")
                }
                OutlinedButton(onClick = { 
                    vm.updatePlayerSettings(context, settings.copy(syncOffsetMs = 0L)) 
                }) {
                    Text("초기화")
                }
                OutlinedButton(onClick = { 
                    vm.updatePlayerSettings(context, settings.copy(syncOffsetMs = settings.syncOffsetMs + 250L)) 
                }) {
                    Text("+250ms")
                }
            }

            Spacer(Modifier.height(16.dp))

            Text("기본 자막 폰트", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("기본체", "나눔고딕", "명조체").forEach { font ->
                    FilterChip(
                        selected = settings.subtitleFont == font,
                        onClick = { vm.updatePlayerSettings(context, settings.copy(subtitleFont = font)) },
                        label = { Text(font) }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(20.dp))

            Text("Lilac Anime", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            val appVersion = context.packageManager
                .getPackageInfo(context.packageName, 0)
                .versionName
                .orEmpty()
                .ifBlank { "Unknown" }
            Text("Version $appVersion", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
        }
    }
}
