# Перевірки

## Автоматичні

0.6.0: `scripts/build.sh` виконує core/DSP, міграції SQLite та debug-збірку з lint; після збірки запускає `python3 scripts/test-providers.py`. HTTP-тести працюють із локальним Subsonic fixture і production-кодом Providers/Http: star/unstar, order/repeats, source-scoped IDs, create/rename/add/remove/delete, stale index і відхилені HTTP/API-відповіді. Desktop org.json 20240303 завантажується в `.tooling` із перевіркою закріпленого SHA-256. Android SourceStore/Base64 замінені лише в цьому JVM-тесті; він не перевіряє Android Keystore або UI.

Перевірки SQLite виконують SQL зі schema/migration v1/v2 → v3, збереження локальних даних, повтори server entries, source-scoped playlist IDs, revision ACK і захист pending likes. Міграцію v2 → v3 також перевірено оновленням установленого APK на Android 14.

На реальному Navidrome перевірено вхідний імпорт улюбленого та чотирьох плейлистів; створення, перейменування, додавання/видалення треку та видалення окремого тестового серверного плейлиста. Невідправлена зміна улюбленого пережила вимкнений Wi-Fi й перезапуск процесу, була підтверджена після повернення мережі; початковий стан треку відновлено. Існуючі серверні плейлисти не редагувалися.

Під час перевірки відтворення Bluetooth A2DP був підключений; зафіксовано PLAYING та роботу PCM DSP 44.1 кГц, stereo. Серії швидких next виконані з логуванням readiness, audio underrun і crash. Фінальний контрольний прогін підтвердив 14 next-переходів із PLAYING: 542 кадри, 14 проблемних (2,58%), без audio underrun або playback error. Дані gfxinfo залежать від прогону, кешу та реального відтворення; попередній кращий результат не слід вважати стабільним benchmark. Специфічне падіння Bluetooth не відтворено; фізичне прослуховування звуку й гарантовану відсутність усіх підвисань ці перевірки не підтверджують. Діагностика: `adb logcat -s SELFoniumPlayback:I ExoPlayerImplInternal:E AndroidRuntime:E`.

Публічний APK 0.5.1: `assembleRelease` і `lintRelease` пройшли (0 помилок, 20 попереджень); перевірено підпис, вирівнювання ZIP, versionCode 6 і `debuggable=false`. Сертифікат підпису збігається з перевіреною debug-збіркою. Окрема runtime-перевірка release-варіанта на телефоні ще не виконана.

0.5.1: core checks, Java/XML/storage checks, debug APK та lint пройшли. APK встановлено поверх 0.5.0; Android підтвердив versionCode 6. На телефоні перевірено відкриття форми джерела: поля займають повну ширину, кнопка збереження залишається над відкритою клавіатурою. Ще перевірити форму на портретному/альбомному екрані, з відкритою клавіатурою та великим шрифтом; показ пароля, помилки полів, додавання/редагування джерела та іконку в круглій і квадратній масках launcher. API-діагностика Jellyfin підтвердила HTTP/HTTPS login і читання порожньої Audio-бібліотеки; це не перевірка Android UI, відтворення або пагінації непорожньої бібліотеки.

`bash scripts/test-core.sh` перевіряє URL/encoding, tokens, origin boundaries, метадані, EQ presets і виміряний DSP (синусоїди, стерео, Preamp, headroom, bypass/reset), синтаксис Java, XML/manifest та SQLite schema/migrations. Синтаксичний parser не перевіряє Android types.

`bash scripts/build.sh` локально виконує core checks, реальну debug-збірку й Android Lint. Для окремої компіляції APK instrumentation-тестів виконай `bash gradlew --no-daemon :app:assembleDebugAndroidTest`; це не запускає тести на Android.

Для під’єднаного власного пристрою з USB debugging:

```sh
bash gradlew --no-daemon :app:connectedDebugAndroidTest
```

`TrackRowTouchTest` перевіряє натискання рядка/обкладинки, окремий overflow, повторне binding, довге натискання й скасування tap прокручуванням. Тести підставляють власні рядки та перевіряють callbacks. Вони не замінюють ручну перевірку Media3 й сервера.

`InstalledCaTrustTest` перевіряє, що справжній HTTP-клієнт у процесі застосунку довіряє системним і встановленим користувачем CA з `AndroidCAStore`. Результат містить кількість перевірених CA без самих сертифікатів. Для перевірки приватного CA на тестовому пристрої має бути встановлений принаймні один користувацький CA; без нього тест перевіряє лише системні CA. Це не замінює перевірку HTTPS-з’єднання з конкретним сервером.

## Ручні сценарії

- Установлення та оновлення поверх попередньої версії; збереження джерел, favorites, плейлистів, черги й офлайн-файлів.
- Tap конкретного треку з композицій, пошуку, альбому, виконавця, favorites та плейлиста. Меню не запускає аудіо. Прокручування не спричиняє випадковий playback.
- Play/pause, seek, previous/next, shuffle/repeat, видалення останнього елемента черги, перезапуск процесу.
- Фонова робота, lockscreen, notification, audio focus, від’єднання гарнітури, Bluetooth.
- Обкладинки після швидкого scroll, кеш без мережі, відсутні або пошкоджені images.
- Оновлення бібліотеки, помилка авторизації, мережевий timeout, сервер за URL subpath, Unicode metadata.
- HTTPS із публічним CA та з установленим користувачем приватним CA: синхронізація, обкладинки, streaming і завантаження. Невідомий CA, прострочений сертифікат і неправильне ім’я сервера мають відхилятися. Звичайний HTTP для локальних джерел має працювати.
- Завантаження, скасування, брак місця, видалення копії; `.part` не має ставати playable.
- EQ on/off, десять незалежних смуг, Preamp, вбудовані/власні пресети, точне введення, headroom, збереження параметрів і робота PCM-процесора.
- Вигляд: палітра, власний HEX, попередній перегляд, AMOLED, збереження після перезапуску й незмінність відтворення.
- Бібліотека: усі три вкладки вміщаються без горизонтального скролу; розділи доступні через «Ще»; обкладинки відступають від країв підсвічування.
- Бібліотека: перемикання категорій, 2/3 колонки, довгі назви, повернення з альбому/виконавця; шапка прокручується зі списком.
- Пошук: порожній запит, очищення, введення Unicode, підтвердження з клавіатури, останні запити, відсутність результатів.
- Черга: видалення, переміщення вище/нижче, активна композиція, порожній стан; зміна порядку не має перезапускати поточний трек.
- Плеєр: обкладинка та фон після зміни треку; EQ відображає активний/власний пресет і скидає підсилення явною кнопкою.
- Portrait/landscape, великий шрифт, TalkBack, системна навігація, rotation під час sync.

Окремо перевіряються мінімальний API26 і сучасний Android. У звіті про помилку вкажи версію застосунку, Android/модель, тип сервера, кроки, очікуваний та фактичний результат. Прибери tokens, паролі й особисті адреси з логів і скриншотів.

## Перевірка 0.4.0 на Android 14

Локальна debug-збірка та Lint пройдені (0 помилок; залишаються попередження локалізації та сумісності). Через ADB перевірені оновлення поверх попередньої версії, категорії бібліотеки, 2/3 колонки, переходи альбом → виконавець → назад, вибір конкретного треку та окреме меню дій, пошук/очищення/історія, переміщення й видалення у черзі включно з останньою композицією. Перевірені скидання EQ, розпізнавання власних налаштувань і відновлення пресета. Перевірені великий шрифт 1.3, горизонтальні бібліотека/пошук/плеєр, збереження медіатеки після оновлення. Системна MediaSession підтвердила відтворення та буферизацію серверного треку.

Це перевірки взаємодій на одному телефоні. Instrumentation-набір у цій ітерації не запускався; API26, TalkBack і всі інші типи серверів не перевірялися. Особисті скриншоти й журнали зберігаються локально поза репозиторієм.

## Перевірка 0.5.0 на Android 14

Пройдені локальна збірка, Lint (0 помилок), 32 core та 69 DSP-перевірок. Через ADB перевірені бібліотека без горизонтального скролу, меню «Ще», внутрішній відступ обкладинки в активному підсвічуванні, великий шрифт 1.3 та горизонтальний EQ. На серверній композиції підтверджено PCM-обробку 44.1 кГц / stereo й зростання лічильника оброблених кадрів. Перевірені вбудований пресет, точне введення смуги/Preamp, збереження, відновлення та видалення власних пресетів. Перевірені палітра, власний HEX, AMOLED і збереження акценту після перезапуску процесу; тестові налаштування повернуті.

Instrumentation-набір, API26, Bluetooth/USB-аудіовиходи та оцінка звуку на слух у цій ітерації не виконувалися. Матеріали пристрою залишаються поза Git.

## Subsonic focus / 0.6.1

`bash scripts/build.sh`: 32 core, 69 measured DSP, Java syntax, XML/manifest, SQLite migrations, debug assembly, Android Lint and 119 HTTP/vault fixture checks passed. Fixtures exercise baseline Subsonic without discovery, advertised OpenSubsonic extension versions, failed optional discovery, and preservation of unknown source records alongside active Subsonic sources. These vault tests cover production JSON serialization, not Android Keystore encryption.

Device review: portrait player distributes extra height between control groups; queue artwork is inset inside the rounded current-row background. Landscape and font scale 1.3 keep controls accessible; a temporary shorter display verifies scrolling to the bottom controls. Display size, rotation and font settings are restored after testing. Screenshots and device backups remain local. No API 26 emulator or instrumentation runtime run is claimed. Historical server-adapter checks above describe earlier versions; current remote support is exclusively Subsonic/OpenSubsonic, tested primarily with Navidrome.

On the connected phone, manual Navidrome synchronization completed with 1093 source tracks. The full stored library retained 1383 tracks, 21 favorites and 4 playlists with an empty favorite outbox and no foreign-key violations before/after the update. Existing encrypted source preferences were unchanged by the upgrade.

## Subsonic reliability / 0.6.2

`bash scripts/build.sh` passed: 39 core checks (including queue remapping), 69 measured DSP checks, Java syntax, XML/manifest, SQLite migrations and protected pruning, real debug assembly and Android Lint (0 errors, 20 warnings). Protocol fixtures passed 119 HTTP/vault checks and 61 catalog checks. The catalog fixture loads 3000 albums across seven pages, checks a maximum of three active album requests and stable result order, and rejects missing/wrong-type containers, failed albums and repeated pages. Optional discovery checks distinguish API, HTTP, malformed JSON and broken-connection failures without leaking fake credentials.

A controlled 12-album fixture with simulated latency measured 758 ms sequentially versus 222 ms with bounded concurrency in the last run. The 3000-album localhost fixture took 649 ms; these are fixture measurements, with no wall-clock threshold asserted and no prediction for remote-server performance.

On the connected Android 14 phone, Navidrome synchronized 483 albums / 1093 source tracks in 4642 ms in one run. A downloaded track played with Wi-Fi off; an offline favorite and queue/index/position survived force-stop/relaunch without autoplay. Reconnection acknowledged the favorite, and the original favorite state was restored. An offline server-playlist creation failed explicitly and left no phantom local playlist. No server test playlist was created.

A stream seek into an uncached region with Wi-Fi off produced an IO/DNS failure. After Wi-Fi became the validated default network, a single Play tap prepared and resumed the stream; playback was paused afterward. An earlier test used a fixed five-second delay and retried before connectivity was ready; that run is not counted as a successful reconnect check. These checks do not cover every Bluetooth route, server implementation or interruption timing.

The temporary downloaded copy was removed. Stored totals remained 1383 tracks, 21 favorites, four playlists, zero offline files and an empty favorite outbox; foreign-key checks were clean. Source preferences remained encrypted and byte-identical before/after the review. Device captures, private backups and event logs are outside the repository. No Android instrumentation runtime or API 26 emulator run is claimed for this change. Release packaging checks are recorded below.

## Release / 0.6.2

Release assembly and Android Lint passed (0 errors, 20 warnings). `SELFonium-0.6.2.apk` has application ID `app.selfonium`, versionName `0.6.2`, versionCode `9`, minSdk 26 and debuggable=false. APK signature verification (v2/v3), ZIP alignment and matching signer certificate against the previous 0.5.1 release passed. SHA-256: `964a3c4c9accf54ea5a479b0ff858676b27a48ff33847581f34e2d35f6f9f068`. The release variant was not installed on the phone; the device checks above used the debug build of the same application code.
