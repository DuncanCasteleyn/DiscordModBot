# DiscordModBot

A Discord bot that helps with managing a Discord server/guild.

The current purpose of this project is to provide services to the Fairy tail guild and Re: zero guild.

You are free to modify the code to make it work on your own guild, but right now no support will be offered. The plan is
to make things less hard coded in the future where possible and make everything more configurable.

## Configuration

Spring Boot configuration can be provided through `application.properties`, environment variables, or command-line args.
For environment variables, convert property names to uppercase and replace `.`/`-` with `_`.

### Required bot properties

These are bound from `discord-mod-bot.*` and the application will fail to start if they are missing.

| Property                    | Environment Variable        | Required | Description                                                      |
|-----------------------------|-----------------------------|----------|------------------------------------------------------------------|
| `discord-mod-bot.owner-id`  | `DISCORD_MOD_BOT_OWNER_ID`  | Yes      | Discord user ID for the bot owner (used for owner-only actions). |
| `discord-mod-bot.bot-token` | `DISCORD_MOD_BOT_BOT_TOKEN` | Yes      | Discord bot token used to connect to the Discord gateway.        |

### Default runtime properties

These defaults are defined in `src/main/resources/application.properties`.

| Property                              | Default                                                          | Description                             |
|---------------------------------------|------------------------------------------------------------------|-----------------------------------------|
| `spring.profiles.active`              | `production`                                                     | Active Spring profile.                  |
| `logging.level.root`                  | `info`                                                           | Root logging level.                     |
| `spring.h2.console.enabled`           | `false`                                                          | Enables/disables H2 web console.        |
| `spring.h2.console.path`              | `/h2`                                                            | Path for H2 web console.                |
| `spring.datasource.url`               | `jdbc:h2:file:./discordModBot;MODE=MySQL;DATABASE_TO_LOWER=TRUE` | Default datasource URL (file-based H2). |
| `spring.datasource.username`          | `sa`                                                             | Database username.                      |
| `spring.datasource.password`          | *(empty)*                                                        | Database password.                      |
| `spring.datasource.driver-class-name` | `org.h2.Driver`                                                  | JDBC driver class.                      |
| `spring.jpa.hibernate.ddl-auto`       | `none`                                                           | Hibernate schema management mode.       |

### Example (environment variables)

```bash
export DISCORD_MOD_BOT_OWNER_ID=123456789012345678
export DISCORD_MOD_BOT_BOT_TOKEN=your-token-here
export SPRING_DATASOURCE_URL=jdbc:mariadb://127.0.0.1:3306/discordmodbot
export SPRING_DATASOURCE_USERNAME=spring
export SPRING_DATASOURCE_PASSWORD=test
export SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.mariadb.jdbc.Driver
```

## Running on Linux (systemd, jar)

Spring Boot 4 removed embedded launch scripts. The recommended replacement for autostart is a `systemd` service that
runs the fat jar directly with `java -jar`.

### Build the jar

```bash
./gradlew bootJar
```

The jar will be at `build/libs/DiscordModBot.jar`.

### Install on a server

```bash
sudo useradd --system --no-create-home --shell /usr/sbin/nologin discordbot
sudo mkdir -p /opt/discordmodbot
sudo cp build/libs/DiscordModBot.jar /opt/discordmodbot/DiscordModBot.jar
sudo chown -R discordbot:discordbot /opt/discordmodbot
```

### Create the systemd unit

Create `/etc/systemd/system/discordmodbot.service` with:

```ini
[Unit]
Description=DiscordModBot
After=network.target

[Service]
Type=simple
User=discordbot
Group=discordbot
WorkingDirectory=/opt/discordmodbot
ExecStart=/usr/bin/java -jar /opt/discordmodbot/DiscordModBot.jar
Restart=on-failure
RestartSec=5
SuccessExitStatus=143
Environment="JAVA_OPTS=-Xms256m -Xmx1g"
# If you want env vars in a file:
# EnvironmentFile=/etc/discordmodbot/discordmodbot.env

[Install]
WantedBy=multi-user.target
```

If you want JVM args, either add them directly to `ExecStart` or change it to:
`ExecStart=/usr/bin/java $JAVA_OPTS -jar /opt/discordmodbot/DiscordModBot.jar`

### Enable and start

```bash
sudo systemctl daemon-reload
sudo systemctl enable discordmodbot
sudo systemctl start discordmodbot
sudo systemctl status discordmodbot
```

### Logs

```bash
journalctl -u discordmodbot -f
```

## Running with Docker or Podman (Boot Build Image)

Spring Boot can build an OCI image using buildpacks via the `bootBuildImage` task.

### Build the image

```bash
./gradlew bootBuildImage
```

By default the image name is derived from the Gradle project name and version:
`docker.io/library/${project.name}:${project.version}`.

If you want a custom image name:

```bash
./gradlew bootBuildImage --imageName=discordmodbot:latest
```

### Run with Docker

```bash
docker run --rm -it --name discordmodbot \
  -e JAVA_TOOL_OPTIONS="-Xms256m -Xmx1g" \
  discordmodbot:latest
```

### Run with Podman

```bash
podman run --rm -it --name discordmodbot \
  -e JAVA_TOOL_OPTIONS="-Xms256m -Xmx1g" \
  discordmodbot:latest
```

### Persisting configuration and data

If you need to provide config files or persist data, mount a volume and reference it in your app configuration:

```bash
docker run --rm -it --name discordmodbot \
  -v /opt/discordmodbot:/opt/discordmodbot \
  -e SPRING_CONFIG_ADDITIONAL_LOCATION=/opt/discordmodbot/ \
  discordmodbot:latest
```

### Note on Podman rootless

Podman rootless runs with a different UID mapping. If you write to a mounted host directory, ensure it is writable by
the
container user or use `--userns=keep-id` when appropriate.

### Run container as a systemd service

You can run the container under `systemd` using either Docker or Podman. Adjust paths, env vars, and ports as needed.

#### Docker systemd unit

Create `/etc/systemd/system/discordmodbot-docker.service`:

```ini
[Unit]
Description=DiscordModBot (Docker)
After=network-online.target docker.service
Wants=network-online.target
Requires=docker.service

[Service]
Type=simple
Restart=on-failure
RestartSec=5
ExecStartPre=-/usr/bin/docker rm -f discordmodbot
ExecStart=/usr/bin/docker run --name discordmodbot \
  -e JAVA_TOOL_OPTIONS="-Xms256m -Xmx1g" \
  discordmodbot:latest
ExecStop=/usr/bin/docker stop discordmodbot

[Install]
WantedBy=multi-user.target
```

Enable and start:

```bash
sudo systemctl daemon-reload
sudo systemctl enable discordmodbot-docker
sudo systemctl start discordmodbot-docker
```

#### Podman systemd unit

Create `/etc/systemd/system/discordmodbot-podman.service`:

```ini
[Unit]
Description=DiscordModBot (Podman)
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
Restart=on-failure
RestartSec=5
ExecStartPre=-/usr/bin/podman rm -f discordmodbot
ExecStart=/usr/bin/podman run --name discordmodbot \
  -e JAVA_TOOL_OPTIONS="-Xms256m -Xmx1g" \
  discordmodbot:latest
ExecStop=/usr/bin/podman stop discordmodbot

[Install]
WantedBy=multi-user.target
```

Enable and start:

```bash
sudo systemctl daemon-reload
sudo systemctl enable discordmodbot-podman
sudo systemctl start discordmodbot-podman
```
