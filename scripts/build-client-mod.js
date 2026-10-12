/**
 * Builds the NightWatch client mod (client-mod/) and copies it to app/assets/bundled/mods/.
 * Gradle must run on Java 25+ (JAVA_HOME); the mod itself is compiled with a Java 21 toolchain.
 */
const { spawnSync } = require('child_process')
const path = require('path')

const dir = path.join(__dirname, '..', 'client-mod')
const gradlew = path.join(dir, process.platform === 'win32' ? 'gradlew.bat' : 'gradlew')

const result = spawnSync(`"${gradlew}"`, ['installToLauncher', '--no-daemon'], { cwd: dir, stdio: 'inherit', shell: true })
process.exit(result.status ?? 1)
