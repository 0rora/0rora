# [0rora - Stellar Payment Manager](https://0rora.com/)
by [Jem Mawson](https://keybase.io/jem)

[![Travis](https://img.shields.io/travis/0rora/0rora.svg)](https://travis-ci.org/0rora/0rora)
[![Coverage](https://img.shields.io/codecov/c/gh/0rora/0rora.svg)](https://codecov.io/gh/0rora/0rora)
[![Chat](https://img.shields.io/gitter/room/0rora/community.svg)](https://gitter.im/0rora/community?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Issues](https://img.shields.io/github/issues/0rora/0rora.svg)](https://github.com/0rora/0rora/issues)

0rora is a web application for easily making and scheduling Stellar payments in bulk.

With 0rora you can:
* schedule future payments;
* distribute your custom tokens _en masse_; and
* view reports of past and future payments.

0rora makes use of payment channels and batching to achieve maximum throughput, so you get the fastest performance possible.

You are free to copy, modify, and distribute 0rora with attribution under the terms of the [MIT license](LICENSE.txt).


## Installation

1. Create an empty Postgres Database.

For example, you may create a Postgres docker container and create your empty database therein.

```bash
docker run --name 0rora_pg -e POSTGRES_PASSWORD=admin_password -d -p "5432:5432" postgres
cat << EOF | docker exec -i 0rora_pg /usr/bin/psql -U postgres
  create database orora;
  create user ford with encrypted password 'pr3fekt';
  grant all privileges on database orora to ford;
EOF
```

2. Run the 0rora [docker image](https://cloud.docker.com/repository/docker/synesso/0rora), passing environment values
    for your database.

```bash
docker run -p 9000:9000 \
    -e HORIZON="test" \
    -e PG_URL="jdbc:postgresql://host:port/db" \
    -e PG_USERNAME="ford" \
    -e PG_PASSWORD="pr3fekt" \
    -e APPLICATION_SECRET=... \
    synesso/0rora:latest
```


- `HORIZON` determines the Horizon instance to connect with. It may be `"test"`, `"public"` or the base URL of any 
    custom Horizon instance. 
- `PG_URL` is the [JDBC URL](https://jdbc.postgresql.org/documentation/80/connect.html) of your database.
- `PG_USERNAME` is username for your database.
- `PG_PASSWORD` is password for your database.
- `APPLICATION_SECRET` is a random, 32-byte minimum value for managing session cookies and CSRF tokens. See 
    PlayFramework documentation on the [application secret](https://www.playframework.com/documentation/2.7.x/ApplicationSecret)
    for more detail. 
    

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

If 0rora has been helpful and you'd like to donate, the address is [![Donate](https://img.shields.io/keybase/xlm/jem.svg)](https://stellar.expert/explorer/public/account/GBRAZP7U3SPHZ2FWOJLHPBO3XABZLKHNF6V5PUIJEEK6JEBKGXWD2IIE)




