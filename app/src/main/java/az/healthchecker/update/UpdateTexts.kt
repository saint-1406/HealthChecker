package az.healthchecker.update
import az.healthchecker.domain.tr
import java.util.Locale

/** The updater's sentences in the app's language; worded after ATLAS's update section. */
object UpdateTexts {
    fun megabytes(bytes: Long) = String.format(Locale.ROOT, "%.1f", bytes / 1024.0 / 1024.0).let { tr("$it МБ".replace('.', ','), "$it MB") }
    fun current(version: String) = tr("Установлена версия $version", "Version $version installed")
    fun available(version: String) = tr("Доступна версия $version", "Version $version is available")
    fun latest(version: String) = tr("Установлена последняя версия ($version)", "The latest version is installed ($version)")
    fun downloading(percent: Int) = tr("Загружаю обновление… $percent%", "Downloading the update… $percent%")
    fun verified(version: String) = tr("Файл версии $version проверен — открываю установку. Дневник, находки и прогулки останутся.",
        "Version $version checked — opening the installer. Your diary, findings and walks stay.")
    val permission get() = tr("Разрешите HealthChecker устанавливать приложения (Android спросит один раз) и вернитесь — установка продолжится сама.",
        "Allow HealthChecker to install apps (Android asks once) and come back — the update carries on by itself.")

    /** What went wrong, and that nothing was installed or lost. */
    fun failure(f: UpdateFailure): String = when (f.reason) {
        UpdateFailure.Reason.OFFLINE -> tr("Нет подключения к интернету. Проверьте сеть и попробуйте ещё раз.", "No internet connection. Check the network and try again.")
        UpdateFailure.Reason.RATE_LIMITED -> tr("GitHub временно ограничил число проверок. Попробуйте через час.", "GitHub is limiting checks for now. Try again in an hour.")
        UpdateFailure.Reason.NO_RELEASE -> tr("На GitHub не найден выпуск с файлом обновления.", "No release with an update file was found on GitHub.")
        UpdateFailure.Reason.SERVER -> tr("Не удалось проверить обновления: GitHub ответил ошибкой (${f.detail}). Попробуйте позже.", "Could not check for updates: GitHub answered with an error (${f.detail}). Try again later.")
        UpdateFailure.Reason.NO_SPACE -> tr("Не хватает места для обновления: освободите ещё около ${f.detail} МБ и попробуйте снова.", "Not enough space for the update: free about ${f.detail} MB more and try again.")
        UpdateFailure.Reason.INTERRUPTED -> tr("Загрузка прервалась (${f.detail}). Ничего не установлено, данные не тронуты — попробуйте ещё раз.", "The download stopped (${f.detail}). Nothing was installed and your data is untouched — try again.")
        UpdateFailure.Reason.INSTALLER -> tr("Не удалось открыть установку: ${f.detail}", "Could not open the installer: ${f.detail}")
        else -> tr("Скачанный файл не прошёл проверку (${check(f)}) и удалён. Ничего не установлено — попробуйте ещё раз.",
            "The downloaded file failed its check (${check(f)}) and was deleted. Nothing was installed — try again.")
    }

    private fun check(f: UpdateFailure) = when (f.reason) {
        UpdateFailure.Reason.CHECKSUM -> tr("контрольная сумма SHA-256 не совпала с опубликованной", "its SHA-256 does not match the published one")
        UpdateFailure.Reason.CHECKSUM_FILE -> tr("файл SHA-256 в выпуске повреждён", "the release's SHA-256 file is damaged")
        UpdateFailure.Reason.SIZE -> tr("размер не совпал", "wrong size")
        UpdateFailure.Reason.NOT_APK -> tr("это не приложение Android", "not an Android app")
        UpdateFailure.Reason.WRONG_PACKAGE -> tr("это другое приложение", "a different app")
        UpdateFailure.Reason.NOT_NEWER -> tr("версия не новее установленной", "not newer than the installed version")
        UpdateFailure.Reason.WRONG_SIGNATURE -> tr("другой ключ подписи", "a different signing key")
        else -> f.detail
    }
}
