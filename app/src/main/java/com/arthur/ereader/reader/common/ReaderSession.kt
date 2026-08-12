package com.arthur.ereader.reader.common

import com.arthur.ereader.domain.model.ReaderLocator

/** Common contract used for settled reading positions across all formats. */
interface ReaderSession {
    val currentLocator: ReaderLocator
    val progress: Float
    fun next()
    fun previous()
    fun goTo(locator: ReaderLocator)
}
