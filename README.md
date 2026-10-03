# Virpe Mart Billing

Offline billing software for the Virpe Mart grocery store. It runs on a single Windows laptop and
prints bills on the store printer. It has two main areas:

- **Products**: the list of items the store sells, with English and Marathi names, rates and MRP.
- **Billing**: bills for walk-in and account (credit) customers, with purchase history and printing.

Status: **Version 1.1.0.** Products, customer khatas, billing, printing, bill history, reports, backups,
restore and the activity log work. The Windows package runs on a laptop with nothing installed.
Still to do at the shop: tune printing for the real printer model once it is bought.

## Guides for the shop

- [Installation](docs/INSTALLATION.md): install on a Windows laptop that has nothing technical installed.
- [User guide](docs/USER_GUIDE.md): everyday use at the counter.
- [Troubleshooting](docs/TROUBLESHOOTING.md): error messages, fixes, and facts for whoever helps
  (you can give this file to an AI assistant together with the two above).
- [Product import guide](docs/product-import-guide.md): loading the product list from Excel.

These guides are also copied into the `Guides` folder of every package.

## Documentation for developers

- [Requirements](docs/requirements.md): confirmed business rules.
- [Architecture](docs/architecture.md): how the software is built.
- [Changelog](CHANGELOG.md): what changed in each phase.
- [CLAUDE.md](CLAUDE.md): working rules and conventions (also used by Claude Code).

## Developer setup

You need these on the development laptop:

| Tool | Notes |
|---|---|
| JDK 25 | Eclipse Temurin 25. Set `JAVA_HOME` to its folder and add its `bin` to `PATH`. |
| Git | Any recent version. |
| VS Code | Install the recommended "Extension Pack for Java" when VS Code offers it. |

Maven does not need to be installed. The Maven Wrapper (`mvnw.cmd`) downloads the right version automatically.

## Everyday commands

Run these from the project folder in a terminal.

```powershell
# Run all automated tests
.\mvnw.cmd test

# Start the app for development
.\mvnw.cmd javafx:run

# Build the packaged app and the zip for the shop laptop (runs all tests first)
powershell -ExecutionPolicy Bypass -File scripts\package.ps1
```

The script writes `target\dist\VirpeMart-<version>-Windows.zip` (about 70 MB). It contains `VirpeMart.exe`,
its own Java runtime and the guides, so the shop laptop needs nothing else installed. Install it with
[docs/INSTALLATION.md](docs/INSTALLATION.md).

## Making a release

1. Set the new version in `pom.xml` (`<version>`), add it to `CHANGELOG.md`, run the tests and commit.
2. Run `scripts\package.ps1`.
3. Tag the commit (`git tag v1.0.1`, `git push origin v1.0.1`).
4. On GitHub: **Releases → Draft a new release**, choose the tag, attach the zip from `target\dist\`, publish.
   Never commit the zip itself.

## Project layout

```
pom.xml                  Build file: dependencies and build steps
mvnw.cmd, .mvn\          Maven Wrapper
scripts\package.ps1      Builds the packaged Windows app
docs\                    Requirements and architecture
src\main\java\           Application code (package com.virpemart.billing)
src\main\resources\      Screen layouts (fxml), styles (css), database scripts
src\test\java\           Automated tests
```

## Data safety

Shop data is never stored in this repository. On the store laptop it lives in
`%LOCALAPPDATA%\VirpeMart\`. Development runs use a separate, git-ignored `dev-data\` folder.
