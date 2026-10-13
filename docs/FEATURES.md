# Можливості та межі

| Джерело | Реалізовано | Поки відсутнє / не перевірено |
|---|---|---|
| Локальні файли | MediaStore, document picker, embedded covers | Рекурсивний folder scanner |
| Subsonic / OpenSubsonic | Token authentication, альбоми/треки, streaming, artwork | Server playlist sync, scrobbling, lyrics; Navidrome перевірений на пристрої |
| Jellyfin / Emby | Login, paged Audio items, static stream | QuickConnect, playback reporting, transcoding; потрібні серверні тести |
| Plex | Server URL + token, music sections/items, original stream | Account discovery/PIN, transcoding; потрібен серверний тест |
| WebDAV | PROPFIND traversal, Basic auth, file streaming | Потрібні серверні тести |
| Kodi | GetSongs / PrepareDownload, локальне відтворення | Remote renderer control |
| Audiobookshelf | Login, libraries/items, окремі tracks | Chapters, bookmarks, progress sync |
| Радіо | Прямі HTTP(S) URL, HLS URL із .m3u8 | Каталог станцій, M3U/PLS resolution |

Пошук, локальні плейлисти, улюблене, історія, офлайн-файли й керування чергою спільні для бібліотеки. Зберігаються черга/позиція; автоматичний autoplay після запуску не виконується.

Еквалайзер використовує Android audio effect. Кількість смуг, частоти та діапазон залежать від пристрою. Немає параметричного DSP, AutoEQ чи заявленого bit-perfect output.

Офлайн-завантаження зберігає цілий файл через тимчасову `.part`-копію. Resume, автоматичні правила кешування та квоти ще не реалізовані.

HTTPS довіряє системним CA та CA, встановленим користувачем у сховище сертифікатів Android. Це дозволяє підключатися до домашніх серверів із приватним CA, наприклад Caddy: спочатку встанови кореневий CA на телефон як сертифікат центру сертифікації, потім використовуй адресу, зазначену в сертифікаті сервера. Перевірка ланцюжка сертифікатів, строку дії та імені сервера залишається ввімкненою. Окремий імпорт CA для одного джерела поки не реалізований. [Налаштування довіри Android](https://developer.android.com/privacy-and-security/security-config).

HTTP підтримується для локальних серверів. Авторизовані redirects на інший origin відхиляються. Credentials джерел шифруються через Android Keystore; резервний експорт цих даних не реалізований.

Android Auto, Cast, UPnP/DLNA, SMB, cloud OAuth, синхронізовані тексти та widgets поки не підтримуються.
