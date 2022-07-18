package crd

class KMTSpec {
  private var namespace: String = null
  private var configName: String = null
  private var configKey: String = null

  def getNamespace: String = namespace

  def setNamespace(namespace: String): Unit = {
    this.namespace = namespace
  }

  def getConfigName: String = configName

  def setConfigName(configName: String): Unit = {
    this.configName = configName
  }

  def getConfigKey: String = configKey

  def setConfigKey(configKey: String): Unit = {
    this.configKey = configKey
  }
}
