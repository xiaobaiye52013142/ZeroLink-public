package com.helium4.localscreenshare.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.helium4.localscreenshare.ui.theme.LocalAppTheme

/** Passing null renders the indeterminate variant. */
@Composable
fun AppLinearProgress(
    progress: Float? = null,
    modifier: Modifier = Modifier,
) {
    val theme = LocalAppTheme.current
    val shaped = modifier.clip(RoundedCornerShape(theme.cornerRadiusSmall))
    if (progress == null) {
        LinearProgressIndicator(
            modifier = shaped,
            color = theme.progressIndicatorColor,
            trackColor = theme.sliderInactiveColor,
        )
    } else {
        LinearProgressIndicator(
            progress = { progress },
            modifier = shaped,
            color = theme.progressIndicatorColor,
            trackColor = theme.sliderInactiveColor,
        )
    }
}

@Composable
fun AppCircularProgress(
    progress: Float? = null,
    modifier: Modifier = Modifier,
) {
    val theme = LocalAppTheme.current
    if (progress == null) {
        CircularProgressIndicator(
            modifier = modifier,
            color = theme.progressIndicatorColor,
            trackColor = theme.sliderInactiveColor,
        )
    } else {
        CircularProgressIndicator(
            progress = { progress },
            modifier = modifier,
            color = theme.progressIndicatorColor,
            trackColor = theme.sliderInactiveColor,
        )
    }
}
