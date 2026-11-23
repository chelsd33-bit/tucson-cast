package dev.autocast

import android.content.ComponentName
import android.content.Context

/** 차 화면 앱 목록에 표시할 앱 (AA 목록 항목 수 제한 때문에 폰에서 미리 고른다) */
object Favorites {
    private const val PREFS = "favorites"
    private const val KEY = "components"

    fun get(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(KEY, emptySet())!!

    fun toggle(context: Context, component: ComponentName, on: Boolean) {
        val cur = get(context).toMutableSet()
        if (on) cur.add(component.flattenToString()) else cur.remove(component.flattenToString())
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet(KEY, cur).apply()
    }
}
