# AGENTS.md

Repository guidance for coding agents working in `DiscordModBot`.

## Scope

- Kotlin 2.4.0, Spring Boot 4.1.0, Gradle 9.6.1, **Java 25** (toolchain and `jvmTarget` both set to 25; CI uses
  Liberica).
- Use the Gradle wrapper: `./gradlew` on Unix, `gradlew.bat` on Windows.
- No `.cursor/rules/`, `.cursorrules`, `.github/copilot-instructions.md`, or `opencode.json` exist.

## Build & Verification

- Full build: `./gradlew build`
- Full verification: `./gradlew check`
- Runnable jar: `./gradlew bootJar` → `build/libs/DiscordModBot.jar`
- OCI image: `./gradlew bootBuildImage --imageName=discordmodbot:latest`
- No dedicated lint task; compiler warnings, `test`, `check`, and `build` are the quality gates.

## Running Tests

- All tests: `./gradlew test`
- One class: `./gradlew test --tests "be.duncanc.discordmodbot.moderation.ScheduledUnmuteServiceTest"`
- One standard method:
  `./gradlew test --tests "be.duncanc.discordmodbot.ApplicationModulesTest.verifiesApplicationModules"`
- One backtick method: quote the full selector, e.g.
  `./gradlew test --tests "be.duncanc.discordmodbot.moderation.ScheduledUnmuteServiceTest.planning an unmute should fail when using time in the past"`
- If selector matching is uncertain, run the whole class.

## Test Environment

- Tests use the `testing` Spring profile and H2 in MySQL compatibility mode (
  `src/test/resources/application.properties`).
- Fast iteration: `./gradlew test`
- MariaDB path: start services with `docker compose up -d` (MariaDB 12.3.2 on 3306, Redis 8.8.0 on 6379), then:
  ```bash
  SPRING_DATASOURCE_URL=jdbc:mariadb://127.0.0.1:3306/discordmodbot \
  SPRING_DATASOURCE_USERNAME=spring \
  SPRING_DATASOURCE_PASSWORD=test \
  SPRING_DATASOURCE_DRIVERCLASSNAME=org.mariadb.jdbc.Driver \
  ./gradlew check
  ```
- Prefer H2 unless changing DB-specific behavior.
- Some tests (e.g. `GuildWarnPointRepositoryTest`) are `@Disabled` due to known Hibernate issues; leave them unless
  fixing the root cause.

## CI

- Build workflow: `./gradlew build` on push/PR to `main`.
- MariaDB workflow: `./gradlew check` against MariaDB 12.3.2 on push/PR to `main`.
- Release Please runs on pushes to `main`.
- `CODEOWNERS`: `@DuncanCasteleyn` owns all files.

## Architecture

- Package root: `be.duncanc.discordmodbot`.
- Spring Modulith bounded contexts. Shared modules: `bootstrap`, `discord`.
- Modules verified by `ApplicationModulesTest`: `bootstrap`, `discord`, `logging`, `member.gate`, `moderation`,
  `narou.novel.api`, `reporting`, `roles`, `server.config`, `utility`, `voting`.
- Each module has a `ModuleMetadata.kt` with `@PackageInfo` and `@ApplicationModule`.
- Modulith detection strategy is `explicitly-annotated` (`spring.modulith.detection-strategy=explicitly-annotated`).
- Keep persistence types in the module-local `persistence` package.
- Central scheduling/config bootstrap: `bootstrap/AppConfig.kt`.

## Persistence & Migrations

- JPA + Spring Data, plus Redis via `@RedisHash`.
- Flyway migrations live in vendor-specific folders under `src/main/resources/db/migration` (`mariadb` and
  `postgresql`). Hibernate `ddl-auto` is `none` in production.
- Common pattern: class-level `@Transactional(readOnly = true)`; method-level `@Transactional` for writes.

## Code Style

- Follow `.editorconfig`: UTF-8, CRLF line endings, 4-space indents, final newline, 120-column limit.
- Kotlin official style (`kotlin.code.style=official`); no trailing commas on normal call sites/collection literals
  unless the surrounding file already uses them.
- Imports layout: regular imports, then `java.*`, `javax.*`, `kotlin.*`, aliases last. Avoid wildcard imports.
- `-Xjsr305=strict` is enabled; respect nullability annotations.
- Prefer constructor injection; do not introduce field injection.

## Discord & Async

- Heavy JDA usage. Match existing `queue { ... }` callback style.
- Reuse `GuildLogger` for moderation/audit logging.
- Preserve user-visible wording unless intentionally changing behavior.

## Testing Conventions

- Tests use `@SpringBootTest(classes = [...])` with `@MockitoBean`/`@MockitoSpyBean` and
  `@TestConstructor(AutowireMode.ALL)`, pure Mockito with `@ExtendWith(MockitoExtension::class)`, or `@DataJpaTest`.
- Mockito agent is attached automatically by Gradle (`-javaagent`).
- Verify exact exception messages when behavior depends on them.
- If touching persistence, scheduling, or transactions, run the MariaDB `./gradlew check` path rather than only
  `./gradlew test`.

### Mockito callback stubbing

When stubbing JDA `RestAction.queue(...)` or similar callback-based methods, avoid explicit unchecked casts from
`InvocationOnMock.arguments`. Use mockito-kotlin's typed component accessors instead:

```kotlin
// preferred for callbacks passed as the first argument
doAnswer { invocation ->
    invocation.component1<Consumer<InteractionHook>>().accept(hook)
    null
}.whenever(replyAction).queue(any(), any())

// for single-argument callbacks, destructuring also works
doAnswer { (consumer: Consumer<InteractionHook>) ->
    consumer.accept(hook)
    null
}.whenever(replyAction).queue(any())
```

## Workflow

- Prefer the smallest change that fits the current architecture.
- Inspect nearby code and mirror similar classes when adding commands or features.
- Do not introduce new tooling, formatters, or architectural layers unless asked.
