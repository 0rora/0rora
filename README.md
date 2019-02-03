# 0rora

_Stellar Payments Manager_

A self-hosted solution for easily making, scheduling, editing and responding to Stellar payments.

[https://0rora.github.io/0rora/](https://0rora.github.io/0rora/)


## Installation

### Quickstart Docker

`docker run --rm -p9000:9000 -ti --name 0rora -eAPPLICATION_SECRET=foorandombar -eACCOUNT_SEED=S...ABC123 0rora/0rora`

It is necessary to define the environment variables:

* `APPLICATION_SECRET`: Used to secure cryptographic functions with the application. 
                        It must be the same value for each deployed instance in a production environment.
* `ACCOUNT_SEED`: A temporary variable that defines the account from which to transact. It will be removed when
                  account management functionality is implemented.


[![Stellar](https://0rora.github.io/0rora/images/web-ico.png "Stellar Rocket")](https://0rora.github.io/0rora/) [![@0roraPay](https://0rora.github.io/0rora/images/twitter-ico.png)](https://twitter.com/0roraPay)
![Stellar](https://0rora.github.io/0rora/images/stellar-ico.png "Stellar Rocket") `GBYTSTC7BBN6MFO55M3IFPIZJ2O5UVNZSF357OVIAVMCDVIHULUXEPAY`
