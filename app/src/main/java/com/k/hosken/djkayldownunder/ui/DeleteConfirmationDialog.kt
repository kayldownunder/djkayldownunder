package com.k.hosken.djkayldownunder.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * Standard "permanently delete" confirmation used everywhere the app deletes a real file
 * or folder from device storage (song deletion in PlayerScreen, folder/album deletion in
 * the Library) - keeps the wording and destructive styling consistent across both.
 */
@Composable
fun DeleteConfirmationDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text("This will permanently remove this file. Do you wish to continue?") },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Yes, I want to delete this file")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Sorry, I made a mistake")
            }
        }
    )
}
