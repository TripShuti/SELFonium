# Перевірки

## Автоматичні

`bash scripts/test-core.sh` перевіряє URL/encoding, tokens, origin boundaries, метадані, EQ presets, синтаксис Java, XML/manifest та SQLite schema/migrations. Синтаксичний parser не перевіряє Android types.

`bash scripts/build.sh` виконує core checks, реальну debug-збірку й Android Lint. CI також компілює APK instrumentation-тестів, але не запускає Android runtime.

Для під’єднаного власного пристрою з USB debugging:

```sh
bash gradlew --no-daemon :app:connectedDebugAndroidTest
```

`TrackRowTouchTest` перевіряє натискання рядка/обкладинки, окремий overflow, повторне binding, довге натискання й скасування tap прокручуванням. Тести підставляють власні рядки та перевіряють callbacks. Вони не замінюють ручну перевірку Media3 й сервера.

## Ручні сценарії

- Установлення та оновлення поверх попередньої версії; збереження джерел, favorites, плейлистів, черги й офлайн-файлів.
- Tap конкретного треку з композицій, пошуку, альбому, виконавця, favorites та плейлиста. Меню не запускає аудіо. Прокручування не спричиняє випадковий playback.
- Play/pause, seek, previous/next, shuffle/repeat, видалення останнього елемента черги, перезапуск процесу.
- Фонова робота, lockscreen, notification, audio focus, від’єднання гарнітури, Bluetooth.
- Обкладинки після швидкого scroll, кеш без мережі, відсутні або пошкоджені images.
- Оновлення бібліотеки, помилка авторизації, мережевий timeout, сервер за URL subpath, Unicode metadata.
- Завантаження, скасування, брак місця, видалення копії; `.part` не має ставати playable.
- EQ on/off, реальні частоти, presets, збереження параметрів, вихід без доступного ефекту.
- Portrait/landscape, великий шрифт, TalkBack, системна навігація, rotation під час sync.

Окремо перевіряються мінімальний API26 і сучасний Android. У звіті про помилку вкажи версію застосунку, Android/модель, тип сервера, кроки, очікуваний та фактичний результат. Прибери tokens, паролі й особисті адреси з логів і скриншотів.
