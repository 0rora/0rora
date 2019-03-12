# Changelog

All notable changes to this project will be documented in this file.

## Next version

### Added
- Support for Federated addresses in source and destination. [#6](https://github.com/0rora/0rora/issues/6)
- Updated Stellar SDK to accommodate for changes in Horizon v0.17.0


## v0.1.1 - 2017-02-16

### Added
- Pagination for payments list pages [#15](https://github.com/0rora/0rora/issues/15)

### Fixed
- DB type of payments.units is BIGINT, not NUMERIC. Decimal places are not used.


## 0.1.0 - 2017-02-10

### Added
- List of historical payments under `Payments > History`. (Unpaginated)
- List of scheduled payments under `Payments > Schedule`. (Also unpaginated)
- Ability to upload CSV files of payments, with format `sender,recipient,asset,<not used>,units,scheduled_date`.
- Ability to configure array of sender accounts in `application.conf`
- Ability to configure Horizon environment, also in `application.conf`.
- Mock login/out functionality with hard-coded credentials. (`demo/demo`).

