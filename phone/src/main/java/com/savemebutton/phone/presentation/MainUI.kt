package com.savemebutton.phone.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import com.savemebutton.shared.SirenSound
import com.savemebutton.shared.SirenTarget

@Composable
fun MainScreen(viewModel: MainViewModel) {
    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
        val draft by viewModel.draft.collectAsStateWithLifecycle()
        val dirty by viewModel.dirty.collectAsStateWithLifecycle()
        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "SAVE ME",
                color = Color(0xFFFF1744),
                fontWeight = FontWeight.Black,
                fontSize = 32.sp,
            )
            Text(
                "Configure 3 emergency contacts and trigger behavior. " +
                    "TEST sends a real SMS and places a real call.",
                style = MaterialTheme.typography.bodySmall,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = { viewModel.save() },
                    enabled = dirty,
                    modifier = Modifier.weight(1f),
                ) { Text("SAVE") }
                OutlinedButton(
                    onClick = { viewModel.runTest() },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFFFF1744),
                    ),
                    modifier = Modifier.weight(1f),
                ) { Text("TEST") }
            }

            Spacer(Modifier.height(4.dp))

            Text("Contacts", style = MaterialTheme.typography.titleMedium)
            draft.contacts.forEachIndexed { idx, contact ->
                ContactRow(
                    index = idx,
                    name = contact.name,
                    number = contact.number,
                    onChange = { name, number -> viewModel.updateContact(idx, name, number) },
                )
            }

            Spacer(Modifier.height(8.dp))
            Text("Default SMS body", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = draft.smsBody,
                onValueChange = viewModel::updateSmsBody,
                label = { Text("Message") },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Coordinates are appended automatically.",
                style = MaterialTheme.typography.bodySmall,
            )

            Spacer(Modifier.height(8.dp))
            Text("Trigger", style = MaterialTheme.typography.titleMedium)
            IntSlider(
                label = "Hold duration",
                value = draft.holdSeconds,
                range = 3..5,
                unit = "s",
                onChange = viewModel::setHoldSeconds,
            )
            IntSlider(
                label = "Cancel taps",
                value = draft.cancelTaps,
                range = 3..5,
                unit = "",
                onChange = viewModel::setCancelTaps,
            )
            IntSlider(
                label = "Countdown",
                value = draft.countdownSeconds,
                range = 5..10,
                unit = "s",
                onChange = viewModel::setCountdownSeconds,
            )
            IntSlider(
                label = "Per-contact wait",
                value = draft.perContactWaitSeconds,
                range = 15..60,
                unit = "s",
                onChange = viewModel::setPerContactWaitSeconds,
            )

            Spacer(Modifier.height(8.dp))
            Text("Alert sound", style = MaterialTheme.typography.titleMedium)
            Text("Play on", style = MaterialTheme.typography.bodyMedium)
            SirenTargetSelector(
                selected = draft.sirenTarget,
                onChange = viewModel::setSirenTarget,
            )
            SoundDropdown(
                selected = draft.sirenSound,
                onChange = viewModel::setSirenSound,
            )
            Text("Volume: ${(draft.sirenVolume * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = draft.sirenVolume,
                onValueChange = viewModel::setSirenVolume,
                valueRange = 0f..1f,
                modifier = Modifier.fillMaxWidth(),
            )
            ToggleRow(
                label = "Loud burst every minute during sequence",
                checked = draft.loudMinutePulse,
                onChange = viewModel::setLoudMinutePulse,
            )
        }
    }
}

@Composable
private fun IntSlider(
    label: String,
    value: Int,
    range: IntRange,
    unit: String,
    onChange: (Int) -> Unit,
) {
    Column {
        Text("$label: $value$unit", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt().coerceIn(range)) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first - 1).coerceAtLeast(0),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SirenTargetSelector(selected: SirenTarget, onChange: (SirenTarget) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SirenTarget.values().forEach { target ->
            FilterChip(
                selected = target == selected,
                onClick = { onChange(target) },
                label = { Text(target.name) },
                colors = FilterChipDefaults.filterChipColors(),
            )
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SoundDropdown(selected: SirenSound, onChange: (SirenSound) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val options = SirenSound.values().toList()
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            readOnly = true,
            value = selected.name,
            onValueChange = {},
            label = { Text("Sound") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt.name) },
                    onClick = { onChange(opt); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun ContactRow(
    index: Int,
    name: String,
    number: String,
    onChange: (String, String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Contact ${index + 1}", style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(
            value = name,
            onValueChange = { onChange(it, number) },
            label = { Text("Name") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = number,
            onValueChange = { onChange(name, it) },
            label = { Text("Phone (E.164)") },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
