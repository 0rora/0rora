package models
import stellar.sdk.Network

class FakeAppConfig extends AppConfig {
  override val network: Network = StubNetwork()
}
