package dev.autocast

import android.os.Handler
import android.os.Looper
import android.view.MotionEvent

/**
 * Android Auto의 SurfaceCallback은 원시 터치가 아니라 제스처만 준다.
 *  - onClick(x, y): 좌표 있음 → 탭
 *  - onScroll(dx, dy): 좌표 없음, 이동량만 있음 → 화면 중앙에서 시작하는 가상 드래그로 변환
 *  - onFling(vx, vy): 드래그를 즉시 끝내면 대상 앱이 마지막 MOVE 속도로 플링을 처리한다
 */
class AaGestureTranslator {
    private val handler = Handler(Looper.getMainLooper())
    private var width = 0
    private var height = 0
    private var dragging = false
    private var curX = 0f
    private var curY = 0f

    private val endDrag = Runnable { finishDrag() }

    fun setSize(w: Int, h: Int) {
        width = w; height = h
    }

    fun onClick(x: Float, y: Float) {
        finishDrag()
        CastController.tap(x, y)
    }

    fun onScroll(distanceX: Float, distanceY: Float) {
        if (width == 0) return
        if (!dragging) {
            dragging = true
            curX = width / 2f
            curY = height / 2f
            CastController.touch(MotionEvent.ACTION_DOWN, curX, curY)
        }
        // GestureDetector 규약: distance = 이전 위치 - 현재 위치
        curX = (curX - distanceX).coerceIn(0f, width - 1f)
        curY = (curY - distanceY).coerceIn(0f, height - 1f)
        CastController.touch(MotionEvent.ACTION_MOVE, curX, curY)
        handler.removeCallbacks(endDrag)
        handler.postDelayed(endDrag, IDLE_END_MS)
    }

    fun onFling() = finishDrag()

    private fun finishDrag() {
        handler.removeCallbacks(endDrag)
        if (!dragging) return
        dragging = false
        CastController.touch(MotionEvent.ACTION_UP, curX, curY)
    }

    private companion object {
        const val IDLE_END_MS = 120L
    }
}
