# AGENTS.md

Repository guidance for coding agents working in `DiscordModBot`.

## Scope

- This is a Kotlin 2.3, Spring Boot 4, Gradle 9 project.
- Use the Gradle wrapper for all project commands.
- Prefer `./gradlew` in docs and Unix shells; use `gradlew.bat` on Windows shells.
- Java 21 is required and configured through the Gradle toolchain.
- No `.cursor/rules/`, `.cursorrules`, or `.github/copilot-instructions.md` files were found.

## Repository Layout

- Main source: `src/main/kotlin`
- Tests: `src/test/kotlin`
- Main resources: `src/main/resources`
- Test resources: `src/test/resources`
- Build files: `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`
- Scheduling/config bootstrap: `src/main/kotlin/be/duncanc/discordmodbot/bootstrap/AppConfig.kt`

## Build, Test, And Verification

- Full build: `./gradlew build`
- Full verification: `./gradlew check`
- Run all tests: `./gradlew test`
- Build runnable jar: `./gradlew bootJar`
- Build OCI image: `./gradlew bootBuildImage`
- Build OCI image with name: `./gradlew bootBuildImage --imageName=discordmodbot:latest`
- There is no dedicated lint task configured.
- Treat compiler warnings, `test`, `check`, and `build` as the effective quality gates.

## Running A Single Test

- Run one test class:
  `./gradlew test --tests "be.duncanc.discordmodbot.moderation.ScheduledUnmuteServiceTest"`
- Run one standard test method:
  `./gradlew test --tests "be.duncanc.discordmodbot.ApplicationModulesTest.verifiesApplicationModules"`
- Run one Kotlin backtick test method:
  `./gradlew test --tests "be.duncanc.discordmodbot.moderation.ScheduledUnmuteServiceTest.planning an unmute should fail when using time in the past"`
- Quote the full selector exactly.
- If matching feels uncertain, run the whole test class instead of guessing.

## Test Environment

- Local tests default to H2 using `src/test/resources/application.properties`.
- H2 runs in MySQL compatibility mode.
- Spring test profile is `testing`.
- CI also runs `./gradlew check` against MariaDB.
- Reproduce the MariaDB path locally with:

```bash
SPRING_DATASOURCE_URL=jdbc:mariadb://127.0.0.1:3306/discordmodbot \
SPRING_DATASOURCE_USERNAME=spring \
SPRING_DATASOURCE_PASSWORD=test \
SPRING_DATASOURCE_DRIVERCLASSNAME=org.mariadb.jdbc.Driver \
./gradlew check
```

- Prefer H2 for fast iteration unless you are changing DB-specific behavior.

## Architecture And Modules

- Package root is `be.duncanc.discordmodbot`.
- The codebase is organized by bounded contexts under that root.
- Current modules are verified by `src/test/kotlin/be/duncanc/discordmodbot/ApplicationModulesTest.kt`.
- Existing modules: `bootstrap`, `discord`, `logging`, `member.gate`, `moderation`, `reporting`, `roles`,
  `server.config`, `utility`, `voting`.
- If module boundaries change, update the corresponding `ModuleMetadata.kt` and related tests.
- Keep persistence types inside the module-local `persistence` package.

## Kotlin Style

- Follow Kotlin official style; `gradle.properties` sets `kotlin.code.style=official`.
- Follow `.editorconfig`: UTF-8, CRLF line endings, 4-space indents, final newline, 120-column limit.
- Use 4-space indentation.
- Prefer concise files and short blank-line-separated blocks.
- Do not add extra vertical whitespace just for visual padding.
- Keep Kotlin trailing commas off in normal call sites and collection literals unless a surrounding file already uses
  them.
- Stack annotations one per line above the declaration they annotate.
- Multiline function parameters and call arguments generally wrap one item per line with closing parens on their own
  line.

## Imports

- Keep `package` first, followed by a blank line, then imports.
- Match nearby import ordering rather than reformatting the whole repo.
- `.editorconfig` import layout prefers regular imports first, then `java.*`, `javax.*`, and `kotlin.*`, with aliases
  last.
- Avoid wildcard imports.
- Nested classes should not be imported implicitly.
- Alias imports are acceptable when needed to disambiguate entity and domain types.

## Types, Nullability, And Functions

- The build enables `-Xjsr305=strict`; respect Java nullability carefully.
- Prefer explicit return types on public functions and service boundaries.
- Use local type inference when the type is evident.
- Prefer `val` over `var` unless mutation is required.
- Prefer nullable types plus safe calls over `!!`.
- Use `!!` only when a local invariant is already established.
- Keep expression bodies and Elvis chains wrapped conservatively; prefer readability over dense one-liners.
- Favor small helper methods when they clarify command flow or Discord API interactions.

## Spring Conventions

- Prefer constructor injection.
- Use Spring stereotypes such as `@Service`, `@Component`, and `@Repository`.
- Do not introduce field injection.
- Older code sometimes uses `@Autowired` constructors; do not rewrite them unless needed.
- Keep beans focused on one bounded context.
- Scheduling is enabled centrally in `bootstrap/AppConfig.kt`.

## Transactions And Persistence

- Follow the common pattern of class-level `@Transactional(readOnly = true)` for read-mostly services.
- Use method-level `@Transactional` for writes.
- Keep transaction boundaries in services, not command handlers, unless there is a strong reason.
- Repository interfaces should stay thin; business rules belong in services.
- Spring Data JPA repositories are mostly derived-query interfaces.
- Some persistence data uses Redis via `@RedisHash`; do not assume everything is JPA-backed.
- Be careful when changing entity IDs, equality, or persistence semantics.

## Naming And Structure

- Package names are lowercase.
- Type names use PascalCase.
- Functions and properties use camelCase.
- Constants use uppercase snake case only for true constants.
- Private file-level constants are common for repeated messages and IDs.
- Service classes usually end with `Service`.
- Command-style classes may be noun-based or verb-based; match the local package convention.
- Tests commonly use descriptive backtick names; keep them behavior-focused.

## Error Handling And Logging

- Fail fast with clear exceptions.
- Prefer `IllegalArgumentException` for invalid input.
- Prefer `IllegalStateException` for impossible state or misconfiguration.
- Keep exception messages precise; many are effectively user-facing.
- Catch exceptions only when you can add context, recover, or translate them into user feedback.
- Use SLF4J `LoggerFactory` for internal logging.
- The common logger property name is `LOG` in a `companion object`.
- Do not leak tokens, secrets, or private identifiers into logs.

## Discord And Async Patterns

- This project uses JDA heavily; inspect nearby command modules before changing behavior.
- Match existing `queue { ... }` callback style when interacting with Discord.
- Keep side effects explicit in multi-step async flows.
- Reuse `GuildLogger` for moderation and audit-style logging instead of ad hoc formatting.
- Preserve user-visible wording unless the behavior change intentionally requires new text.

## Testing Guidance

- Add or update tests whenever practical.
- Prefer focused unit or slice tests over broad integration tests unless wiring is the thing under test.
- Match existing Spring test patterns such as `@SpringBootTest`, `@TestConstructor`, `@MockitoBean`, and
  `@MockitoSpyBean`.
- Verify exact exception messages when command behavior depends on them.
- If you touch persistence, scheduling, or transactions, prefer `./gradlew check` over only `./gradlew test`.

## Agent Workflow

- Inspect nearby code before editing and follow the local style.
- Prefer the smallest change that fits the current architecture.
- Do not introduce new tooling, formatting systems, or architectural layers unless asked.
- Run targeted tests first, then broader verification for significant changes.
- If you add a new command or sequence, mirror similar classes in the same module.
