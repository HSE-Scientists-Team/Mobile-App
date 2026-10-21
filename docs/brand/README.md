# Знак MatchLab

Знак — два белых кольца, наложенных друг на друга, на синем фоне (референс заказчика, 2026-10-21). Цвета — из коллекции
«MatchLab Color»: фон `primary` `#3550DC`, кольца `onPrimary` `#FFFFFF`. Других цветов нет.

| Файл | Что это |
|---|---|
| `matchlab-icon.svg` | основной знак: скруглённый квадрат (радиус 112 из 512) |
| `matchlab-icon-round.svg` | круглый вариант |
| `matchlab-icon-fullbleed.svg` | квадрат без скруглений: для магазинов (они сами накладывают маску); то же — исходник для иконки iOS 1024×1024 |
| `matchlab-playstore-512.png` | иконка для карточки в Google Play (из `matchlab-icon-fullbleed.svg`) |
| `generate-android-icons.mjs` | рисует PNG для старых лаунчеров и для Google Play из этих SVG |

## Геометрия (холст 512)

Два кольца с центрами `(192, 256)` и `(320, 256)`, радиус средней линии 98, толщина 48 (внешний диаметр 244, пара шириной 372 —
72,6 % знака). Пара стоит ровно по центру; у референса она сдвинута вправо примерно на 2 % — это не повторено.

## Иконка Android

- Адаптивная (Android 8+): фон — цвет `ic_launcher_background` (`androidApp/src/main/res/values/colors.xml`), знак —
  `drawable/ic_launcher_foreground.xml`, тематическая иконка (Android 13+) — `drawable/ic_launcher_monochrome.xml`. Холст 108 dp
  показывает 72 dp, пара шириной 52 dp стоит внутри безопасной зоны 66 dp, маска (круг, скруглённый квадрат, «капля») её не
  срезает. Геометрия — холст SVG, умноженный на 72/512.
- Старые лаунчеры (Android 7.x, `minSdk` 24): `mipmap-*dpi/ic_launcher.png` (скруглённый квадрат) и `ic_launcher_round.png` (круг)
  размером 48/72/96/144/192 px.
- Если меняется цвет или форма знака: поправить SVG, пересчитать числа в двух `ic_launcher_*.xml` (масштаб 72/512) и цвет
  `ic_launcher_background`, запустить `node docs/brand/generate-android-icons.mjs`.

## iOS

Папку `iosApp/` агенты не правят. Иконка приложения iOS — 1024×1024 PNG без прозрачности и без скруглений: экспортировать из
`matchlab-icon-fullbleed.svg` в набор `AppIcon` проекта Xcode. Название под иконкой задаёт `PRODUCT_NAME` (сейчас `AcademicNet`) в
`iosApp/Configuration/Config.xcconfig`; чтобы показывать «MatchLab», не переименовывая продукт, задать `CFBundleDisplayName`.
