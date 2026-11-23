package dev.autocast

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

/**
 * Android Auto 진입점. NAVIGATION 카테고리로 등록해야 Surface에 직접 그릴 수 있다.
 */
class AutoCastCarService : CarAppService() {

    /**
     * 이 서비스에 연결하는 호스트를 서명으로 검증한다.
     *
     * 이 서비스가 받은 클릭/스크롤은 shell 권한으로 가상 디스플레이에 주입되므로,
     * 모든 호스트를 허용(ALLOW_ALL_HOSTS_VALIDATOR)하면 폰의 다른 앱이 Android Auto인 척
     * 연결해 우리 권한으로 입력을 넣을 수 있다. 그래서 디버그 빌드에서도 허용 목록만 쓴다.
     *
     * hosts_allowlist_sample: Car App Library가 제공하는 Google 공식 호스트
     * (Android Auto 앱 com.google.android.projection.gearhead 등)의 서명 목록.
     * DHU(Desktop Head Unit)도 폰의 Android Auto 앱을 거쳐 연결되므로 함께 허용된다.
     * 라이브러리를 올리면 목록도 함께 갱신된다.
     */
    override fun createHostValidator(): HostValidator =
        HostValidator.Builder(applicationContext)
            .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)
            .build()

    override fun onCreateSession(): Session = object : Session() {
        override fun onCreateScreen(intent: Intent): Screen {
            CastController.bind(carContext)
            return CastScreen(carContext)
        }
    }
}
