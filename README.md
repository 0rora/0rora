# Luxe

## Installation

### Quickstart Docker

`docker run --rm -p9000:9000 -ti --name luxe -eAPPLICATION_SECRET=foorandombar -eLUXE_ACCOUNT_SEED=S...ABC123 luxeapp/luxe`

It is necessary to define the environment variables:

* `APPLICATION_SECRET`: Used to secure cryptographic functions with the application. 
                        It must be the same value for each deployed instance in a production environment.
* `LUXE_ACCOUNT_SEED`: A temporary variable that defines the account from which to transact. It will be removed when
                       account management functionality is implemented.


[![@app_luxe](http://i.imgur.com/tXSoThF.png)](https://twitter.com/app_luxe)