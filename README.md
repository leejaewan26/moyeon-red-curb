# 빨간 연석 (red-curb)

**"P에 넣는 순간, 차가 '앞쪽 4m쯤 소화전이에요, 뒤로 4m만 옮기세요'라고 말해 줍니다."**
소방차가 물을 대는 자리(소화전 5m)를 그 앞에 서는 일반 승용차가 비워 두게 하는 Pleos Connect 차량 앱. 패키지 `com.moyeon.redcurb`.

- 기어가 P가 되는 순간 근처(12m) 소화전·등록한 소방차 전용구역을 찾는다(서울 소방용수시설 60,023곳 내장, 0.5km 격자 검색).
- 운전석에 앉아 있으면 **정차** 알림(짧은 음성 + 작은 카드), 운전석 문이 열리거나 운전자가 내리면 **주차** 알림(무드등 빨강 + 긴 음성 + 큰 카드).
- '위반'이라 단정하지 않고 상세위치(데이터의 주소·상세위치)와 시설 그림(지상식 기둥/지하식 맨홀)으로 눈으로 확인하게 한다. 위치 신뢰도가 낮으면 숫자를 빼고 "주변을 한 번 봐 주세요"로 낮춘다.
- 알림 뒤 10분 안에 P→D/R→P 로 8m 밖으로 옮기면 "비웠어요" + 이번 달 비운 횟수. 무드등은 원래 색으로 복원.
- 같은 대상·같은 상황(정차/주차) 음성은 하루 1회, [여기는 아니에요]는 30일 음소거. 주행 중(D/R/N)엔 상태 한 줄만 보이고 아무 말도 하지 않는다.
- 기록·전용구역은 차 안(SharedPreferences)에만 저장. 서버·계정·내보내기 없음.

## 실행 방법

```sh
adb install -r -g apk/모연_빨간연석_release.apk
adb shell am start -n com.moyeon.redcurb/.MainActivity
```

- `-g` 는 런타임 권한(`pleos.car.permission.FUSED_LOCATION` 등)을 바로 준다. 빼고 설치하면 첫 실행 때 권한 창이 뜬다.
- **시연**: 왼쪽 [시나리오 재생] → [▶ 30초 시연 재생]. 명세 7절 30초 시연(골목 도착·D → P(정차) → 문 열림(주차) → 문 닫고 R·4m 뒤 → P(비웠어요) → 기록 화면)을 실제와 같은 엔진 입력 함수로 흘린다. 재생 중엔 화면 위에 노란 "시나리오 재생 중" 띠가 뜨고, 실제 SDK 입력(기어·문·착석·위치)은 엔진에 넣지 않는다.
- **수동 입력**: 같은 화면의 [위치: 골목 도착 / 4m 뒤], [기어 P/D/R], [문 열기/닫기], [착석 ON/OFF]. SDK 콜백이 안 올 때의 대체 경로(명세 8 '첫 30분 확인')이며 엔진 입력은 실제 콜백과 같은 함수다.
- 시연 소화전: 마포구 독막로20나길 21 · 우강빌딩 앞 지하식 소화전(`마포-현장-001291`, 주변 78m 안에 다른 시설 없음). 시연 재생은 "이번 달 세 번째"를 보여 주려고 '예시 자리 (시연 예시)' 기록 2건을 넣는다(기록 화면에 표시).
- 오른쪽 'SDK 상태' 패널에 모든 SDK 호출 결과(성공/실패·콜백 값)가 남는다(logcat 태그 `RedCurb`).

### 빌드

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
sh ./gradlew test             # 상태 엔진 단위 테스트(순수 Kotlin, 23개)
sh ./gradlew assembleRelease  # ../keystore/keystore.properties 가 있으면 서명, 없으면 서명 없이
```

`local.properties` 에 `sdk.dir=<Android SDK 경로>` 가 필요하다(저장소에 없음).

## 테스트 환경

| 항목 | 값 |
|---|---|
| 에뮬레이터 | Pleos_Connect_v2.1.0_API_34 (Android 14, arm64), 화면 2560x1440, density 160 |
| 빌드 | Gradle 8.10.2, AGP 8.8.2, Kotlin 2.0.0, Compose BOM 2024.04.01, JDK 17, compileSdk/targetSdk 35, minSdk 28 |
| Pleos SDK | `ai.pleos.playground:Vehicle:2.3.2`, `ai.pleos.playground:FusedLocation:1.1.3`, `ai.pleos.playground:TextToSpeech:3.3.1` (저장소 `https://nexus-playground.pleos.ai/repository/maven-releases/`) |
| 권한 | `android.car.permission.CAR_POWERTRAIN`(기어), `pleos.car.permission.CAR_DOORS`, `CAR_SEATS`, `CAR_LIGHTS`, `CONTROL_CAR_LIGHTS`, `pleos.car.permission.FUSED_LOCATION`(dangerous, 런타임 요청), `pleos.car.permission.TTS_SERVICE`, `ai.pleos.playground.service.location.fused.FUSED_LOCATION_BINDING`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` |

`FUSED_LOCATION`·`TTS_SERVICE` 두 권한은 샘플 매니페스트에 없고, 에뮬레이터 실행 때 SDK 가 `SecurityException` 으로 요구해 추가했다.

## APK

- `apk/모연_빨간연석_release.apk` (release, minify 끔, 팀 서명 키로 서명)

## 사용 SDK API

| 순간 | API (javap 로 시그니처 확인) | 에뮬레이터 결과 |
|---|---|---|
| 연결 | `Vehicle(context).initialize()` | 성공 |
| 기어 | AAOS 표준 `Car.createCar(context)` → `CarPropertyManager.getProperty(VehiclePropertyIds.GEAR_SELECTION, 0)`, `registerCallback(callback, GEAR_SELECTION, SENSOR_RATE_ONCHANGE)` → `onChangeEvent(CarPropertyValue)`(`android.car.VehicleGear`). Pleos Gear 도메인은 SDK 2.3.2에서 deprecated·공개 문서 제외 → SDK 안내대로 AAOS 표준 GEAR_SELECTION(CarPropertyManager)으로 읽음 | `getProperty` = 4(GEAR_PARK), `registerCallback` 등록 성공(true), 등록 직후 GEAR_PARK 이벤트 수신. 기어 변화 이벤트는 user 빌드라 값 주입 불가로 미확인 |
| 운전석 문 | `Vehicle.getDoor().registerDoorPosition(DoorArea(DoorValue.ROW_1_LEFT), DoorPositionListener)` → `onPositionUpdated(Int?)`, `getCurrentDoorPosition(...)` | 등록 성공, 닫힌 상태 값 `0` 수신. 열림 값은 미확인(>0 을 열림으로 봄) |
| 운전석 착석 | `Vehicle.getSeat().registerSeatOccupancy(SeatArea(SeatValue.ROW_1_LEFT), SeatOccupancyListener)` → `onSeatOccupancyUpdated(Boolean?)` | 등록 성공, `false` 수신 |
| 무드등 | `Light.getAmbientLightBrightnessColor` / `getAmbientLightSwitch` 로 원래 값 저장 → `setAmbientLightSwitch(area, LightSwitch.ON, …)` → `setAmbientLightBrightnessColor(area, 0xFF0000, …)` → 끝나면 원래 색 복원. 영역은 `checkAmbientLightBrightnessColorCapabilities` 가 준 영역 | `SeatArea(ROW_1_LEFT)` 단독은 `area ID 0x1 not supported`. capability 영역으로 다시 해서 원래 색 `16777215`(0xFFFFFF) → `16711680`(0xFF0000) 쓰기·복원 성공. 색은 RGB 정수로 보임. 패널에 보이는지는 미확인이라 화면 빨간 테두리를 같이 켬 |
| 위치 | `FusedLocation(context).initialize()`, `isSupported()`, `registerFusedLocationCallback(FusedLocationListener)` → `onChanged(LocationData?)`: `latitude`, `longitude`, `heading`, `confidence` | `isSupported=true`, 1초마다 `onChanged` 수신(에뮬레이터 기본 위치 37.421998,-122.084, heading 0, confidence 5.0) |
| 음성 | `TextToSpeech(context, Mode.ON_DEVICE).initialize(OnInitializeListener)` → `setLocale(Locale.KOREA)` → `speak(text, true)`, `addEventListener` | 성공(onStart→onDone). `Locale.KOREAN`("ko")은 엔진이 `unsupported locale: ko-` 로 실패해 `ko_KR` 로 바꿈. 실패 시 Android 기본 TTS 로 대체하는 코드가 있으나 에뮬레이터에선 기본 TTS 초기화가 status=-1 |

쓰지 않는 것: `addProximityAlert`, NaviHelper, STT, 서버 TTS(`registerApp`), Pleos LLM SDK.

## 구조

```
app/src/main/java/com/moyeon/redcurb/
  engine/   순수 Kotlin(Android 의존 없음) — ParkingEngine(상태 엔진), Geo(명세 4-3 계산), TargetIndex(0.5km 격자·CSV), Phrases(문구), Scenario(30초 시연)
  sdk/      실제 SDK 어댑터 — VehicleAdapter, AmbientLight, LocationAdapter, Speaker
  RedCurbViewModel.kt  어댑터 → 엔진 → TTS·무드등·화면
  ui/       Jetpack Compose 화면(홈·알림 카드·기록·전용구역·시나리오)
app/src/test/  엔진 단위 테스트(정차→주차, 반경 11.9/12.1m, 앞·뒤, 비움 10분 창, 하루 1회 음성 등)
tools/convert_hydrants.py  원본 CSV → assets/hydrants.csv
```

## 데이터 출처 · 라이선스

- **서울특별시 소방재난본부 「서울시 소방용수시설 현황」** — 서울 열린데이터광장 OA-21306 (https://data.seoul.go.kr/dataList/OA-21306/S/1/datasetView.do), 파일 `서울시 소방재난본부_소방용수시설_20251231.csv`, **공공누리 제1유형(출처표시)**. 앱 홈 화면 아래에도 출처를 적었다.
- 가공: `tools/convert_hydrants.py` 로 위경도·시설유형·상세위치만 남김(`id,lat,lon,type,detail,usable`). 원본 60,171행 중 좌표가 깨졌거나 서울 범위 밖인 148행 제외 → 60,023곳. 시설유형코드는 같은 데이터셋의 `소방용수시설 표준등록 현행화_2026.xlsx` 머리글(01 지상식소화전 / 02 지하식소화전 / 03 급수탑 / 04 저수조 / 05 지하식상승식)을 따랐다. 좌표는 WGS84 위경도로 보임(종로-현장-001 = 우정국로 67 부근으로 확인).
- 과태료 문구 "8만 원 이상"은 보도 재인용(명세 2장).
