# Virpe Mart Billing

Offline billing software for the Virpe Mart grocery store. It runs on a single Windows laptop and
prints bills on the store printer. It has two main areas:

- **Products**: the list of items the store sells, with English and Marathi names, rates and MRP.
- **Billing**: bills for walk-in and account (credit) customers, with purchase history and printing.

Status: **Phase 6 complete.** Products, customer khatas, billing, printing, bill history and reports work.
Users, security and backups come next.
Features arrive phase by phase.

## Documentation

- [Requirements](docs/requirements.md): confirmed business rules.
- [Architecture](docs/architecture.md): how the software is built.
- [Product import guide](docs/product-import-guide.md): loading the product list from Excel.
- [Changelog](CHANGELOG.md): what changed in each phase.

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

# Build the packaged app for the store laptop
powershell -ExecutionPolicy Bypass -File scripts\package.ps1
```

The packaged app appears in `target\dist\VirpeMart\`. Copy that whole folder to the store laptop and
run `VirpeMart.exe`. It carries its own Java, so the store laptop needs nothing else installed.

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
