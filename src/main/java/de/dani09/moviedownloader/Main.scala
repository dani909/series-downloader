package de.dani09.moviedownloader

import java.net.URL
import java.nio.file.{Path, Paths}

import de.dani09.moviedownloader.config.{CLIConfig, Config}
import org.json.JSONException

// TODO Windows support??
// TODO entry in config file to override movieDataSource
// TODO regex filter for episodeTitle
// TODO fix episodeTitles that will create another folder in the series folder like "Some text / Some different Text" --> Some different Text in folder Some Text
// TODO interactive mode without config file

object Main {
  val movieListFileName = "movie-data.xz"
  val movieDataSource = new URL("http://verteiler1.mediathekview.de/Filmliste-akt.xz")

  def main(args: Array[String]): Unit = {
    val cliConf = CLIConfig.parse(args)
    if (cliConf.interactive) {
      InteractiveMode.start()
      System.exit(0)
    }
    var config: Config = null

    try {
      config = Config.parseConfig(cliConf.configPath)
    } catch {
      case e: JSONException =>
        println(s"Couldn't parse Config at ${"\"" + cliConf.configPath + "\""}: $e")
        System.exit(1)
      case e: Throwable =>
        println(s"Couldn't load Config at ${"\"" + cliConf.configPath + "\""}: $e")
        System.exit(1)
    }

    println(s"Parsed Config from ${"\"" + cliConf.configPath + "\""} successfully")

    println("Launching Movie Downloader")
    downloadMovies(config)
  }

  def downloadMovies(config: Config): Unit = {
    val downloader = new MovieDownloader(config)
    saveMovieData(downloader = downloader)

    var movies: List[Movie] = getMovies(downloader)

    movies = movies.filter(x => config.matchesMovie(x))
    println(s"${movies.length} Movies matched Filters")

    movies = movies.filter(x => !downloader.isMovieAlreadyDownloaded(x))
    println(s"${movies.length} not already downloaded")

    if (movies.nonEmpty) {
      println(s"Will download ${movies.length} Movies:")
      movies.foreach(m => println(s"${m.tvChannel} --> ${m.seriesTitle} --> ${m.episodeTitle}"))
      println()
    } else {
      println("No new Movies to download!")
    }

    movies.foreach(x => {
      println(s"[${movies.indexOf(x) + 1}/${movies.length}] Will download following Movie:")
      x.printInfo()
      downloader.downloadMovie(x)
      println()
    })

    println("Done!")
  }

  def saveMovieData(path: Path = getMovieListTmpPath,
                    downloader: MovieDownloader = new MovieDownloader(null),
                    source: URL = movieDataSource): Unit = {
    downloader.saveMovieData(getMovieListTmpPath, movieDataSource)
  }

  def getMovieListTmpPath: Path = {
    val tmp = System.getProperty("java.io.tmpdir")

    Paths.get(tmp, movieListFileName)
  }

  def getMovies(downloader: MovieDownloader, path: Path = getMovieListTmpPath): List[Movie] = {
    println("Reading Movie Data")
    val movies = downloader.getMovieList(path)
    println("Parsed Movie Data successfully")
    println(s"${movies.length} Movies found in total")

    movies
  }
}
