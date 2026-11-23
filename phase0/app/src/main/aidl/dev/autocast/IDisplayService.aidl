package dev.autocast;

import android.view.Surface;

// Shizuku UserService(shell 권한 프로세스)에서 실행되는 서비스의 인터페이스.
// 한 메서드에 트랜잭션 번호를 지정하면 모두 지정해야 하므로 전부 명시한다.
interface IDisplayService {
    // AA(또는 폰 테스트 화면)의 Surface로 가상 디스플레이를 만든다. 반환값: displayId, 실패 시 -1
    int createDisplay(in Surface surface, int width, int height, int densityDpi) = 1;

    void releaseDisplay() = 2;

    // "패키지/액티비티" 형식의 컴포넌트를 가상 디스플레이에서 실행. 반환값: am 출력
    String launch(String component) = 3;

    void tap(float x, float y) = 4;

    // action: MotionEvent.ACTION_DOWN / ACTION_MOVE / ACTION_UP
    void touch(int action, float x, float y) = 5;

    void key(int keyCode) = 6;

    // 디버깅용: 현재 uid와 디스플레이 상태
    String status() = 7;

    // Shizuku 규약: 서비스 종료
    void destroy() = 16777114;
}
