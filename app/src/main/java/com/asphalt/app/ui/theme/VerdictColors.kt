package com.asphalt.app.ui.theme

import androidx.compose.ui.graphics.Color
import com.asphalt.app.model.VerdictType

/** لون الحكم ولون خلفيته حسب الوضع الحالي (نهاري / ليلي). */
fun VerdictType.primaryColor(): Color = when (this) {
    VerdictType.ACCEPT -> VerdictAccept
    VerdictType.CHECK_DETAILS -> VerdictCheck
    VerdictType.CONSIDER -> VerdictConsider
    VerdictType.REJECT -> VerdictReject
    VerdictType.WAIT_DETAILS -> VerdictWait
}

fun VerdictType.bgColor(): Color = when (this) {
    VerdictType.ACCEPT -> VerdictAcceptBg
    VerdictType.CHECK_DETAILS -> VerdictCheckBg
    VerdictType.CONSIDER -> VerdictConsiderBg
    VerdictType.REJECT -> VerdictRejectBg
    VerdictType.WAIT_DETAILS -> VerdictWaitBg
}
