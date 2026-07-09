package com.savemebutton.phone.presentation

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.savemebutton.phone.R
import com.savemebutton.shared.SirenSound
import com.savemebutton.shared.SirenTarget

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onUnlockPremium: () -> Unit = {},
    onRestorePurchases: () -> Unit = {},
) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val dirty by viewModel.dirty.collectAsStateWithLifecycle()
    val testDialogShown by viewModel.testDialogShown.collectAsStateWithLifecycle()
    val premium by viewModel.premium.collectAsStateWithLifecycle()
    val priceText by viewModel.priceText.collectAsStateWithLifecycle()
    val secondsSuffix = stringResource(R.string.suffix_seconds)
    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.app_title),
                color = Color(0xFFFF1744),
                fontWeight = FontWeight.Black,
                fontSize = 32.sp,
            )
            Text(
                stringResource(R.string.header_description),
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
                ) { Text(stringResource(R.string.action_save)) }
                OutlinedButton(
                    onClick = { viewModel.onTestClicked() },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFFFF1744),
                    ),
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.action_test)) }
            }

            Spacer(Modifier.height(4.dp))

            Text(stringResource(R.string.section_contacts), style = MaterialTheme.typography.titleMedium)
            draft.contacts.forEachIndexed { idx, contact ->
                ContactRow(
                    index = idx,
                    name = contact.name,
                    number = contact.number,
                    onChange = { name, number -> viewModel.updateContact(idx, name, number) },
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.section_default_sms), style = MaterialTheme.typography.titleMedium)
            // Cosmetic premium gate: free users keep the DEFAULT message (fully
            // functional). Premium/trial unlocks editing to a custom text. The
            // SOS sending path is never gated.
            val smsEditable = premium.hasFullAccess
            OutlinedTextField(
                value = if (smsEditable) draft.smsBody else viewModel.defaultSmsBody,
                onValueChange = viewModel::updateSmsBody,
                label = { Text(stringResource(R.string.label_message)) },
                readOnly = !smsEditable,
                enabled = smsEditable,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(R.string.hint_coords_appended),
                style = MaterialTheme.typography.bodySmall,
            )
            if (!smsEditable) {
                Text(
                    stringResource(R.string.premium_sms_locked_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFFF1744),
                )
            }

            Spacer(Modifier.height(8.dp))
            PremiumSection(
                premium = premium,
                priceText = priceText,
                onUnlock = onUnlockPremium,
                onRestore = onRestorePurchases,
            )

            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.section_settings), style = MaterialTheme.typography.titleMedium)
            IntSlider(
                label = stringResource(R.string.setting_cancel_taps),
                value = draft.cancelTaps,
                range = 3..5,
                unit = "",
                onChange = viewModel::setCancelTaps,
            )
            IntSlider(
                label = stringResource(R.string.setting_countdown),
                value = draft.countdownSeconds,
                range = 5..10,
                unit = secondsSuffix,
                onChange = viewModel::setCountdownSeconds,
            )
            IntSlider(
                label = stringResource(R.string.setting_per_contact_wait),
                value = draft.perContactWaitSeconds,
                range = 15..60,
                unit = secondsSuffix,
                onChange = viewModel::setPerContactWaitSeconds,
            )
            ToggleRow(
                label = stringResource(R.string.setting_voicemail_trap_escape),
                checked = draft.voicemailTrapEscape,
                onChange = viewModel::setVoicemailTrapEscape,
            )
            Text(
                stringResource(R.string.setting_voicemail_trap_escape_help),
                style = MaterialTheme.typography.bodySmall,
            )

            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.section_alert_sound), style = MaterialTheme.typography.titleMedium)
            ToggleRow(
                label = stringResource(R.string.setting_alert_sound_enabled),
                checked = draft.sirenEnabled,
                onChange = viewModel::setSirenEnabled,
            )
            if (draft.sirenEnabled) {
                Text(stringResource(R.string.label_play_on), style = MaterialTheme.typography.bodyMedium)
                SirenTargetSelector(
                    selected = draft.sirenTarget,
                    onChange = viewModel::setSirenTarget,
                )
                SoundDropdown(
                    selected = draft.sirenSound,
                    onChange = viewModel::setSirenSound,
                )
                OutlinedButton(
                    onClick = { viewModel.previewSiren() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.action_preview)) }
                Text(
                    stringResource(R.string.label_volume, (draft.sirenVolume * 100).toInt()),
                    style = MaterialTheme.typography.bodySmall,
                )
                Slider(
                    value = draft.sirenVolume,
                    onValueChange = viewModel::setSirenVolume,
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth(),
                )
                ToggleRow(
                    label = stringResource(R.string.setting_loud_minute_pulse),
                    checked = draft.loudMinutePulse,
                    onChange = viewModel::setLoudMinutePulse,
                )
            }

            Spacer(Modifier.height(8.dp))
            AboutSection()
        }
    }

    if (testDialogShown) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissTestDialog() },
            title = { Text(stringResource(R.string.test_dialog_title)) },
            text = { Text(stringResource(R.string.test_dialog_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.runTest() }) {
                    Text(stringResource(R.string.test_dialog_confirm), color = Color(0xFFFF1744))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissTestDialog() }) {
                    Text(stringResource(R.string.test_dialog_cancel))
                }
            },
        )
    }
}

@Composable
private fun PremiumSection(
    premium: PremiumUiState,
    priceText: String,
    onUnlock: () -> Unit,
    onRestore: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.premium_section_title), style = MaterialTheme.typography.titleMedium)
            val statusLine = when {
                premium.isPremium -> stringResource(R.string.premium_status_active)
                else -> {
                    val hours = (premium.trialRemainingMs / (1000L * 60 * 60)).toInt()
                    val minutes = ((premium.trialRemainingMs % (1000L * 60 * 60)) / (1000L * 60)).toInt()
                    if (premium.hasFullAccess) {
                        stringResource(R.string.premium_status_trial, hours, minutes)
                    } else {
                        stringResource(R.string.premium_status_expired)
                    }
                }
            }
            Text(
                statusLine,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (premium.hasFullAccess) Color(0xFF2E7D32) else Color(0xFFFF1744),
            )
            Text(
                stringResource(R.string.premium_explainer),
                style = MaterialTheme.typography.bodySmall,
            )
            if (!premium.isPremium) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = onUnlock,
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.premium_unlock, priceText)) }
                    OutlinedButton(
                        onClick = onRestore,
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.premium_restore)) }
                }
            }
        }
    }
}

@Composable
private fun AboutSection() {
    val context = LocalContext.current
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "?"
    }

    fun openUrl(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("About", style = MaterialTheme.typography.titleMedium)
            Text(
                "Application built with AI by a person with disabilities. Support disabled people by buying apps.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = { openUrl("https://play.google.com/store/apps/developer?id=Consultant+BPM") },
                    modifier = Modifier.weight(1f),
                ) { Text("Portfolio") }
                OutlinedButton(
                    onClick = { openUrl("market://details?id=com.savemebutton.app") },
                    modifier = Modifier.weight(1f),
                ) { Text("Rate") }
            }
            Text("Version $versionName", style = MaterialTheme.typography.bodySmall)
            Text("Package: ${context.packageName}", style = MaterialTheme.typography.bodySmall)
            Text(
                "Part of the Crown-family watch apps.",
                style = MaterialTheme.typography.bodySmall,
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SirenTargetSelector(selected: SirenTarget, onChange: (SirenTarget) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        listOf(SirenTarget.NONE, SirenTarget.WATCH, SirenTarget.PHONE, SirenTarget.BOTH).forEach { target ->
            FilterChip(
                selected = target == selected,
                onClick = { onChange(target) },
                label = { Text(target.labelRes().let { stringResource(it) }) },
                colors = FilterChipDefaults.filterChipColors(),
            )
        }
    }
}

private fun SirenTarget.labelRes(): Int = when (this) {
    SirenTarget.NONE -> R.string.siren_target_off
    SirenTarget.WATCH -> R.string.siren_target_watch
    SirenTarget.PHONE -> R.string.siren_target_phone
    SirenTarget.BOTH -> R.string.siren_target_both
}

private fun SirenSound.labelRes(): Int = when (this) {
    SirenSound.TWO_TONE -> R.string.siren_sound_two_tone
    SirenSound.KLAXON -> R.string.siren_sound_klaxon
    SirenSound.WHOOP -> R.string.siren_sound_whoop
    SirenSound.PULSE -> R.string.siren_sound_pulse
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
            value = stringResource(selected.labelRes()),
            onValueChange = {},
            label = { Text(stringResource(R.string.label_sound)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(stringResource(opt.labelRes())) },
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
        Text(
            stringResource(R.string.contact_label, index + 1),
            style = MaterialTheme.typography.labelLarge,
        )
        OutlinedTextField(
            value = name,
            onValueChange = { onChange(it, number) },
            label = { Text(stringResource(R.string.label_name)) },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = number,
            onValueChange = { onChange(name, it) },
            label = { Text(stringResource(R.string.label_phone_e164)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
