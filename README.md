# [0rora - Stellar Payment Manager](https://0rora.com/)
by [Jem Mawson](https://keybase.io/jem)

[![Travis](https://img.shields.io/travis/0rora/0rora.svg)](https://travis-ci.org/0rora/0rora)
[![Coverage](https://img.shields.io/codecov/c/gh/0rora/0rora.svg)](https://codecov.io/gh/0rora/0rora)
[![Chat](https://img.shields.io/gitter/room/0rora/community.svg)](https://gitter.im/0rora/community?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Download](https://img.shields.io/github/downloads/0rora/0rora/v0.1.1/total.svg)](https://github.com/0rora/0rora/releases/tag/v0.1.1)
[![Issues](https://img.shields.io/github/issues/0rora/0rora.svg)](https://github.com/0rora/0rora/issues)

0rora is a web application for easily making and scheduling Stellar payments in bulk.

With 0rora you can:
* schedule future payments;
* distribute your custom tokens _en masse_; and
* view reports of past and future payments.

0rora makes use of payment channels and batching to achieve maximum throughput, so you get the fastest performance possible.

You are free to copy, modify, and distribute 0rora with attribution under the terms of the [MIT license](LICENSE.txt).


## Installation

1. Unzip the latest [distribution](https://github.com/0rora/0rora/releases) to your preferred path.
2. Configure your instance.

### Configuration

Create a `production.conf` override file specific for your site, e.g.

```hocon
include "application"

0rora {
  horizon = "test"
  accounts = [
    "SCTQSBBI7SULSIFMHWIOHPKEMN6JLHZRNUKIUZSN2XYPU4CCCQMGGGCS",
    "SCVD5GCA3RTIFPDN2RBVLXZGUNTUP7NQN4EYORJPWDXPSQ7EEHNDJUKN",
    "SA4OT2TPC2XPGOCXHNJP6N2LQIGM4WZE63IIEATA4N5GM6WY2Z6GLSLQ"
  ]
}

db {
  default {
    driver = org.postgresql.Driver
    url = "jdbc:postgresql://localhost/orora"
    username = "ford"
    password = "pr3fekt"
  }
}

play.http.secret.key = "babelfish77"
```

... and to pass that file as a system property at initialisation:

`bin/0rora -Dconfig.file=/path/to/production.conf`

#### Properties

- `0rora.horizon` determines the Horizon instance to connect with. It may be `test`, `public` or the base URL of any 
    custom Horizon instance. 
- `0rora.accounts` is an array of secret seeds for accounts that will be both payers and payment channel participants.
    (Future builds will migrate this sensitive data elsewhere).
- `db.default` is the JDBC configuration of the application database as per the [ScalikeJDBC documentation](http://scalikejdbc.org/documentation/configuration.html#scalikejdbc-config).
    At the very least, you will require `db.default.driver` and `db.default.url`.
- `play.http.secret.key` is the [application secret](https://www.playframework.com/documentation/2.7.x/ApplicationSecret).


## Documentation

See the [changelog](CHANGELOG.md) for details of features in the next and current releases.

Further detail on how to install and use 0rora is available under the `docs` folder.


## Future Work

0rora has many planned features on its road to being a fully-fledged Stellar payment manager and gateway. These include:

* Receiving payments through event integration points, such as AMQP
* Netting payments
* Holding payments for manual validation
* Account and channel management
* User management and permissions

Users of 0rora are encouraged to help prioritise future work. Contact the project via
[chat](https://gitter.im/0rora/community) or [github](https://github.com/0rora/0rora/issues).


## Contributing

Suggest new features, or raise bug reports in the [issuer tracker](https://github.com/0rora/0rora/issues).

Chat about 0rora in [Gitter](https://gitter.im/0rora/community).

Help is warmly welcomed and pull requests in any area, big or small, are greatly appreciated.

If 0rora has been helpful and you'd like to donate, the address is [![Donate](https://img.shields.io/keybase/xlm/jem.svg)](https://keybase.io/jem)




