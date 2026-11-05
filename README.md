# Depter — электронный насыя дептер

Android-приложение для магазинов и мастеров: кто сколько взял в долг, кто вернул, напоминания в WhatsApp/SMS, сроки оплаты, PDF-выписки и отчёты. Работает без интернета, данные хранятся только на телефоне.

- Языки: кыргызча, русский, oʻzbekcha, қазақша, English
- Kotlin, Jetpack Compose (Material 3), SQLite без ORM, minSdk 26, targetSdk 36
- Монетизация: 14 дней пробного Pro, затем бесплатный план (до 30 контактов) и Pro по коду активации (`tools/license`)

## Сборка

Каждый push в `main` запускает GitHub Actions: unit-тесты, сборка APK и AAB, прогон на эмуляторе со скриншотами всех экранов на трёх языках. Результат публикуется в ветку `builds`.

Локально: откройте папку в Android Studio или выполните `./gradlew assembleRelease`.

## Коды Pro

```
python3 tools/license/depter_license.py --key depter_license_private.pem --id A1B2-C3D4-E5F6-0718 --days 30
```

Приватный ключ не хранится в репозитории.
