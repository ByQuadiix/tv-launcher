package net.quadiix.tvlauncher.ui

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout

/**
 * Texteingabe als klassischer Android-Dialog. Der funktioniert auf Google TV zuverlässig mit
 * der Fernbedienung und der Bildschirmtastatur.
 */
fun showTextPrompt(
    context: Context,
    title: String,
    initial: String = "",
    password: Boolean = false,
    onResult: (String) -> Unit,
) {
    val input = EditText(context).apply {
        setText(initial)
        setSelection(text.length)
        isSingleLine = true
        inputType = if (password) {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        } else {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        imeOptions = EditorInfo.IME_ACTION_DONE
    }
    val pad = (24 * context.resources.displayMetrics.density).toInt()
    val container = FrameLayout(context).apply {
        setPadding(pad, pad / 2, pad, 0)
        addView(input)
    }

    val dialog = AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
        .setTitle(title)
        .setView(container)
        .setPositiveButton("OK") { _, _ -> onResult(input.text.toString()) }
        .setNegativeButton("Abbrechen", null)
        .create()

    input.setOnEditorActionListener { _, actionId, _ ->
        if (actionId == EditorInfo.IME_ACTION_DONE) {
            dialog.dismiss()
            onResult(input.text.toString())
            true
        } else false
    }
    dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
    dialog.show()
    input.requestFocus()
}
