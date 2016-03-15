# Change Log

We consider [SemVer](http://semver.org):

Given a version number MAJOR.MINOR.PATCH, increment the:

1. MAJOR version when you make incompatible API changes,
2. MINOR version when you add functionality in a backwards-compatible
   manner, and
3. PATCH version when you make backwards-compatible bug fixes.

We would like to emphasize §4 SemVer:
Major version zero (0.y.z) is for initial development.
Anything may change at any time. The public API should not be considered stable.

And [Keep a CHANGELOG](http://keepachangelog.com):

- Each version should:
  - List its release date in the above format.
  - Group changes to describe their impact on the project, as follows:
    - `Added` for new features. (+MINOR)
    - `Changed` for changes in existing functionality. (+MINOR or +MAJOR)
    - `Deprecated` for once-stable features removed in upcoming releases. (+MINOR)
    - `Removed` for deprecated features removed in this release. (+MAJOR)
    - `Fixed` for any bug fixes. (+PATCH)
    - `Security` to invite users to upgrade in case of vulnerabilities. (+PATCH)

## 0.3.0 - n/a

## 0.2.0 - 2015-12-17
### Fixed
- Using *no* hard coded `authority` anymore.

### Added
- Introducing a `annotations_index` for searching document elements
  with annotations.
- Query `annotaitons_index` with a simple XML interface.

### Changed (minior)
- Message reading from broker via (faster) raw STOMP protocol.
- Message reading from broker uses now Callbacks, which should be a lot faster.
- Accumulo closes and reopens the BatchWriters all 1000 corpora.

## 0.1.0 - 2015-10-28
### Added
- First non-SNAPSHOT release
- Has Accumulo backend
- Communication via message broker
- Can handle simple FoundCorpus and QueryDocelem events
