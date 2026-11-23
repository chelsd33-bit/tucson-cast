package dev.autocast

import android.graphics.Rect
import android.view.KeyEvent
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * 차 화면 전체를 가상 디스플레이로 채우는 메인 화면.
 * AA가 준 Surface를 그대로 Shizuku 서비스에 넘겨 VirtualDisplay의 출력으로 쓴다.
 */
class CastScreen(carContext: CarContext) : Screen(carContext) {

    private val gestures = AaGestureTranslator()
    private var lastStatus = "대기 중"

    private val surfaceCallback = object : SurfaceCallback {
        override fun onSurfaceAvailable(container: SurfaceContainer) {
            val surface = container.surface ?: return
            gestures.setSize(container.width, container.height)
            CastController.attach(carContext, surface, container.width, container.height, container.dpi)
        }

        override fun onSurfaceDestroyed(container: SurfaceContainer) {
            CastController.detach()
        }

        override fun onVisibleAreaChanged(visibleArea: Rect) {}
        override fun onStableAreaChanged(stableArea: Rect) {}

        override fun onClick(x: Float, y: Float) = gestures.onClick(x, y)
        override fun onScroll(distanceX: Float, distanceY: Float) = gestures.onScroll(distanceX, distanceY)
        override fun onFling(velocityX: Float, velocityY: Float) = gestures.onFling()
        override fun onScale(focusX: Float, focusY: Float, scaleFactor: Float) {
            // Phase 0 범위 밖 (핀치 줌)
        }
    }

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                carContext.getCarService(AppManager::class.java).setSurfaceCallback(surfaceCallback)
                CastController.onStatus = { lastStatus = it; invalidate() }
            }

            override fun onDestroy(owner: LifecycleOwner) {
                CastController.onStatus = null
                CastController.detach()
            }
        })
    }

    override fun onGetTemplate(): Template {
        val strip = ActionStrip.Builder()
            .addAction(
                Action.Builder().setTitle("앱")
                    .setOnClickListener { screenManager.push(AppListScreen(carContext)) }
                    .build()
            )
            .addAction(
                Action.Builder().setTitle("뒤로")
                    .setOnClickListener { CastController.key(KeyEvent.KEYCODE_BACK) }
                    .build()
            )
            .build()
        return NavigationTemplate.Builder()
            .setActionStrip(strip)
            .build()
    }
}
