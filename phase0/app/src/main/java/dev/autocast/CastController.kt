package dev.autocast

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Surface
import rikka.shizuku.Shizuku
import java.util.concurrent.Executors

/**
 * 앱 프로세스 쪽 중앙 관리자.
 * - Shizuku UserService(DisplayService) 바인딩
 * - Surface(AA 또는 폰 테스트 화면) 연결과 해제
 * - 입력/앱 실행 요청 전달
 *
 * 모든 binder 호출은 순서 보장을 위해 단일 스레드에서 실행한다.
 */
object CastController {
    private const val TAG = "AutoCast/Controller"
    const val SHIZUKU_REQUEST_CODE = 1001

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    @Volatile private var service: IDisplayService? = null
    private var pending: PendingSurface? = null
    @Volatile var displayId: Int = -1
        private set

    /** UI 갱신용 상태 메시지 리스너 */
    var onStatus: ((String) -> Unit)? = null

    private data class PendingSurface(val surface: Surface, val w: Int, val h: Int, val dpi: Int)

    // ---------------- Shizuku ----------------

    fun shizukuState(): String = when {
        !Shizuku.pingBinder() -> "Shizuku 미실행 (Shizuku 앱에서 시작하세요)"
        Shizuku.isPreV11() -> "Shizuku 버전이 너무 낮음"
        Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED -> "Shizuku 권한 없음"
        service == null -> "Shizuku 준비됨, 서비스 미연결"
        else -> "서비스 연결됨"
    }

    fun requestPermission() {
        if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
        }
    }

    private fun userServiceArgs(context: Context) = Shizuku.UserServiceArgs(
        ComponentName(context.packageName, DisplayService::class.java.name)
    )
        .daemon(false)
        .processNameSuffix("display")
        .debuggable(BuildConfig.DEBUG)
        .version(BuildConfig.VERSION_CODE)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (binder == null || !binder.pingBinder()) return
            service = IDisplayService.Stub.asInterface(binder)
            Log.i(TAG, "service connected")
            io.execute {
                status("서비스 연결됨: " + runCatching { service?.status() }.getOrNull())
                pending?.let { p -> pending = null; doAttach(p) }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            displayId = -1
            status("서비스 연결 끊김")
        }
    }

    /** 권한이 있으면 UserService에 바인딩. 여러 번 호출해도 안전하다. */
    fun bind(context: Context): Boolean {
        if (service != null) return true
        if (!Shizuku.pingBinder() || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            status(shizukuState())
            return false
        }
        return try {
            Shizuku.bindUserService(userServiceArgs(context.applicationContext), connection)
            true
        } catch (t: Throwable) {
            Log.e(TAG, "bindUserService failed", t)
            status("바인딩 실패: $t")
            false
        }
    }

    // ---------------- Surface ----------------

    fun attach(context: Context, surface: Surface, w: Int, h: Int, dpi: Int) {
        val p = PendingSurface(surface, w, h, dpi)
        if (service == null) {
            pending = p
            bind(context)
            return
        }
        io.execute { doAttach(p) }
    }

    private fun doAttach(p: PendingSurface) {
        val s = service ?: run { pending = p; return }
        displayId = runCatching { s.createDisplay(p.surface, p.w, p.h, p.dpi) }.getOrDefault(-1)
        status(
            if (displayId >= 0) "가상 디스플레이 #$displayId (${p.w}x${p.h}@${p.dpi}dpi)"
            else "가상 디스플레이 생성 실패 (logcat의 AutoCast/Service 확인)"
        )
    }

    fun detach() {
        pending = null
        io.execute {
            runCatching { service?.releaseDisplay() }
            displayId = -1
            status("디스플레이 해제됨")
        }
    }

    // ---------------- 입력과 앱 실행 ----------------

    fun launch(component: ComponentName) = io.execute {
        val out = runCatching { service?.launch(component.flattenToString()) }.getOrElse { it.toString() }
        status("실행: ${component.packageName}\n${out?.trim()}")
    }

    fun tap(x: Float, y: Float) = io.execute { runCatching { service?.tap(x, y) } }

    fun touch(action: Int, x: Float, y: Float) = io.execute { runCatching { service?.touch(action, x, y) } }

    fun key(keyCode: Int) = io.execute { runCatching { service?.key(keyCode) } }

    // ---------------- 유틸 ----------------

    /** 런처에 표시되는 앱 목록 (라벨 순) */
    fun launcherApps(context: Context): List<Pair<String, ComponentName>> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != context.packageName }
            .map { it.loadLabel(pm).toString() to ComponentName(it.activityInfo.packageName, it.activityInfo.name) }
            .sortedBy { it.first.lowercase() }
    }

    private fun status(msg: String) {
        Log.i(TAG, msg)
        main.post { onStatus?.invoke(msg) }
    }
}
