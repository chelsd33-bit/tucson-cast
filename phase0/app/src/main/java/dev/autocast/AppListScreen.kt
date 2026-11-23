package dev.autocast

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template

/**
 * 차 화면에서 실행할 앱을 고르는 목록.
 * AA는 목록 항목 수를 제한하므로(주행 중에는 더 적게) 호스트가 허용하는 만큼만 보여준다.
 * 폰 앱(MainActivity)에서 고른 즐겨찾기를 먼저 보여준다.
 */
class AppListScreen(carContext: CarContext) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val limit = runCatching {
            carContext.getCarService(ConstraintManager::class.java)
                .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
        }.getOrDefault(6)

        val list = ItemList.Builder()
        val favorites = Favorites.get(carContext)
        val apps = CastController.launcherApps(carContext)
            .sortedByDescending { it.second.flattenToString() in favorites } // 즐겨찾기 먼저
        apps.take(limit).forEach { (label, component) ->
            list.addItem(
                Row.Builder()
                    .setTitle(label)
                    .setOnClickListener {
                        CastController.launch(component)
                        screenManager.pop()
                    }
                    .build()
            )
        }
        return ListTemplate.Builder()
            .setTitle("실행할 앱")
            .setHeaderAction(Action.BACK)
            .setSingleList(list.build())
            .build()
    }
}
