# MARK Audio — аудит YaMusicSDK и Yamulite (22.11.2026)

## Область и метод

По запросу владельца подробно изучены **исходные файлы** текущих default-веток:
- **YaMusicSDK:** https://github.com/Yellastro2/YaMusicSDK — `master`, дерево Git `bff7324fb5e2da176b55ca4ca7a00e038d68bdae`, 107 элементов (файлы/каталоги).
- **YaMuLite Android:** https://github.com/dpronyaev/yamulite-android — `main`, дерево Git `4791b00df7964c12a14d6199850f7ccd57cef02a`, 118 элементов.

Проверены README, Gradle, реализация OAuth, сетевые клиенты, модели DTO, чтение каталога, разрешение аудио, Media3 и механизм хранения токена. GitHub-метаданные показывают последний push YaMusicSDK **03.11.2026**, YaMuLite **30.09.2026**. **Не проводились** сборка этих чужих проектов, аудит всех строк, авторизованные live-запросы с их OAuth-клиентами или испытания на Huawei. Указанная функциональность — *реализована в исходниках*, а не подтверждена работой с нашим OAuth-клиентом.

Основная цель: понять, применимы ли решения к **собственному OAuth Client ID MARK Audio** и установленным результатам `GET /account/status -> HTTP 403` (Issue #46, тесты Huawei 0.7.27–0.7.28). Не импортировать чужие токены/Client ID и не менять рабочую авторизацию.

## 1. YaMusicSDK — Kotlin/JVM библиотека

### 1.1 Авторизация
- [`YandexDeviceAuth.kt`](https://github.com/Yellastro2/YaMusicSDK/blob/master/src/main/java/com/yellastrodev/yamusicsdk/auth/YandexDeviceAuth.kt) реализует `POST https://oauth.yandex.ru/device/code`, затем polling `/token`, `grant_type=device_code`, с переданными вызывающим кодом `clientId` и `clientSecret`. Обрабатываются `authorization_pending`, истечение срока, сетевые ошибки/отмена корутин; есть refresh token.
- [`DeviceAuthTransport.kt`](https://github.com/Yellastro2/YaMusicSDK/blob/master/src/main/java/com/yellastrodev/yamusicsdk/auth/DeviceAuthTransport.kt) использует OkHttp и передаёт заголовок `X-Yandex-Music-Client`, значение которого изображает определённый тип официального Android-клиента. Этот заголовок не является доказательством отдельного допуска независимого приложения.
- Полученные токены **не сохраняются библиотекой**: README явно возлагает хранение на вызывающее приложение. В MARK Audio действует собственный AES-GCM/Android Keystore; подмена этого слоя не требуется.
- Наличие подходящих clientId/clientSecret нельзя предполагать. В частности, публичный идентификатор нашего API-type OAuth и кодовая PKCE-схема **не эквивалентны** готовому Device Flow с client secret.

### 1.2 Каталог и модели
- [`YamApiClient.kt`](https://github.com/Yellastro2/YaMusicSDK/blob/master/src/main/java/com/yellastrodev/yamusicsdk/YamApiClient.kt) — фасад над `AccountApi`, `SearchApi`, `PlaylistApi`, `LikesApi`, `TrackApi`, `RotorApi`, `LandingApi`, `AlbumApi`, `ArtistApi`, `DownloadApi`, `CoverApi`.
- Поддерживаются поиск/подсказки, лайки, списки/создание/редактирование плейлистов, треки по ID, «Моя волна», landing и play-audio telemetry.
- [`YaTrack.kt`](https://github.com/Yellastro2/YaMusicSDK/blob/master/src/main/java/com/yellastrodev/yamusicsdk/entities/YaTrack.kt): ID с сериализатором **число или строка**, nullable duration/cover, необязательные artists/albums, `available` и некоторые флаги доступности.
- [`SearchModels.kt`](https://github.com/Yellastro2/YaMusicSDK/blob/master/src/main/java/com/yellastrodev/yamusicsdk/search/SearchModels.kt) различает типы `track/artist/album/playlist/user/podcast/...` и хранит `total/perPage/order/results`, best match, орфографические подсказки.
- [`PlaylistModels.kt`](https://github.com/Yellastro2/YaMusicSDK/blob/master/src/main/java/com/yellastrodev/yamusicsdk/playlists/PlaylistModels.kt) включает `PlaylistDetails(playlist,tracks)`. DTO — полезные **примеры схем**, но их фактическая совместимость с нашими токенами не проверена.

### 1.3 Сеть и аудио
- [`YamHttpTransport.kt`](https://github.com/Yellastro2/YaMusicSDK/blob/master/src/main/java/com/yellastrodev/yamusicsdk/network/YamHttpTransport.kt): OkHttp, отдельная политика retry/429, конфигурируемый прокси, категоризация ошибок (см. [`YamResult.kt`](https://github.com/Yellastro2/YaMusicSDK/blob/master/src/main/java/com/yellastrodev/yamusicsdk/network/YamResult.kt)); default base URL `https://api.music.yandex.net`. Authorization: OAuth token.
- [`DownloadApi.kt`](https://github.com/Yellastro2/YaMusicSDK/blob/master/src/main/java/com/yellastrodev/yamusicsdk/download/DownloadApi.kt) читает `/tracks/{id}/download-info`, XML и конструирует прямой URL через известный из неофициальных реализаций алгоритм. Есть `downloadBytes/downloadTo`. **Это закрытый механизм выдачи потоков, а не лицензионный контракт Media3**; не копировать эту часть в MARK Audio без разрешения.
- Архитектурно полезны `YamResult` и отдельные error/transport/model слои; модель работы с ошибками и 429 заслуживает самостоятельной реализации/тестирования.

### 1.4 Зависимости / релиз
- [`build.gradle.kts`](https://github.com/Yellastro2/YaMusicSDK/blob/master/build.gradle.kts): Kotlin JVM с serialization plugin `2.1.20`, target JVM 11; Coroutines `1.8.1`, kotlinx.serialization JSON `1.7.3`, OkHttp `4.12.0`, JUnit `4.13.2`.
- README: Gradle `9.1` и JDK 17, **нет Gradle Wrapper**, не опубликовано в Maven, предполагается подключение из исходников. Требуется самостоятельная проверка Android/D8, dependency compatibility и пересборки относительно нашего Gradle. Прямое добавление модуля сейчас необоснованно.
- В дереве **не найден отдельный LICENSE/COPYING-файл**, и GitHub API возвращает `license: null`; однако README **прямо заявляет LGPL-3.0** и происхождение от LGPL-3.0 MarshalX/yandex-music-api. Для повторного использования нужно уточнить фактический текст лицензии и соблюдать LGPL, уведомления и условия распространения/замены компонентов. Без разрешения не копировать код в закрытую кодовую базу.

## 2. YaMuLite — Android приложение Kotlin/Compose

### 2.1 Авторизация: главный риск
- [`AuthRepository.kt`](https://github.com/dpronyaev/yamulite-android/blob/main/app/src/main/java/dev/pdv/yamulite/data/auth/AuthRepository.kt): Device Flow и polling `/token` через `AuthApi`, поддерживает refresh. В исходном коде **жёстко прописаны сторонние OAuth Client ID и Client Secret** (их значения сюда сознательно не переносим). README прямо указывает, что используется известная регистрация сообщества.
- [`TokenStore.kt`](https://github.com/dpronyaev/yamulite-android/blob/main/app/src/main/java/dev/pdv/yamulite/data/auth/TokenStore.kt): access/refresh token сохраняются как **обычные строковые значения Preferences DataStore**, а не в Android Keystore. Для MARK Audio это регресс конфиденциальности; не перенимать.
- [`TokenRefreshAuthenticator.kt`](https://github.com/dpronyaev/yamulite-android/blob/main/app/src/main/java/dev/pdv/yamulite/data/music/TokenRefreshAuthenticator.kt): mutex предотвращает параллельное обновление токена; повтор ограничен. Полезный паттерн для собственной совместимой реализации, если потребуется refresh.

### 2.2 Каталог и DTO
- [`MusicApi.kt`](https://github.com/dpronyaev/yamulite-android/blob/main/app/src/main/java/dev/pdv/yamulite/data/music/MusicApi.kt): `/account/status`, `/search`, `/users/{uid}/likes/tracks`, `POST /tracks`, лайки, сведения об артисте/альбоме, download-info. **В данном интерфейсе не реализован список/редактирование личных плейлистов**.
- [`MusicRepository.kt`](https://github.com/dpronyaev/yamulite-android/blob/main/app/src/main/java/dev/pdv/yamulite/data/music/MusicRepository.kt): `uid` берётся из `/account/status`, кэшируется и используется для избранного; есть поиск track/artist/album, `favoritesPage` c дозагрузкой метаданных пачками по 50 ID и `StateFlow` избранных ID.
- [`MusicDtos.kt`](https://github.com/dpronyaev/yamulite-android/blob/main/app/src/main/java/dev/pdv/yamulite/data/music/dto/MusicDtos.kt): `ApiResponse<T>`, track/artist/album/search/library DTO с nullable/default полями; [`FlexibleStringSerializer`](https://github.com/dpronyaev/yamulite-android/blob/main/app/src/main/java/dev/pdv/yamulite/data/network/Serializers.kt) обрабатывает ID в JSON как число или строку. Это хороший пример устойчивых моделей при нестабильных данных.
- Непосредственно использует `api.music.yandex.net` + `Authorization: OAuth ...`; [`AuthInterceptor.kt`](https://github.com/dpronyaev/yamulite-android/blob/main/app/src/main/java/dev/pdv/yamulite/data/music/AuthInterceptor.kt) задаёт собственный User-Agent (не решение entitlement).

### 2.3 Media3 и офлайн
- [`AudioPlayer.kt`](https://github.com/dpronyaev/yamulite-android/blob/main/app/src/main/java/dev/pdv/yamulite/data/playback/AudioPlayer.kt): MediaController, очередь, `StateFlow`, seek, пауза, resume.
- [`PlaybackService.kt`](https://github.com/dpronyaev/yamulite-android/blob/main/app/src/main/java/dev/pdv/yamulite/data/playback/PlaybackService.kt): Media3 `MediaSessionService` и ExoPlayer.
- [`ResolvingDataSourceFactory.kt`](https://github.com/dpronyaev/yamulite-android/blob/main/app/src/main/java/dev/pdv/yamulite/data/playback/ResolvingDataSourceFactory.kt): псевдо-URI `yamulite://track/{id}`, при открытии `DataSpec` выбирается локальный файл либо динамически разрешённый URL; URL кэшируется в LRU (20 записей), используются `FileDataSource` и `DefaultHttpDataSource`.
- [`StreamUrlResolver.kt`](https://github.com/dpronyaev/yamulite-android/blob/main/app/src/main/java/dev/pdv/yamulite/data/music/StreamUrlResolver.kt): принимает данные `download-info`, формирует аудиоссылки через неофициальный механизм (ключ/схема не переносится); не означает право на свободный audio-stream.
- [`DownloadManager.kt`](https://github.com/dpronyaev/yamulite-android/blob/main/app/src/main/java/dev/pdv/yamulite/data/playback/DownloadManager.kt): WorkManager, файлы в private app storage, состояния прогресса. Офлайн-копирование лицензированной музыки допустимо **только при соответствующем праве/условиях**; в MARK Audio не добавлять его на основе этого кода.
- Одна особенность требует дополнительной оценки: кэш прямых URL не учитывает явно их серверный срок жизни; для подписанных краткоживущих ссылок возможны просроченные записи. Также статус фонового воспроизведения нельзя объявлять подтверждённым на EMUI 15 по наличию `MediaSessionService`.

### 2.4 Зависимости и лицензия
- [`libs.versions.toml`](https://github.com/dpronyaev/yamulite-android/blob/main/gradle/libs.versions.toml): Kotlin `2.0.21`, AGP `8.10.0`, Compose BOM `2024.12.01`, Hilt `2.52` + KSP, Retrofit `2.11.0`, OkHttp `4.12.0`, kotlinx.serialization `1.7.3`, Coil `3.2.0`, Media3 `1.10.1`, DataStore `1.1.1`, WorkManager `2.9.1`.
- [`app/build.gradle.kts`](https://github.com/dpronyaev/yamulite-android/blob/main/app/build.gradle.kts): compile/target SDK `36`, min `26`, Java 17; приложение с Hilt. MARK Audio уже использует Compose + Media3 и не нуждается в замене своей архитектуры Hilt только ради чужого API.
- Hilt/Jetpack не тождественны Google Play Services; без специальной интеграции в коде они не доказывают обязательную зависимость от GMS, но Huawei-тест необходим.
- **Нет обнаруженного LICENSE/COPYING-файла**; GitHub API сообщает `license: null`, README не предоставляет явно сформулированную лицензию на исходный код. Публичная доступность GitHub **не даёт автоматически права копировать/модифицировать/распространять** исходники как свои. До явного разрешения владельца или лицензии — только анализ подходов и собственная реализация.

## 3. Сравнение применимости к MARK Audio

| Направление | YaMusicSDK | YaMuLite | Решение |
|---|---|---|---|
| OAuth без чужой регистрации | Да, параметры вводятся извне, но Device Flow требует подходящий client secret | Нет, клиент жёстко встроен | Оставить наш LoginSDK/PKCE и свой Client ID |
| Доступ к личному каталогу **с нашим токеном** | Не доказан | Не доказан | Gate: официальный допустимый метод и успешный реальный тест |
| Типы каталога / DTO | Широкие модели | Устойчивые compact DTO, пагинация избранного | Составить собственный Kotlin-контракт с fixture-тестами |
| Ошибки сети и 429 | Явный `YamResult` и политики retry | Retrofit, refresh mutex | Взять **идею**, не переносить заголовки/клиентские секреты |
| Media3 | Не является Android-плеером | Уже есть controller/service/resolving datasource | Имеющийся Media3 MARK Audio сохранить |
| Audio links | Неофициальная схема | Неофициальная схема | Не копировать механизм подписи URL и аудио-обходы |
| Хранение токенов | Делегировано потребителю | Не шифрует DataStore | Сохранить Keystore AES-GCM MARK Audio |
| Лицензия | README: LGPL-3.0; отдельный файл не найден | Нет установленной открытой лицензии | Не копировать до проверки/разрешения |
| Huawei EMUI 15 | Не тестировался | Не тестировался | Нужны реальные тесты |

## 4. Риски и подтверждённые ограничения

1. **Работоспособность другого приложения ≠ разрешение на чужой Client ID.** Наш Music OAuth API-type/user-type (включая оба scopes в 0.7.28) прошёл Яндекс ID, но `GET /account/status` получил 403. Ни одна библиотека не устанавливает причину отказа или разрешённый обход.
2. YaMuLite использует стороннюю OAuth-регистрацию. Её копирование нарушит принцип самостоятельного Client ID и не доказывает разрешений Яндекса на распространение нашего Android-клиента.
3. В YaMusicSDK специальный `X-Yandex-Music-Client` не следует трактовать как универсальное устранение 403. Предыдущий независимый A/B-тест с другим рабочим токеном показывал ответ 2xx и без заголовка; политика сервера неизвестна.
4. Яндекс Музыка может менять недокументированный протокол без уведомления; использование закрытых механизмов потока не заменяет разрешений правообладателей и условий сервиса.
5. Для Android 0.7.28 нельзя механически добавлять целые проекты: они имеют разные toolchains, схемы хранения токенов, лицензии и UX; интеграция существенно повысит риски без решения OAuth-entitlement.

## 5. Конкретное решение для разработки

**Не копировать YaMuLite в MARK Audio и не подключать YaMusicSDK как зависимость в production прямо сейчас.** Вместо этого создать свой небольшой `MusicCatalogGateway` *только после определения допустимого способа обращения к серверу*. Интерфейс может заранее предусматривать:
- `search(query, page): Result<Page<TrackSummary>>`;
- `likedTracks(page): Result<Page<TrackSummary>>`;
- `playlists(): Result<List<PlaylistSummary>>`;
- `tracks(ids): Result<List<TrackSummary>>`;
- `capabilities`: явно не обещать поток без подтверждённого Media3/DRM контракта;
- `MusicError` с разграничением `Unauthorized`, `Forbidden`, `RateLimited`, `Network`, `InvalidResponse`;
- `TrackSummary.id` как String (обрабатывать и JSON-number и JSON-string), nullable поля и устойчивый парсинг;
- отдельные redacted unit tests на публично созданных синтетических fixtures; недоступный API не подменять выдуманной «успешной загрузкой».

Если заказчик хочет экспериментальную реализацию, можно подготовить **строго изолированный mock/fixture UI** для архитектурной проверки, но это не доказательство работы живого каталога и не повод выпускать новую «интеграцию».

**Следующий настоящий gate:** официальное пояснение, поддерживает ли Яндекс независимые OAuth-клиенты для методов каталога (включая `music:api-public`/`music:major-public-api`), и получение разрешённого read-only теста с собственным Client ID на Huawei. После этого — минимальный gateway, тесты, CI, только потом подписанный релиз.

## Проверки выполнены

- В GitHub изучены конкретные классы, Gradle файлы, модели, OAuth transport и Media3 components обоих репозиториев.
- Проверены дерево файлов на LICENSE/COPYING и GitHub поле license у обоих проектов; `MarshalX/yandex-music-api` для сверки происхождения действительно обозначен GitHub как LGPL-3.0.
- **Не выполнялись** собственные сетевые вызовы к закрытому Music API с чужими credentials; токены и Client secret не передавались пользователем и не сохранялись в отчёте.
- **Не выполнялись** чужие сборки и Huawei-device тесты; отсутствие live-проверки указано отдельно.
- **Нет изменений Kotlin-кода, авторизации, versionCode или APK MARK Audio 0.7.28.**
