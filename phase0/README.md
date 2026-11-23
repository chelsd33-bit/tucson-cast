# AutoCast Phase 0: 기술 검증 앱

PRD(`../docs/PRD.md`)의 Phase 0 목표인 "투싼 화면에 폰의 아무 앱이나 한 번 띄워 보기"를 검증하는 최소 앱입니다.
개인 테스트 전용이며, 주행 중 차단 같은 안전 기능(PRD S1~S4)은 Phase 1에서 넣습니다. **테스트는 반드시 정차 중에 하세요.**

## 구조

```
AA 호스트 ──Surface──▶ CastScreen (SurfaceCallback)
                          │ CastController.attach()
                          ▼  AIDL (Surface는 Parcelable로 전달)
                    DisplayService  ← Shizuku UserService, shell 권한
                          ├─ DisplayManager.createVirtualDisplay(AA Surface)
                          ├─ am start --display <id> <앱>
                          └─ InputManager.injectInputEvent(displayId)
```

| 파일 | 역할 |
|---|---|
| `IDisplayService.aidl` | 앱 프로세스와 shell 프로세스 사이 인터페이스 |
| `DisplayService.kt` | shell 권한으로 가상 디스플레이 생성, 앱 실행, 입력 주입 |
| `CastController.kt` | Shizuku 바인딩, Surface 연결/해제, 요청 전달 |
| `AutoCastCarService.kt` / `CastScreen.kt` | Android Auto 진입점, AA Surface 수신, 액션 버튼(앱/뒤로) |
| `AppListScreen.kt` | 차 화면에서 실행할 앱 고르기 |
| `AaGestureTranslator.kt` | AA 제스처(탭/스크롤/플링)를 터치 이벤트로 변환 |
| `MainActivity.kt` | Shizuku 권한, 폰 테스트 모드, 즐겨찾기 선택 |

## 빌드

```
./gradlew assembleDebug
```
결과물: `app/build/outputs/apk/debug/app-debug.apk`

## 테스트 순서

### 1. 준비 (폰)
1. Google Play에서 **Shizuku**를 설치하고, 앱 안내에 따라 "무선 디버깅으로 시작"합니다.
2. 빌드한 `app-debug.apk`를 설치합니다(`adb install` 또는 파일로 옮겨 설치).
3. AutoCast를 열고 **1. Shizuku 권한 요청** → 허용 → **2. 서비스 연결**을 누릅니다.
   상태에 `서비스 연결됨: uid=2000 ...`이 나오면 정상입니다.

### 2. 폰에서 먼저 검증 (차 없이)
**3. 폰에서 테스트 시작**을 누르면 1280×720 가상 디스플레이가 화면 안에 작게 뜨고 설정 앱이 실행됩니다.
그 작은 화면을 터치해 설정 앱이 조작되면 핵심 구조(가상 디스플레이 + 앱 실행 + 입력 주입)가 이 폰에서 동작한다는 뜻입니다.
이와 함께 아래 "차에 띄울 앱" 즐겨찾기를 몇 개 골라 두세요(AA 목록은 항목 수가 제한됩니다).

### 3. Android Auto 설정
1. 폰 설정 → Android Auto → 맨 아래 "버전"을 10번 눌러 개발자 모드를 켭니다.
2. 오른쪽 위 메뉴 → 개발자 설정 → **알 수 없는 출처** 체크.
3. (선택) 같은 메뉴에서 동영상 해상도를 최대로 설정합니다.
4. (선택) 차에 가기 전 PC의 Desktop Head Unit(DHU)으로 먼저 확인할 수 있습니다.

### 4. 투싼 실차 (정차 중)
1. AA를 연결하고 AA 런처에서 **AutoCast**를 엽니다.
2. 오른쪽 **앱** 버튼 → 앱을 고르면 차 화면에 그 앱이 떠야 합니다.
3. 탭, 스크롤, **뒤로** 버튼을 확인합니다.

## 결과 체크리스트 (PRD Phase 0 종료 조건)

결과를 알려주시면 Phase 1 설계에 반영합니다. 실패하면 `adb logcat -s AutoCast/Service AutoCast/Controller` 로그를 같이 주세요.

- [ ] 폰 테스트 모드에서 설정 앱이 보이고 터치가 된다
- [ ] 투싼 AA 런처에 AutoCast가 보인다
- [ ] 차 화면에 선택한 앱이 표시된다 (Surface를 shell 프로세스로 넘기는 구조 검증)
- [ ] 차 화면 탭이 앱에 전달된다
- [ ] 스크롤(드래그)이 동작한다
- [ ] 그 앱의 소리가 차 스피커로 나온다
- [ ] 차에 앱이 떠 있는 동안 폰에서 다른 앱을 쓸 수 있다
- 확인: 폰 기종/Android 버전, AA 유선/무선, 차 화면 해상도(상태 메시지의 `WxH@dpi`)

## 알려진 한계 (Phase 0)
- AA는 탭/스크롤/플링/핀치 제스처만 주므로 롱프레스, 핀치는 아직 지원하지 않습니다. 스크롤은 화면 중앙에서 시작하는 드래그로 변환합니다.
- 텍스트 입력(키보드)은 지원하지 않습니다.
- 일부 앱은 보조 디스플레이 실행을 거부하거나 기본 화면으로 옮겨 갈 수 있습니다.
- DRM/FLAG_SECURE 앱은 검은 화면으로 나옵니다.
- 디버그 빌드는 모든 AA 호스트를 허용합니다(`ALLOW_ALL_HOSTS_VALIDATOR`). 배포용으로 쓰면 안 됩니다.
