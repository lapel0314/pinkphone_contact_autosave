# 핑크폰 연락처 자동 저장 APK

공기계에서 Supabase `customers`를 조회해 고객 연락처를 자동 저장하는 별도 Android 프로젝트입니다.

## 범위

- Supabase email/password 로그인
- `join_date` 기준 당일 고객 조회
- 서버의 `name`, `phone`, `memo`를 연락처 이름, 휴대폰 번호, 메모로 저장
- 매일 20:00 연락처 일괄 저장 예약
- 앱 화면을 닫아도 20:00 작업 실행
- 기기 재부팅 후 20:00 예약 복구
- 수동 테스트용 `오늘 연락처 일괄 저장` 버튼
- 과거 고객 백필용 `서버 전체 연락처 저장` 버튼

카카오톡 발송은 이 앱에서 하지 않습니다. 발송은 사람이 직접 수동으로 처리합니다.

Android 설정에서 앱을 강제 종료하면 예약 알람이 중지되므로 앱을 한 번 다시 열어야 합니다.

## 설정

앱 첫 화면에서 Supabase URL, anon key, 이메일, 비밀번호를 입력한 뒤 `로그인`을 누릅니다.
비밀번호는 저장하지 않고, access token과 refresh token만 로컬에 저장합니다.

## 빌드

```bash
./gradlew assembleDebug
```

APK 위치:

```text
app/build/outputs/apk/debug/app-debug.apk
```
