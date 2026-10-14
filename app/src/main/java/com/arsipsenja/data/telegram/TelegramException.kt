package com.arsipsenja.data.telegram

import org.drinkless.tdlib.TdApi

/**
 * Galat yang berasal dari TDLib atau dari lapisan pembungkusnya.
 */
open class TelegramException(
    val pesan: String,
    val kode: Int = 0,
    cause: Throwable? = null,
) : Exception(pesan, cause) {

    constructor(error: TdApi.Error) : this(
        pesan = error.message ?: "Galat TDLib",
        kode = error.code,
    )
}
