package models
import stellar.sdk.Network

class FakeAppConfig extends AppConfig {
  val stubNetwork: StubNetwork = StubNetwork()
  override val network: Network = stubNetwork
}
