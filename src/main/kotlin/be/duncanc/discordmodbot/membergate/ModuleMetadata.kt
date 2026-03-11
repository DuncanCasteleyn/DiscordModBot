package be.duncanc.discordmodbot.membergate

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

@PackageInfo
@ApplicationModule(allowedDependencies = ["logging"])
class ModuleMetadata
