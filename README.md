# 오리 QR 카메라

학교 태블릿에서 QR 코드 링크를 빠르게 열기 위한 아주 단순한 Android 카메라 앱입니다.

## 기능

- 카메라 권한 요청
- QR 코드 자동 인식
- `http://`, `https://`, `www.` 링크를 기본 브라우저로 열기
- 링크가 아닌 QR 내용은 화면에 표시
- 구형 태블릿 호환을 위한 Android 기본 Camera API 사용

## 빌드

```bash
./gradlew assembleDebug
```

빌드 결과물은 `app/build/outputs/apk/debug/app-debug.apk`에 생성됩니다.
