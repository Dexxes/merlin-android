package dev.merlin.android.network

import retrofit2.HttpException
import retrofit2.Response

/**
 * Endpunkte mit `204 No Content` liefern `Response<Unit>`, das bei HTTP-Fehlern nicht von selbst
 * wirft. Damit Aufrufer (Offline-Queue, ViewModels) Fehler wie bei den übrigen Endpunkten als
 * [HttpException] sehen, prüft das hier nach.
 */
fun Response<*>.requireSuccess() {
    if (!isSuccessful) throw HttpException(this)
}
