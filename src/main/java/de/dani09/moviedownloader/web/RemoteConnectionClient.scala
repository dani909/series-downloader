package de.dani09.moviedownloader.web

import java.net.{ConnectException, URI}
import java.util.concurrent.CountDownLatch

import de.dani09.http.HttpProgressListener
import de.dani09.moviedownloader.data.Movie
import me.tongfei.progressbar.{ProgressBar, ProgressBarBuilder}
import org.eclipse.jetty.websocket.api.Session
import org.eclipse.jetty.websocket.api.annotations._
import org.eclipse.jetty.websocket.client.WebSocketClient
import org.json.JSONObject

@WebSocket
class RemoteConnectionClient(movie: Movie, listener: HttpProgressListener, remote: String, progressBarBuilder: ProgressBarBuilder) {
  private val uri = new URI(s"ws://$remote/ws")
  private var progressBar: ProgressBar = _
  private val latch = new CountDownLatch(1)
  private val hash = HashUtil.sha256Short(movie.toJson.toString)

  def downloadMovieOnRemote(): Unit = {
    var client: WebSocketClient = null
    var session: Session = null

    try {
      client = new WebSocketClient()
      client.start()
      session = client.connect(this, uri).get

      latch.await()
    } catch {
      case e: ConnectException => println(s"Could't connect to remote: ${e.getMessage}")
      case e: Throwable =>
        println(s"An error occurred: $e")
        e.printStackTrace()
    } finally {
      if (session != null)
        session.close()
      if (client != null)
        client.stop()
      progressBar.close()
    }
  }

  @OnWebSocketConnect
  def onConnect(s: Session): Unit = {
    println(s"Connected to remote at $uri")
    queueDownload(s)
  }

  private def queueDownload(session: Session): Unit = {
    session.getRemote.sendString(
      new JSONObject()
        .put("method", "queueDownload")
        .put("movie", movie.toJson)
        .toString
    )
  }

  @OnWebSocketMessage
  def onMessage(text: String): Unit = {
    val json = new JSONObject(text)
    val status = json.getString("status")

    json.optString("method") match {
      case "queueDownload" => status match {
        case "error" =>
          println(s"An error occurred while queueing download: ${json.getString("message")}")
        case "success" =>
          val place = json.getInt("place")
          val remoteHash = json.getString("hash")

          if (hash != remoteHash) {
            println("Remote and local calculated hash of movie is not the same. Some error occurred.")
            latch.countDown()
            return
          }

          println(s"Download for movie got queued and currently is place $place")
      }

      case _ if status == "update" && json.getString("hash") == hash => // update message from remote
        json.getString("jobStatus") match {
          case "DOWNLOADING" =>
            val currentProgress = json.getLong("progress")
            val maxProgress = json.getLong("maxProgress")

            if (currentProgress == 0)
              progressBar = progressBarBuilder.build()

            progressBar.maxHint(maxProgress)
            progressBar.stepTo(currentProgress)
          case "FINISHED" =>
            progressBar.close()
            println("Successfully downloaded movie on remote")
            latch.countDown()
        }

      case _ =>
    }
  }

  @OnWebSocketError
  def onError(s: Session, e: Throwable): Unit = {
    println(s"An error occurred: $e")
    latch.countDown()
  }

  @OnWebSocketClose
  def onClose(s: Session, code: Int, reason: String): Unit = {
    println(s"Disconnected from remote. disconnect code: $code, reason: $reason")
    latch.countDown()
  }
}
