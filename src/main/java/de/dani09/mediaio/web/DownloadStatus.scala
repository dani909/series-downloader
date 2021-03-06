package de.dani09.mediaio.web

/**
  * Enum for reporting status when downloading on remote server
  */
object DownloadStatus extends Enumeration {
  type DownloadStatus = Value
  val QUEUED, DOWNLOADING, FINISHED = Value
}