package watcher

trait DefaultWatcher {
  @throws[Exception]
  def getClusterIps(): List[String] = List("127.0.0.1", "127.0.0.2")
}
