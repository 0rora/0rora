# 0rora

_Stellar Payment Manager_

[![Travis](https://travis-ci.org/0rora/0rora.svg?branch=master)](https://travis-ci.org/0rora/0rora)
[![Join chat at https://gitter.im/0rora/community](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/0rora/community?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

A self-hosted solution for easily making, scheduling, editing and responding to Stellar payments.

The application currently supports only batch & scheduled payments via CSV upload. Full details are available in the 
[changelog](CHANGELOG.md).


## Installation

1. Unzip the distribution to your preferred path.
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
- `play.http.secret.key` the [application secret](https://www.playframework.com/documentation/2.7.x/ApplicationSecret)


## Development

Please send pull requests and raise issues.

## License

[MIT License](LICENSE.txt)

## Changes

[Changelog](CHANGELOG.md)

## Credits

[0rora](https://0rora.com/) is created and maintained by [Jem Mawson](https://keybase.io/jem).

If you are using 0rora, or plan to, please get in touch. 
