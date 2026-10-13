package com.arsipsenja.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.arsipsenja.ui.theme.WarnaArsip

/**
 * Kolom isian bergaya Arsip Senja: label kecil di atas, kotak isi gelap
 * 10dp, kursor amber, slot konten opsional di kanan (tombol mata dsb.).
 */
@Composable
fun KolomIsian(
    label: String,
    nilai: String,
    onUbah: (String) -> Unit,
    modifier: Modifier = Modifier,
    tempat: String = "",
    sandi: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onKirim: (() -> Unit)? = null,
    isian: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = WarnaArsip.Tinta2,
        )
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(BorderStroke(1.dp, WarnaArsip.GarisRambut), androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                .background(WarnaArsip.Permukaan, androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (nilai.isEmpty() && tempat.isNotEmpty()) {
                        Text(
                            text = tempat,
                            style = MaterialTheme.typography.bodyMedium,
                            color = WarnaArsip.Tinta3,
                        )
                    }
                    BasicTextField(
                        value = nilai,
                        onValueChange = onUbah,
                        enabled = isian,
                        singleLine = true,
                        textStyle = TextStyle(
                            color = WarnaArsip.Tinta,
                            fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                            fontFamily = MaterialTheme.typography.bodyMedium.fontFamily,
                            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight,
                        ),
                        cursorBrush = SolidColor(WarnaArsip.Amber),
                        visualTransformation = if (sandi) PasswordVisualTransformation()
                        else VisualTransformation.None,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = keyboardType,
                            imeAction = imeAction,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { onKirim?.invoke() },
                            onGo = { onKirim?.invoke() },
                            onSearch = { onKirim?.invoke() },
                            onNext = { onKirim?.invoke() },
                            onSend = { onKirim?.invoke() },
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 44.dp),
                    )
                }
                if (trailing != null) {
                    Spacer(Modifier.width(6.dp))
                    trailing()
                }
            }
        }
    }
}
