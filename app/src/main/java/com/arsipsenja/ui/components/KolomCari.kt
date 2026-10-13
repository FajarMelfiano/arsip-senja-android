package com.arsipsenja.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.arsipsenja.ui.icons.ArsipIcons
import com.arsipsenja.ui.theme.WarnaArsip

/**
 * Kolom pencarian as-you-type: ikon kiri, teks tengah, tombol hapus di kanan.
 * Memakai BasicTextField agar styling gelapnya persis seperti bahasa desain.
 */
@Composable
fun KolomCari(
    nilai: String,
    onUbah: (String) -> Unit,
    modifier: Modifier = Modifier,
    tempat: String = "Cari nama berkas…",
    onKosongkan: (() -> Unit)? = null,
    onCari: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, WarnaArsip.GarisRambut), RoundedCornerShape(10.dp))
            .background(WarnaArsip.Permukaan, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = ArsipIcons.Search,
                contentDescription = null,
                tint = WarnaArsip.Tinta3,
                modifier = Modifier.size(17.dp),
            )
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (nilai.isEmpty()) {
                    Text(
                        text = tempat,
                        style = MaterialTheme.typography.bodyMedium,
                        color = WarnaArsip.Tinta3,
                    )
                }
                BasicTextField(
                    value = nilai,
                    onValueChange = onUbah,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = WarnaArsip.Tinta,
                        fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                        fontFamily = MaterialTheme.typography.bodyMedium.fontFamily,
                        lineHeight = MaterialTheme.typography.bodyMedium.lineHeight,
                    ),
                    cursorBrush = SolidColor(WarnaArsip.Amber),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onCari?.invoke() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 40.dp),
                )
            }
            if (nilai.isNotEmpty()) {
                Spacer(Modifier.width(6.dp))
                TombolIkon(
                    ikon = ArsipIcons.X,
                    aksi = "Kosongkan pencarian",
                    tint = WarnaArsip.Tinta3,
                    onClick = { onKosongkan?.invoke() },
                )
            }
        }
    }
}
