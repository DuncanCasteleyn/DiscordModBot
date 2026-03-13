# AGENTS.md

Repository guidance for coding agents working in `DiscordModBot`.

## Scope

- This repository is a Kotlin 2.3 / Spring Boot 4 Gradle project.
- Use the Gradle wrapper for all build and test commands.
- Commands below use `./gradlew`; on Windows use `gradlew.bat` if needed.
- Java 21 is required and configured via the Gradle toolchain.

## Project Layout

- Main code: `src/main/kotlin`
- Tests: `src/test/kotlin`
- Main resources/config: `src/main/resources`
- Test resources/config: `src/test/resources`
- Root build files: `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`

## Module Boundaries

- Preserve the bounded-context package layout under `be.duncanc.discordmodbot`.
- Current modules are verified by `src/test/kotlin/be/duncanc/discordmodbot/ApplicationModulesTest.kt`.
- Existing modules: `bootstrap`, `discord`, `logging`, `member.gate`, `moderation`, `reporting`, `roles`,
  `server.config`, `utility`, `voting`.
- `bootstrap` and `discord` are shared modules.
- Keep persistence types in a module-local `persistence` package.
- Update `ModuleMetadata.kt` when module-level behavior changes.

## Build And Verification Commands

- Full CI-style build: `./gradlew build`
- Full verification task: `./gradlew check`
- Run all tests: `./gradlew test`
- Create the runnable jar: `./gradlew bootJar`
- Build an OCI image: `./gradlew bootBuildImage`
- Build an OCI image with a custom name: `./gradlew bootBuildImage --imageName=discordmodbot:latest`

## Single-Test Commands

- Run one test class: `./gradlew test --tests "be.duncanc.discordmodbot.moderation.ScheduledUnmuteServiceTest"`
- Run one standard test method:
  `./gradlew test --tests "be.duncanc.discordmodbot.ApplicationModulesTest.verifiesApplicationModules"`
- Run one Kotlin backtick test method:
  `./gradlew test --tests "be.duncanc.discordmodbot.moderation.ScheduledUnmuteServiceTest.planning an unmute should fail when using time in the past"`
- Quote the full test selector exactly; if matching feels uncertain, run the whole class instead.

## Database And Test Environment

- Default local tests use in-memory H2 from `src/test/resources/application.properties`.
- CI also runs `check` against MariaDB.
- Reproduce the MariaDB CI path locally with:

```bash
SPRING_DATASOURCE_URL=jdbc:mariadb://127.0.0.1:3306/discordmodbot \
SPRING_DATASOURCE_USERNAME=spring \
SPRING_DATASOURCE_PASSWORD=test \
SPRING_DATASOURCE_DRIVERCLASSNAME=org.mariadb.jdbc.Driver \
./gradlew check
```

- Prefer H2 for fast local iteration unless you are changing DB-specific behavior.

## Linting

- There is no dedicated linter task configured here.
- No `ktlint`, `detekt`, `spotless`, `checkstyle`, `pmd`, or `spotbugs` config is present.
- Treat `./gradlew test`, `./gradlew check`, and `./gradlew build` as the practical verification commands.
- Compiler warnings still matter; Java deprecation warnings are enabled.

## Kotlin Formatting

- Follow Kotlin official style; `gradle.properties` sets `kotlin.code.style=official`.
- Use 4-space indentation.
- Keep one top-level declaration style consistent with nearby files.
- Use trailing commas in multiline constructor calls, collection literals, and argument lists.
- Put wrapped constructor parameters one per line.
- Stack annotations one per line above the declaration they apply to.
- Prefer short blank-line-separated blocks; do not add extra vertical whitespace.
- Preserve Apache license headers in files that already use them; match nearby production files when adding new ones.

## Imports

- Keep `package` first, then a blank line, then imports.
- Follow the repo's common grouping style: project imports first, third-party imports next, Java/Kotlin stdlib imports
  last when practical.
- Avoid wildcard imports.
- Remove unused imports promptly.
- Alias imports are acceptable when needed to disambiguate, especially for persistence entities.

## Types And Nullability

- Respect strict nullability interop; the build enables `-Xjsr305=strict`.
- Prefer explicit return types on public functions and service/repository boundaries.
- Use type inference for obvious local values.
- Prefer `val` over `var` unless mutation is required.
- Prefer nullable types plus safe calls over `!!`.
- Use `!!` only when a local invariant is already established and the alternative would be noisier.
- At Java/Spring boundaries, the repo commonly uses `Optional.orElse(null)` and then Kotlin null-safe handling.

## Spring And Dependency Injection

- Prefer constructor injection.
- Use Spring stereotypes on classes: `@Service`, `@Component`, `@Repository`.
- Do not introduce field injection.
- Older classes sometimes use `@Autowired` constructors; prefer plain primary-constructor injection in new code.
- Use `@Lazy` only when needed for initialization order or circular dependency concerns.
- Keep beans focused on one bounded context.

## Transactions

- Follow the common service pattern: class-level `@Transactional(readOnly = true)` for read-mostly services,
  method-level `@Transactional` for writes.
- Keep transaction boundaries in services, not controllers or command handlers, unless there is a strong reason.
- Repository interfaces should remain thin; business rules belong in services.

## Persistence Conventions

- Persistence classes usually live in `persistence` packages.
- Spring Data repositories are generally thin interfaces with derived query methods.
- JPA entities are often constructor-driven Kotlin data classes.
- Be careful when changing entity equality or IDs; some entities override `equals`/`hashCode` for Hibernate identity
  semantics.
- Prefer small, explicit persistence changes and verify them with tests.

## Naming

- Package names are lowercase.
- Type names use PascalCase.
- Functions and properties use camelCase.
- Constants use `const val` with uppercase snake case when they are true constants.
- Private file-level constants are common for repeated messages and component IDs.
- Service classes usually end in `Service`; repositories usually end in `Repository`.
- Command-like classes may be verb-based or feature-based; match the local package convention rather than forcing a
  suffix.
- Tests often use descriptive backtick names; keep new tests readable and behavior-focused.

## Error Handling

- Fail fast with clear exceptions.
- Prefer `IllegalArgumentException` for invalid inputs and `IllegalStateException` for impossible or misconfigured
  states.
- Exception messages are often user-facing, so keep them precise and readable.
- Avoid swallowing exceptions silently.
- Catch exceptions only when you can add context, recover meaningfully, or translate them into user feedback.
- Reuse existing permission and command exceptions where the command framework expects them.

## Logging And Discord Feedback

- Use SLF4J `LoggerFactory` for internal logs.
- The common logger name is `LOG`, often in a `companion object`.
- Log errors with context and include the throwable when useful.
- Use the `GuildLogger` service for Discord moderation or audit logging rather than ad hoc message formatting.
- Do not leak secrets, tokens, or private identifiers into logs.

## JDA And Async Patterns

- Match existing JDA usage around `queue { ... }` and callback-based actions.
- Keep side effects explicit when chaining Discord API calls.
- When changing multi-step command flows, inspect related `Sequence` classes and cleanup behavior.
- Preserve user-visible wording unless the change intentionally updates behavior.

## Testing Guidance

- Add or update tests with code changes whenever practical.
- Favor focused unit or slice tests over broad integration tests unless wiring is the thing under test.
- For Spring tests, follow existing patterns like `@SpringBootTest`, `@TestConstructor`, `@MockitoBean`, and
  `@MockitoSpyBean` when appropriate.
- Verify exact exception messages when the command framework depends on them.
- For module or package changes, consider whether `ApplicationModulesTest` needs an update.

## Agent Workflow

- Before editing, inspect nearby code and follow local style instead of normalizing the whole repo.
- Prefer the smallest change that fits the existing architecture.
- Run targeted tests first, then broader verification if the change is significant.
- If you touch persistence, scheduling, transactions, or module boundaries, prefer `./gradlew check` over only
  `./gradlew test`.
- If you add a new command or sequence, inspect similar classes in the same module and mirror their interaction
  patterns.
- Do not add new tooling, formatting systems, or architectural layers unless explicitly requested.
