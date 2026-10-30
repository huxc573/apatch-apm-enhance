package me.bmax.apatch.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.bmax.apatch.R

@Composable
fun ModuleUpdateButton(
    onClick: () -> Unit,
    compact: Boolean = false
) = FilledTonalButton(
    onClick = onClick,
    enabled = true,
    modifier = if (compact) Modifier.height(30.dp) else Modifier,
    contentPadding = PaddingValues(horizontal = if (compact) 8.dp else 12.dp)
) {
    Icon(
        modifier = Modifier.size(if (compact) 16.dp else 20.dp),
        painter = painterResource(id = R.drawable.device_mobile_down),
        contentDescription = null
    )

    Spacer(modifier = Modifier.width(if (compact) 4.dp else 6.dp))
    Text(
        text = stringResource(id = R.string.apm_update),
        style = if (compact) MaterialTheme.typography.labelMedium
        else MaterialTheme.typography.labelLarge,
        maxLines = 1,
        overflow = TextOverflow.Visible,
        softWrap = false
    )
}

@Composable
fun ModuleRemoveButton(
    enabled: Boolean,
    onClick: () -> Unit,
    compact: Boolean = false
) = FilledTonalButton(
    onClick = onClick,
    enabled = enabled,
    modifier = if (compact) Modifier.height(30.dp) else Modifier,
    contentPadding = PaddingValues(horizontal = if (compact) 8.dp else 12.dp)
) {
    Icon(
        modifier = Modifier.size(if (compact) 16.dp else 20.dp),
        painter = painterResource(id = R.drawable.trash),
        contentDescription = null
    )

    Spacer(modifier = Modifier.width(if (compact) 4.dp else 6.dp))
    Text(
        text = stringResource(id = R.string.apm_remove),
        style = if (compact) MaterialTheme.typography.labelMedium
        else MaterialTheme.typography.labelLarge,
        maxLines = 1,
        overflow = TextOverflow.Visible,
        softWrap = false
    )
}

@Composable
fun KPModuleRemoveButton(
    enabled: Boolean, onClick: () -> Unit
) = FilledTonalButton(
    onClick = onClick, enabled = enabled, contentPadding = PaddingValues(horizontal = 12.dp)
) {
    Icon(
        modifier = Modifier.size(20.dp),
        painter = painterResource(id = R.drawable.trash),
        contentDescription = null
    )

    Spacer(modifier = Modifier.width(6.dp))
    Text(
        text = stringResource(id = R.string.kpm_unload),
        maxLines = 1,
        overflow = TextOverflow.Visible,
        softWrap = false
    )
}

@Composable
fun ModuleStateIndicator(
    @DrawableRes icon: Int, color: Color = MaterialTheme.colorScheme.outline
) {
    Image(
        modifier = Modifier.requiredSize(150.dp),
        painter = painterResource(id = icon),
        contentDescription = null,
        alpha = 0.1f,
        colorFilter = ColorFilter.tint(color)
    )
}