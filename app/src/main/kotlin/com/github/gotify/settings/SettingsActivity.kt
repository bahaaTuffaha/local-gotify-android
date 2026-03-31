package com.github.gotify.settings

import android.app.Activity
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.MenuItem
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.preference.ListPreference
import androidx.preference.ListPreferenceDialogFragmentCompat
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import com.github.gotify.R
import com.github.gotify.Utils
import com.github.gotify.database.LocalDataRepository
import com.github.gotify.databinding.SettingsActivityBinding
import com.github.gotify.service.WebSocketService
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class SettingsActivity :
    AppCompatActivity(),
    OnSharedPreferenceChangeListener {
    private lateinit var binding: SettingsActivityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = SettingsActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings, SettingsFragment())
            .commit()
        setSupportActionBar(binding.appBarDrawer.toolbar)
        val actionBar = supportActionBar
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true)
            actionBar.setDisplayShowCustomEnabled(true)
        }
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        sharedPreferences.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return false
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (sharedPreferences == null) return
        when (key) {
            getString(R.string.setting_key_theme) -> {
                ThemeHelper.setTheme(
                    this,
                    sharedPreferences.getString(key, getString(R.string.theme_default))!!
                )
            }
        }
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        private val exportMessageStateLauncher: ActivityResultLauncher<Intent> =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    result.data?.data?.let { exportMessageState(it) }
                }
            }

        private val importMessageStateLauncher: ActivityResultLauncher<Intent> =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    result.data?.data?.let { importMessageState(it) }
                }
            }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                findPreference<SwitchPreferenceCompat>(
                    getString(R.string.setting_key_notification_channels)
                )?.isEnabled = true
            }
            findPreference<androidx.preference.EditTextPreference>(
                getString(R.string.setting_key_reconnect_delay)
            )?.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, newValue ->
                    val value = (newValue as String).trim().toIntOrNull() ?: 60
                    if (value !in 5..1200) {
                        Utils.showSnackBar(
                            requireActivity(),
                            "Please enter a value between 5 and 1200"
                        )
                        return@OnPreferenceChangeListener false
                    }

                    requestWebSocketRestart()
                    true
                }
            findPreference<SwitchPreferenceCompat>(
                getString(R.string.setting_key_exponential_backoff)
            )?.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, _ ->
                    requestWebSocketRestart()
                    true
                }
        }

        private fun requestWebSocketRestart() {
            val intent = Intent(requireContext(), WebSocketService::class.java)
            requireContext().startService(intent)
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            findPreference<ListPreference>(
                getString(R.string.setting_key_message_layout)
            )?.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, _ ->
                    showRestartDialog()
                    true
                }
            findPreference<SwitchPreferenceCompat>(
                getString(R.string.setting_key_notification_channels)
            )?.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, _ ->
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                        return@OnPreferenceChangeListener false
                    }
                    showRestartDialog()
                    true
                }
            findPreference<SwitchPreferenceCompat>(
                getString(R.string.setting_key_exclude_from_recent)
            )?.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, value ->
                    Utils.setExcludeFromRecent(requireContext(), value as Boolean)
                    return@OnPreferenceChangeListener true
                }
            findPreference<SwitchPreferenceCompat>(
                getString(R.string.setting_key_intent_dialog_permission)
            )?.let {
                it.setOnPreferenceChangeListener { _, _ ->
                    openSystemAlertWindowPermissionPage()
                }
            }
            findPreference<Preference>(getString(R.string.setting_key_export_message_state))
                ?.setOnPreferenceClickListener {
                    openExportMessageStateDocument()
                }
            findPreference<Preference>(getString(R.string.setting_key_import_message_state))
                ?.setOnPreferenceClickListener {
                    openImportMessageStateDocument()
                }
            checkSystemAlertWindowPermission()
        }

        override fun onDisplayPreferenceDialog(preference: Preference) {
            if (preference is ListPreference) {
                showListPreferenceDialog(preference)
            } else {
                super.onDisplayPreferenceDialog(preference)
            }
        }

        override fun onResume() {
            super.onResume()
            checkSystemAlertWindowPermission()
        }

        private fun openSystemAlertWindowPermissionPage(): Boolean {
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:${requireContext().packageName}".toUri()
            ).apply {
                startActivity(this)
            }
            return true
        }

        private fun openExportMessageStateDocument(): Boolean {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                type = "application/json"
                addCategory(Intent.CATEGORY_OPENABLE)
                putExtra(Intent.EXTRA_TITLE, getString(R.string.message_state_export_filename))
            }
            return launchDocumentIntent(
                exportMessageStateLauncher,
                intent,
                R.string.message_state_select_export
            )
        }

        private fun openImportMessageStateDocument(): Boolean {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "application/json"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            return launchDocumentIntent(
                importMessageStateLauncher,
                intent,
                R.string.message_state_select_import
            )
        }

        private fun launchDocumentIntent(
            launcher: ActivityResultLauncher<Intent>,
            intent: Intent,
            chooserTitleRes: Int
        ): Boolean {
            return try {
                launcher.launch(Intent.createChooser(intent, getString(chooserTitleRes)))
                true
            } catch (_: ActivityNotFoundException) {
                Utils.showSnackBar(requireActivity(), getString(R.string.please_install_file_browser))
                false
            }
        }

        private fun exportMessageState(uri: Uri) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val snapshots = LocalDataRepository(requireContext()).exportMarkerSnapshots()
                    val payload = MessageStateTransfer(markers = snapshots)
                    requireContext().contentResolver.openOutputStream(uri)?.bufferedWriter().use { writer ->
                        requireNotNull(writer) { "Could not open export destination" }
                        writer.write(Utils.JSON.toJson(payload))
                    }
                    withContext(Dispatchers.Main) {
                        Utils.showSnackBar(
                            requireActivity(),
                            getString(R.string.message_state_export_success, snapshots.size)
                        )
                    }
                } catch (_: Exception) {
                    withContext(Dispatchers.Main) {
                        Utils.showSnackBar(
                            requireActivity(),
                            getString(R.string.message_state_export_failed)
                        )
                    }
                }
            }
        }

        private fun importMessageState(uri: Uri) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val payload = requireContext().contentResolver.openInputStream(uri)
                        ?.bufferedReader().use { reader ->
                            requireNotNull(reader) { "Could not open import source" }
                            Utils.JSON.fromJson(reader, MessageStateTransfer::class.java)
                        }
                    val snapshots = payload?.markers.orEmpty()
                    LocalDataRepository(requireContext()).importMarkerSnapshots(snapshots)
                    withContext(Dispatchers.Main) {
                        requireActivity().setResult(Activity.RESULT_OK)
                        Utils.showSnackBar(
                            requireActivity(),
                            getString(R.string.message_state_import_success, snapshots.size)
                        )
                    }
                } catch (_: Exception) {
                    withContext(Dispatchers.Main) {
                        Utils.showSnackBar(
                            requireActivity(),
                            getString(R.string.message_state_import_failed)
                        )
                    }
                }
            }
        }

        private fun checkSystemAlertWindowPermission() {
            findPreference<SwitchPreferenceCompat>(
                getString(R.string.setting_key_intent_dialog_permission)
            )?.let {
                val canDrawOverlays = Settings.canDrawOverlays(requireContext())
                it.isChecked = canDrawOverlays
                it.summary = if (canDrawOverlays) {
                    getString(R.string.setting_summary_intent_dialog_permission_granted)
                } else {
                    getString(R.string.setting_summary_intent_dialog_permission)
                }
            }
        }

        private fun showListPreferenceDialog(preference: ListPreference) {
            val dialogFragment = MaterialListPreference()
            dialogFragment.arguments = Bundle(1).apply { putString("key", preference.key) }
            @Suppress("DEPRECATION") // https://issuetracker.google.com/issues/181793702#comment3
            dialogFragment.setTargetFragment(this, 0)
            dialogFragment.show(
                parentFragmentManager,
                "androidx.preference.PreferenceFragment.DIALOG"
            )
        }

        private fun showRestartDialog() {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.setting_restart_dialog_title)
                .setMessage(R.string.setting_restart_dialog_message)
                .setPositiveButton(getString(R.string.setting_restart_dialog_button1)) { _, _ ->
                    restartApp()
                }
                .setNegativeButton(getString(R.string.setting_restart_dialog_button2), null)
                .show()
        }

        private fun restartApp() {
            val packageManager = requireContext().packageManager
            val packageName = requireContext().packageName
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            val componentName = intent!!.component
            val mainIntent = Intent.makeRestartActivityTask(componentName)
            startActivity(mainIntent)
            Runtime.getRuntime().exit(0)
        }
    }

    class MaterialListPreference : ListPreferenceDialogFragmentCompat() {
        private var mWhichButtonClicked = 0

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            mWhichButtonClicked = DialogInterface.BUTTON_NEGATIVE
            val builder = MaterialAlertDialogBuilder(requireActivity())
                .setTitle(preference.dialogTitle)
                .setPositiveButton(preference.positiveButtonText, this)
                .setNegativeButton(preference.negativeButtonText, this)

            val contentView = context?.let { onCreateDialogView(it) }
            if (contentView != null) {
                onBindDialogView(contentView)
                builder.setView(contentView)
            } else {
                builder.setMessage(preference.dialogMessage)
            }
            onPrepareDialogBuilder(builder)
            return builder.create()
        }

        override fun onClick(dialog: DialogInterface, which: Int) {
            mWhichButtonClicked = which
        }

        override fun onDismiss(dialog: DialogInterface) {
            onDialogClosedWasCalledFromOnDismiss = true
            super.onDismiss(dialog)
        }

        private var onDialogClosedWasCalledFromOnDismiss = false

        override fun onDialogClosed(positiveResult: Boolean) {
            if (onDialogClosedWasCalledFromOnDismiss) {
                onDialogClosedWasCalledFromOnDismiss = false
                super.onDialogClosed(mWhichButtonClicked == DialogInterface.BUTTON_POSITIVE)
            } else {
                super.onDialogClosed(positiveResult)
            }
        }
    }
}
