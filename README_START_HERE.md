# Justina's starter foundation

Includes a `User` model, `Role`, salted PBKDF2 password hashing, authentication with a three-attempt lockout, a repository interface, a quoted-argument command parser and a parser test.

## Open in VS Code
1. Extract this ZIP.
2. VS Code → File → Open Folder → select the extracted folder.
3. Install **Extension Pack for Java** from Extensions (`Ctrl+Shift+X`).
4. Open Terminal (`Ctrl+``) and run `java -version`.
5. Java 17 or newer is required. Run `mvn test` if Maven is installed.

## Integration warning
This is a starter, not the finished team integration. Compare package names, `User`, `Role`, and repository method signatures with the team's code before merging. Do not create duplicate model classes. Lockout state must be persisted by the team's repository to survive restart. ReportService and the complete CLI need the real service/repository APIs from teammates.
