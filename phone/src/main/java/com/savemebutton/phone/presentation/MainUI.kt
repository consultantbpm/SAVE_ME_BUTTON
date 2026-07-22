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
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.savemebutton.phone.R
import com.savemebutton.shared.SirenSound
import com.savemebutton.shared.SirenTarget
import com.savemebutton.shared.WatchProfile
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToInt
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
    var showWalkthrough by remember { mutableStateOf(false) }
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
                color = SaveMeRed,
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
                        contentColor = SaveMeRed,
                    ),
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.action_test)) }
            }

            Spacer(Modifier.height(4.dp))

            SectionCard(title = stringResource(R.string.section_contacts)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    draft.contacts.forEachIndexed { idx, contact ->
                        ContactRow(
                            index = idx,
                            name = contact.name,
                            number = contact.number,
                            onChange = { name, number -> viewModel.updateContact(idx, name, number) },
                        )
                    }
                }
            }

            SectionCard(title = stringResource(R.string.section_default_sms)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Cosmetic premium gate: free users keep the DEFAULT message
                    // (fully functional). Premium/trial unlocks editing to a
                    // custom text. The SOS sending path is never gated.
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
                            color = SaveMeRed,
                        )
                    }
                }
            }

            SectionCard(title = stringResource(R.string.section_settings)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { showWalkthrough = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Ghid Configurare") }
                    WatchProfileBanner(
                        manufacturer = draft.watchManufacturer,
                        nativeProfile = draft.watchNativeProfile,
                        currentProfile = draft.watchProfile,
                        onSetProfile = viewModel::setWatchProfile
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${stringResource(R.string.setting_hold_seconds)}: ${draft.holdSeconds}$secondsSuffix",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    PressTimelineStrip(
                        holdSeconds = draft.holdSeconds,
                        onSetThreshold = viewModel::setHoldSeconds
                    )
                    Text(
                        stringResource(R.string.setting_hold_seconds_help),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    IntSlider(
                        label = stringResource(R.string.setting_countdown),
                        value = draft.countdownSeconds,
                        range = 5..10,
                        unit = secondsSuffix,
                        onChange = viewModel::setCountdownSeconds,
                    )
                    IntSlider(
                        label = stringResource(R.string.setting_cancel_taps),
                        value = draft.cancelTaps,
                        range = 3..5,
                        unit = "",
                        onChange = viewModel::setCancelTaps,
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
                }
            }

            SectionCard(title = stringResource(R.string.section_alert_sound)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                }
            }

            SectionCard(title = stringResource(R.string.premium_section_title)) {
                PremiumSectionContent(
                    premium = premium,
                    priceText = priceText,
                    onUnlock = onUnlockPremium,
                    onRestore = onRestorePurchases,
                )
            }

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
                    Text(stringResource(R.string.test_dialog_confirm), color = SaveMeRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissTestDialog() }) {
                    Text(stringResource(R.string.test_dialog_cancel))
                }
            },
        )
    }

    if (showWalkthrough) {
        WalkthroughDialog(
            brand = com.savemebutton.shared.detectKnownBrand(draft.watchManufacturer).key,
            onDismiss = { showWalkthrough = false }
        )
    }
}

@Composable
private fun PremiumSectionContent(
    premium: PremiumUiState,
    priceText: String,
    onUnlock: () -> Unit,
    onRestore: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            color = if (premium.hasFullAccess) Color(0xFF2E7D32) else SaveMeRed,
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

/**
 * 1:1 port of Crown Button's About card (accessibility credit, Portfolio,
 * Rate, version) — strings copied verbatim from Crown's `strings.xml`
 * (`about_title_section`, `about_disability_credit`, `about_portfolio_button`,
 * `about_rate_button`, `about_version_format`) across all 8 locales. Expanded
 * by default, matching Crown.
 */
@Composable
private fun AboutSection() {
    val context = LocalContext.current
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "?"
    }
    val versionCode = remember {
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            PackageInfoCompat.getLongVersionCode(info).toInt()
        }.getOrDefault(0)
    }

    fun openUrl(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    SectionCard(title = stringResource(R.string.about_title_section), initiallyExpanded = true) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringResource(R.string.about_disability_credit),
                style = MaterialTheme.typography.bodySmall,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AboutButton(
                    text = stringResource(R.string.about_portfolio_button),
                    onClick = { openUrl("https://play.google.com/store/apps/developer?id=Consultant+BPM") },
                    modifier = Modifier.weight(1f),
                )
                AboutButton(
                    text = stringResource(R.string.about_rate_button),
                    onClick = { openUrl("market://details?id=com.savemebutton.app") },
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                stringResource(R.string.about_version_format, versionName, versionCode),
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

@Composable
private fun WatchProfileBanner(
    manufacturer: String,
    nativeProfile: WatchProfile,
    currentProfile: WatchProfile,
    onSetProfile: (WatchProfile) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val title = if (manufacturer.isNotBlank()) "Watch detected: ${manufacturer.replaceFirstChar { it.uppercase() }}" else "Watch Profile"
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        ProfileCombo(native = nativeProfile, current = currentProfile, onSelect = onSetProfile)
        Text(
            if (currentProfile == WatchProfile.OTHER) "Configurable universal profile. Drag the slider to adjust hold threshold."
            else "Fixed mapping, tested for your watch.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun PressTimelineStrip(
    holdSeconds: Int,
    onSetThreshold: (Int) -> Unit,
) {
    val visMax = 5000f
    val handleColor = MaterialTheme.colorScheme.onSurface
    val accentColor = SaveMeRed
    val currentMs = androidx.compose.runtime.rememberUpdatedState(holdSeconds * 1000)
    val onThr = androidx.compose.runtime.rememberUpdatedState(onSetThreshold)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, _ ->
                        val w = size.width.toFloat()
                        val raw = (change.position.x / w) * visMax
                        val ms = (raw / 1000f).roundToInt()
                        onThr.value(ms.coerceIn(3, 5))
                        change.consume()
                    }
                )
            }
    ) {
        val w = size.width
        val axisY = size.height / 2f
        fun xOf(ms: Int) = (ms / visMax) * w
        drawLine(handleColor.copy(alpha = 0.35f), Offset(0f, axisY), Offset(w, axisY), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        // Half-second tick marks: the axis is LINEAR (x ∝ ms); the ticks make
        // the proportion visible so equal durations read as equal lengths.
        var tick = 500
        while (tick < visMax.toInt()) {
            val tx = xOf(tick)
            drawLine(handleColor.copy(alpha = 0.5f), Offset(tx, axisY - 8.dp.toPx()), Offset(tx, axisY + 8.dp.toPx()), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            tick += 500
        }
        val x = xOf(currentMs.value)
        drawLine(accentColor, Offset(0f, axisY), Offset(x, axisY), strokeWidth = 10.dp.toPx(), cap = StrokeCap.Round)
        drawLine(handleColor, Offset(x, axisY - 16.dp.toPx()), Offset(x, axisY + 16.dp.toPx()), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileCombo(
    native: WatchProfile,
    current: WatchProfile,
    onSelect: (WatchProfile) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val options = when {
        native.isPowerButtonProfile() -> listOf("Power" to native, "Generic" to WatchProfile.OTHER)
        native.isNonPowerButtonProfile() -> listOf("Non-Power" to native, "Generic" to WatchProfile.OTHER)
        else -> listOf("Power" to WatchProfile.ONEPLUS, "Non-Power" to WatchProfile.OTHER_ACCESSIBILITY, "Generic" to WatchProfile.OTHER)
    }
    val selectedText = options.firstOrNull { it.second == current }?.first ?: "Generic"

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            readOnly = true,
            value = selectedText,
            onValueChange = {},
            label = { Text("Watch Profile") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.filter { it.second != current }.forEach { (lbl, prof) ->
                DropdownMenuItem(
                    text = { Text(lbl) },
                    onClick = { onSelect(prof); expanded = false },
                )
            }
        }
    }
}
@Composable
private fun WalkthroughDialog(
    brand: com.savemebutton.shared.BrandKey,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ghid Configurare") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Step 1: Set up on the watch.", fontWeight = FontWeight.Bold)
                Text(when (brand) {
                    com.savemebutton.shared.BrandKey.SAMSUNG -> "Assign to double-press action of the Home button."
                    com.savemebutton.shared.BrandKey.ONEPLUS -> "Assign to the short-press action of the Power button."
                    else -> "Assign to the action available for launching apps in Watch Settings."
                })
                Text("Step 2: Set up on the phone.", fontWeight = FontWeight.Bold)
                Text("Configure your emergency contacts and SOS behavior.")
                Text("Step 3: Using the buttons.", fontWeight = FontWeight.Bold)
                Text("Press and hold the configured button until the SOS sequence starts.")
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("OK") }
        }
    )
}
