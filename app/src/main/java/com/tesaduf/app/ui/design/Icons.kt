package com.tesaduf.app.ui.design

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.composables.icons.lucide.R as Lucide

/**
 * TESADÜF icon set: Lucide (thin, rounded 2px strokes) — one consistent family across
 * the app instead of emoji or mixed Material glyphs. Unused icons are removed from
 * release builds by resource shrinking.
 */
object TIcons {
    val ArrowLeft: ImageVector @Composable get() = ImageVector.vectorResource(Lucide.drawable.lucide_ic_arrow_left)
    val ChevronRight: ImageVector @Composable get() = ImageVector.vectorResource(Lucide.drawable.lucide_ic_chevron_right)
    val Close: ImageVector @Composable get() = ImageVector.vectorResource(Lucide.drawable.lucide_ic_x)
    val Lock: ImageVector @Composable get() = ImageVector.vectorResource(Lucide.drawable.lucide_ic_lock)
    val WifiOff: ImageVector @Composable get() = ImageVector.vectorResource(Lucide.drawable.lucide_ic_wifi_off)
    val Alert: ImageVector @Composable get() = ImageVector.vectorResource(Lucide.drawable.lucide_ic_circle_alert)
    val Shield: ImageVector @Composable get() = ImageVector.vectorResource(Lucide.drawable.lucide_ic_shield_check)
    val Timer: ImageVector @Composable get() = ImageVector.vectorResource(Lucide.drawable.lucide_ic_timer)
    val Users: ImageVector @Composable get() = ImageVector.vectorResource(Lucide.drawable.lucide_ic_users)
    val Heart: ImageVector @Composable get() = ImageVector.vectorResource(Lucide.drawable.lucide_ic_heart)
    val HeartHandshake: ImageVector @Composable get() = ImageVector.vectorResource(Lucide.drawable.lucide_ic_heart_handshake)
    val Incognito: ImageVector @Composable get() = ImageVector.vectorResource(Lucide.drawable.lucide_ic_eye_off)
    val Sparkles: ImageVector @Composable get() = ImageVector.vectorResource(Lucide.drawable.lucide_ic_sparkles)
    val Refresh: ImageVector @Composable get() = ImageVector.vectorResource(Lucide.drawable.lucide_ic_refresh_cw)
    val Send: ImageVector @Composable get() = ImageVector.vectorResource(Lucide.drawable.lucide_ic_send_horizontal)
    val Block: ImageVector @Composable get() = ImageVector.vectorResource(Lucide.drawable.lucide_ic_ban)
    val Flag: ImageVector @Composable get() = ImageVector.vectorResource(Lucide.drawable.lucide_ic_flag)
    val More: ImageVector @Composable get() = ImageVector.vectorResource(Lucide.drawable.lucide_ic_ellipsis_vertical)
    val Megaphone: ImageVector @Composable get() = ImageVector.vectorResource(Lucide.drawable.lucide_ic_megaphone)
    val Frown: ImageVector @Composable get() = ImageVector.vectorResource(Lucide.drawable.lucide_ic_frown)
    val Warning: ImageVector @Composable get() = ImageVector.vectorResource(Lucide.drawable.lucide_ic_triangle_alert)
    val Ellipsis: ImageVector @Composable get() = ImageVector.vectorResource(Lucide.drawable.lucide_ic_ellipsis)
    val Palette: ImageVector @Composable get() = ImageVector.vectorResource(Lucide.drawable.lucide_ic_palette)
    val History: ImageVector @Composable get() = ImageVector.vectorResource(Lucide.drawable.lucide_ic_history)
    val Info: ImageVector @Composable get() = ImageVector.vectorResource(Lucide.drawable.lucide_ic_info)
    val Bell: ImageVector @Composable get() = ImageVector.vectorResource(Lucide.drawable.lucide_ic_bell_ring)
    val LogOut: ImageVector @Composable get() = ImageVector.vectorResource(Lucide.drawable.lucide_ic_log_out)
    val Home: ImageVector @Composable get() = ImageVector.vectorResource(Lucide.drawable.lucide_ic_house)
    val Chats: ImageVector @Composable get() = ImageVector.vectorResource(Lucide.drawable.lucide_ic_messages_square)
    val Settings: ImageVector @Composable get() = ImageVector.vectorResource(Lucide.drawable.lucide_ic_settings)
    val UserBlock: ImageVector @Composable get() = ImageVector.vectorResource(Lucide.drawable.lucide_ic_user_round_x)
    val ShieldAlert: ImageVector @Composable get() = ImageVector.vectorResource(Lucide.drawable.lucide_ic_shield_alert)
    val DoorOpen: ImageVector @Composable get() = ImageVector.vectorResource(Lucide.drawable.lucide_ic_door_open)
    val Laugh: ImageVector @Composable get() = ImageVector.vectorResource(Lucide.drawable.lucide_ic_laugh)
    val Wow: ImageVector @Composable get() = ImageVector.vectorResource(Lucide.drawable.lucide_ic_zap)
    val Flame: ImageVector @Composable get() = ImageVector.vectorResource(Lucide.drawable.lucide_ic_flame)
    val Check: ImageVector @Composable get() = ImageVector.vectorResource(Lucide.drawable.lucide_ic_check)
    val CheckCheck: ImageVector @Composable get() = ImageVector.vectorResource(Lucide.drawable.lucide_ic_check_check)
    val Lightbulb: ImageVector @Composable get() = ImageVector.vectorResource(Lucide.drawable.lucide_ic_lightbulb)
    val Hourglass: ImageVector @Composable get() = ImageVector.vectorResource(Lucide.drawable.lucide_ic_hourglass)
}
