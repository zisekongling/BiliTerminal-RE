package com.RobinNotBad.BiliClient.ui.theme

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.view.View

class ThemeUtils {

    companion object {

        fun getPrimaryColor(): Int {
            return BiliColors.Primary
        }

        fun getPrimaryDarkColor(): Int {
            return BiliColors.PrimaryDark
        }

        fun getPrimaryLightColor(): Int {
            return BiliColors.PrimaryLight
        }

        fun getTextPrimaryDarkColor(): Int {
            return BiliColors.TextPrimaryDark
        }

        fun getTextSecondaryDarkColor(): Int {
            return BiliColors.TextSecondaryDark
        }

        fun getSurfaceDarkColor(): Int {
            return BiliColors.SurfaceDark
        }

        fun getCardDarkColor(): Int {
            return BiliColors.CardDark
        }

        fun getBackgroundDarkColor(): Int {
            return BiliColors.BackgroundDark
        }

        fun getDividerColor(): Int {
            return BiliColors.DividerColor
        }

        fun getPlayerProgressFillColor(): Int {
            return BiliColors.PlayerProgressFill
        }

        fun getLikeColor(): Int {
            return BiliColors.LikeColor
        }

        fun getFavColor(): Int {
            return BiliColors.FavColor
        }

        fun getShareColor(): Int {
            return BiliColors.ShareColor
        }

        fun getInfoColor(): Int {
            return BiliColors.Info
        }

        fun getBtnHoverColor(): Int {
            return BiliColors.BtnHover
        }

        fun getBtnPressedColor(): Int {
            return BiliColors.BtnPressed
        }

        fun getPrimaryTransparentColor(): Int {
            return BiliColors.PrimaryTransparent
        }

        fun getGrayColor(): Int {
            return BiliColors.Gray
        }

        fun createButtonBackground(normalColor: Int, pressedColor: Int): Drawable {
            val stateListDrawable = StateListDrawable()

            val pressedDrawable = GradientDrawable()
            pressedDrawable.setColor(pressedColor)
            pressedDrawable.cornerRadius = 12f

            val normalDrawable = GradientDrawable()
            normalDrawable.setColor(normalColor)
            normalDrawable.cornerRadius = 12f

            stateListDrawable.addState(intArrayOf(android.R.attr.state_pressed), pressedDrawable)
            stateListDrawable.addState(intArrayOf(android.R.attr.state_focused), pressedDrawable)
            stateListDrawable.addState(intArrayOf(), normalDrawable)

            return stateListDrawable
        }

        fun setViewBackground(view: View, color: Int) {
            val drawable = GradientDrawable()
            drawable.setColor(color)
            drawable.cornerRadius = 0f
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                view.background = drawable
            } else {
                view.setBackgroundDrawable(drawable)
            }
        }
    }
}
