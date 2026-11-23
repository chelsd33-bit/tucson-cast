package dev.autocast

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import rikka.shizuku.Shizuku

/**
 * 폰 쪽 화면.
 * 1) Shizuku 권한 요청과 서비스 연결
 * 2) "폰에서 테스트": 차 없이 SurfaceView에 가상 디스플레이를 띄워 구조를 검증
 * 3) 차 화면 앱 목록에 보일 즐겨찾기 선택
 */
class MainActivity : Activity() {

    private lateinit var statusView: TextView
    private lateinit var testSurface: SurfaceView
    private var testing = false

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { code, result ->
        if (code == CastController.SHIZUKU_REQUEST_CODE && result == PackageManager.PERMISSION_GRANTED) {
            CastController.bind(this)
        }
        refresh()
    }
    private val binderListener = Shizuku.OnBinderReceivedListener { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 48, 32, 32) }
        statusView = TextView(this).apply { textSize = 15f }
        root.addView(TextView(this).apply { text = "AutoCast Phase 0"; textSize = 22f })
        root.addView(statusView)

        root.addView(button("1. Shizuku 권한 요청") { CastController.requestPermission() })
        root.addView(button("2. 서비스 연결") { CastController.bind(this); refresh() })
        root.addView(button("3. 폰에서 테스트 시작/중지") { toggleTest() })

        testSurface = SurfaceView(this).apply { visibility = View.GONE }
        root.addView(testSurface, LinearLayout.LayoutParams(MATCH_PARENT, 0).apply { height = 1 })

        root.addView(TextView(this).apply {
            text = "\n차 화면 앱 목록에 먼저 보일 앱 (즐겨찾기)"
            textSize = 16f
        })
        val favorites = Favorites.get(this)
        CastController.launcherApps(this).forEach { (label, component) ->
            root.addView(CheckBox(this).apply {
                text = label
                isChecked = component.flattenToString() in favorites
                setOnCheckedChangeListener { _, on -> Favorites.toggle(this@MainActivity, component, on) }
            })
        }

        setContentView(ScrollView(this).apply { addView(root) })

        Shizuku.addBinderReceivedListenerSticky(binderListener)
        Shizuku.addRequestPermissionResultListener(permissionListener)
    }

    override fun onResume() {
        super.onResume()
        CastController.onStatus = { msg -> statusView.text = CastController.shizukuState() + "\n" + msg }
        refresh()
    }

    override fun onDestroy() {
        Shizuku.removeBinderReceivedListener(binderListener)
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        if (testing) CastController.detach()
        super.onDestroy()
    }

    private fun refresh() {
        statusView.text = CastController.shizukuState()
    }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        gravity = Gravity.CENTER
        setOnClickListener { onClick() }
    }

    // ---------------- 폰 테스트 모드 ----------------

    private val surfaceHolderCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {}
        override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
            if (w != TEST_W || h != TEST_H) return
            CastController.attach(this@MainActivity, holder.surface, TEST_W, TEST_H, TEST_DPI)
            // 확인용으로 설정 앱을 띄운다
            testSurface.postDelayed({
                packageManager.getLaunchIntentForPackage("com.android.settings")?.component
                    ?.let { CastController.launch(it) }
            }, 800)
        }
        override fun surfaceDestroyed(holder: SurfaceHolder) = CastController.detach()
    }

    private fun toggleTest() {
        testing = !testing
        if (testing) {
            val viewW = resources.displayMetrics.widthPixels - 64
            testSurface.layoutParams = testSurface.layoutParams.apply {
                width = viewW; height = viewW * TEST_H / TEST_W
            }
            testSurface.holder.setFixedSize(TEST_W, TEST_H)
            testSurface.holder.addCallback(surfaceHolderCallback)
            testSurface.setOnTouchListener { v, ev -> forwardTouch(v, ev); true }
            testSurface.visibility = View.VISIBLE
        } else {
            testSurface.holder.removeCallback(surfaceHolderCallback)
            testSurface.visibility = View.GONE
            CastController.detach()
        }
    }

    /** 폰 테스트 화면의 터치를 가상 디스플레이 좌표로 변환해 전달 (차에서는 AaGestureTranslator가 담당) */
    private fun forwardTouch(v: View, ev: MotionEvent) {
        val x = ev.x * TEST_W / v.width
        val y = ev.y * TEST_H / v.height
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP ->
                CastController.touch(ev.actionMasked, x, y)
            MotionEvent.ACTION_CANCEL -> CastController.touch(MotionEvent.ACTION_UP, x, y)
        }
    }

    private companion object {
        const val TEST_W = 1280
        const val TEST_H = 720
        const val TEST_DPI = 160
    }
}
