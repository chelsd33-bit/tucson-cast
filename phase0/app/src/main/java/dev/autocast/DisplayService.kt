package dev.autocast

import android.annotation.SuppressLint
import android.content.AttributionSource
import android.content.Context
import android.content.ContextWrapper
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.lang.reflect.Method
import kotlin.system.exitProcess

/**
 * Shizuku UserService로 실행된다. 이 클래스의 코드는 우리 앱 프로세스가 아니라
 * shell(uid 2000) 권한 프로세스에서 돈다. 그래서 다른 앱을 띄울 수 있는 가상
 * 디스플레이 생성과 입력 주입이 가능하다.
 *
 * Shizuku는 (Context) 생성자를 우선 사용한다.
 */
@SuppressLint("PrivateApi", "DiscouragedPrivateApi", "BlockedPrivateApi")
class DisplayService(private val baseContext: Context?) : IDisplayService.Stub() {

    constructor() : this(null)

    private var virtualDisplay: VirtualDisplay? = null
    private var downTime = 0L

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            HiddenApiBypass.addHiddenApiExemptions("")
        }
        Log.i(TAG, "DisplayService started, uid=${Process.myUid()}")
    }

    // ---------------- 가상 디스플레이 ----------------

    override fun createDisplay(surface: Surface, width: Int, height: Int, densityDpi: Int): Int {
        releaseDisplay()
        // 일부 기기(제조사 롬)는 TRUSTED 계열 플래그를 거부하므로 기본 플래그로 한 번 더 시도한다.
        for (flags in listOf(DISPLAY_FLAGS, BASIC_FLAGS).distinct()) {
            try {
                val vd = displayManager().createVirtualDisplay(
                    "AutoCast", width, height, densityDpi, surface, flags
                )
                virtualDisplay = vd
                val id = vd.display.displayId
                Log.i(TAG, "virtual display created id=$id ${width}x$height@$densityDpi flags=0x${flags.toString(16)}")
                return id
            } catch (t: Throwable) {
                Log.e(TAG, "createVirtualDisplay failed flags=0x${flags.toString(16)}", t)
            }
        }
        return -1
    }

    override fun releaseDisplay() {
        virtualDisplay?.release()
        virtualDisplay = null
    }

    private fun displayId(): Int = virtualDisplay?.display?.displayId ?: -1

    /**
     * DisplayManager는 호출자의 uid와 패키지 이름이 일치하는지 확인한다.
     * shell uid로 돌고 있으므로 패키지 이름도 com.android.shell로 맞춘 Context를 넘긴다.
     */
    private fun displayManager(): DisplayManager {
        val ctor = DisplayManager::class.java.getDeclaredConstructor(Context::class.java)
        ctor.isAccessible = true
        return ctor.newInstance(ShellContext(baseContext))
    }

    private class ShellContext(base: Context?) : ContextWrapper(base) {
        override fun getPackageName() = SHELL_PACKAGE
        override fun getOpPackageName() = SHELL_PACKAGE
        override fun getApplicationContext(): Context = this

        override fun getAttributionSource(): AttributionSource =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                AttributionSource.Builder(Process.SHELL_UID).setPackageName(SHELL_PACKAGE).build()
            } else {
                super.getAttributionSource()
            }
    }

    // ---------------- 앱 실행 ----------------

    override fun launch(component: String): String {
        val id = displayId()
        if (id < 0) return "no display"
        val cmd = arrayOf(
            "am", "start", "--display", id.toString(),
            "-a", "android.intent.action.MAIN",
            "-c", "android.intent.category.LAUNCHER",
            "-f", "0x10000000", // FLAG_ACTIVITY_NEW_TASK
            "-n", component
        )
        return try {
            val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText()
            p.waitFor()
            Log.i(TAG, "launch $component on $id: $out")
            out
        } catch (t: Throwable) {
            Log.e(TAG, "launch failed", t)
            t.toString()
        }
    }

    // ---------------- 입력 주입 ----------------

    override fun tap(x: Float, y: Float) {
        touch(MotionEvent.ACTION_DOWN, x, y)
        touch(MotionEvent.ACTION_UP, x, y)
    }

    override fun touch(action: Int, x: Float, y: Float) {
        val now = SystemClock.uptimeMillis()
        if (action == MotionEvent.ACTION_DOWN) downTime = now
        val ev = MotionEvent.obtain(downTime, now, action, x, y, 0)
        ev.source = InputDevice.SOURCE_TOUCHSCREEN
        inject(ev)
        ev.recycle()
    }

    override fun key(keyCode: Int) {
        val now = SystemClock.uptimeMillis()
        for (action in intArrayOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            val ev = KeyEvent(
                now, now, action, keyCode, 0, 0,
                KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD
            )
            inject(ev)
        }
    }

    private fun inject(ev: InputEvent) {
        val id = displayId()
        if (id < 0) return
        try {
            setDisplayIdMethod.invoke(ev, id)
            injectMethod.invoke(inputManager, ev, INJECT_MODE_ASYNC)
        } catch (t: Throwable) {
            Log.e(TAG, "inject failed", t)
        }
    }

    // Android 14(API 34)부터 injectInputEvent는 InputManagerGlobal로 옮겨졌다.
    private val inputManager: Any by lazy {
        if (Build.VERSION.SDK_INT >= 34) {
            Class.forName("android.hardware.input.InputManagerGlobal")
                .getMethod("getInstance").invoke(null)!!
        } else {
            Class.forName("android.hardware.input.InputManager")
                .getDeclaredMethod("getInstance").invoke(null)!!
        }
    }

    private val injectMethod: Method by lazy {
        inputManager.javaClass.getMethod(
            "injectInputEvent", InputEvent::class.java, Int::class.javaPrimitiveType
        )
    }

    private val setDisplayIdMethod: Method by lazy {
        InputEvent::class.java.getMethod("setDisplayId", Int::class.javaPrimitiveType)
    }

    // ---------------- 기타 ----------------

    override fun status(): String =
        "uid=${Process.myUid()} display=${displayId()} sdk=${Build.VERSION.SDK_INT}"

    override fun destroy() {
        releaseDisplay()
        exitProcess(0)
    }

    companion object {
        private const val TAG = "AutoCast/Service"
        private const val SHELL_PACKAGE = "com.android.shell"
        private const val INJECT_MODE_ASYNC = 0

        // DisplayManager의 숨겨진 플래그 값 (AOSP 기준)
        private const val FLAG_PUBLIC = 1 shl 0
        private const val FLAG_OWN_CONTENT_ONLY = 1 shl 3
        private const val FLAG_SUPPORTS_TOUCH = 1 shl 6
        private const val FLAG_ROTATES_WITH_CONTENT = 1 shl 7
        private const val FLAG_DESTROY_CONTENT_ON_REMOVAL = 1 shl 8
        private const val FLAG_TRUSTED = 1 shl 10
        private const val FLAG_OWN_DISPLAY_GROUP = 1 shl 11
        private const val FLAG_ALWAYS_UNLOCKED = 1 shl 12

        private const val BASIC_FLAGS = FLAG_PUBLIC or FLAG_OWN_CONTENT_ONLY or FLAG_SUPPORTS_TOUCH or
            FLAG_ROTATES_WITH_CONTENT or FLAG_DESTROY_CONTENT_ON_REMOVAL

        private val DISPLAY_FLAGS: Int = run {
            var f = BASIC_FLAGS
            if (Build.VERSION.SDK_INT >= 33) {
                // TRUSTED: 시스템 데코(키보드 등)와 포커스가 정상 동작하도록
                f = f or FLAG_TRUSTED or FLAG_OWN_DISPLAY_GROUP or FLAG_ALWAYS_UNLOCKED
            }
            f
        }
    }
}
