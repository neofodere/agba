package dev.neofodere.agba.shortcut

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import dev.neofodere.agba.app
import dev.neofodere.agba.data.Game
import dev.neofodere.agba.emulator.EmulatorActivity
import dev.neofodere.agba.ui.COVER_BOTTOM_ARGB
import dev.neofodere.agba.ui.COVER_TOP_ARGB

object ShortcutHelper {
    private const val ICON_SIZE = 192

    fun isSupported(context: Context): Boolean = ShortcutManagerCompat.isRequestPinShortcutSupported(context)

    fun requestPin(context: Context, game: Game): Boolean {
        if (!isSupported(context)) return false
        val shortcut = ShortcutInfoCompat.Builder(context, "game_${game.id}")
            .setShortLabel(game.title)
            .setLongLabel(game.title)
            .setIcon(IconCompat.createWithBitmap(renderIcon(context, game)))
            .setIntent(EmulatorActivity.intentFor(context, game.id))
            .build()
        return ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
    }

    private fun renderIcon(context: Context, game: Game): Bitmap {
        val bitmap = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val cover = context.app.library.loadCover(game.id, ICON_SIZE)
        if (cover != null) {
            val side = minOf(cover.width, cover.height)
            val left = (cover.width - side) / 2
            val top = (cover.height - side) / 2
            val source = Rect(left, top, left + side, top + side)
            canvas.drawBitmap(cover, source, Rect(0, 0, ICON_SIZE, ICON_SIZE), Paint(Paint.FILTER_BITMAP_FLAG))
            return bitmap
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(
            0f, 0f, 0f, ICON_SIZE.toFloat(),
            COVER_TOP_ARGB, COVER_BOTTOM_ARGB, Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, ICON_SIZE.toFloat(), ICON_SIZE.toFloat(), paint)
        val drawable = ContextCompat.getDrawable(context, game.platform.iconRes)
        if (drawable != null) {
            val inset = (ICON_SIZE * 0.17f).toInt()
            drawable.setBounds(inset, inset, ICON_SIZE - inset, ICON_SIZE - inset)
            drawable.draw(canvas)
        }
        return bitmap
    }
}
