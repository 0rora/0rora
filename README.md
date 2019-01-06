# Luxe

_Stellar Payments Manager_

A self-hosted solution for easily making, scheduling, editing and responding to Stellar payments.

[https://luxe-app.github.io/luxe/](https://luxe-app.github.io/luxe/)


## Installation

### Quickstart Docker

`docker run --rm -p9000:9000 -ti --name luxe -eAPPLICATION_SECRET=foorandombar -eLUXE_ACCOUNT_SEED=S...ABC123 luxeapp/luxe`

It is necessary to define the environment variables:

* `APPLICATION_SECRET`: Used to secure cryptographic functions with the application. 
                        It must be the same value for each deployed instance in a production environment.
* `LUXE_ACCOUNT_SEED`: A temporary variable that defines the account from which to transact. It will be removed when
                       account management functionality is implemented.


[![Stellar](https://luxe-app.github.io/luxe/images/web-ico.png "Stellar Rocket")](https://luxe-app.github.io/luxe/) [![@app_luxe](https://luxe-app.github.io/luxe/images/twitter-ico.png)](https://twitter.com/app_luxe)  
![Stellar](https://luxe-app.github.io/luxe/images/stellar-ico.png "Stellar Rocket") `GBYTSTC7BBN6MFO55M3IFPIZJ2O5UVNZSF357OVIAVMCDVIHULUXEPAY`
