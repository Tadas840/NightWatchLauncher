/**
 * Generates app/assets/distribution.json from nightwatch/nightwatch.json.
 *
 * Usage: node scripts/generate-distribution.js
 *
 * - Fabric loader and its libraries are downloaded from maven.fabricmc.net.
 * - Mods listed in nightwatch.json are downloaded from their URL.
 * - Mods in nightwatch/mods/ and app/assets/bundled/mods/ (the NightWatch client mod)
 *   are bundled with the launcher.
 * - Files in nightwatch/client/ are bundled and installed to the game's config/nightwatch/ folder.
 */
const crypto = require('crypto')
const fs = require('fs')
const path = require('path')

const ROOT = path.join(__dirname, '..')
const SETTINGS_DIR = path.join(ROOT, 'nightwatch')
const BUNDLED_DIR = path.join(ROOT, 'app', 'assets', 'bundled')
const META = 'https://meta.fabricmc.net/v2'

const settings = JSON.parse(fs.readFileSync(path.join(SETTINGS_DIR, 'nightwatch.json'), 'utf-8'))

async function fetchBuffer(url) {
    const res = await fetch(url)
    if(!res.ok) {
        throw new Error(`GET ${url} failed: ${res.status}`)
    }
    return Buffer.from(await res.arrayBuffer())
}

function mavenUrl(repo, name) {
    const [group, artifact, version] = name.split(':')
    return `${repo.replace(/\/$/, '')}/${group.replace(/\./g, '/')}/${artifact}/${version}/${artifact}-${version}.jar`
}

function md5(buf) {
    return crypto.createHash('md5').update(buf).digest('hex')
}

async function remoteArtifact(url) {
    const buf = await fetchBuffer(url)
    return { size: buf.length, MD5: md5(buf), url }
}

/** Artifact for a file bundled with the launcher (relative to app/assets/bundled). */
function bundledArtifact(relativePath) {
    const buf = fs.readFileSync(path.join(BUNDLED_DIR, relativePath))
    return { size: buf.length, MD5: md5(buf), url: `bundled:${relativePath.split(path.sep).join('/')}` }
}

function listFiles(dir) {
    if(!fs.existsSync(dir)) {
        return []
    }
    return fs.readdirSync(dir, { withFileTypes: true }).flatMap(entry => {
        const full = path.join(dir, entry.name)
        return entry.isDirectory() ? listFiles(full) : [full]
    })
}

function copyInto(srcDir, destDir, filter = () => true) {
    for(const file of listFiles(srcDir).filter(filter)) {
        const dest = path.join(destDir, path.relative(srcDir, file))
        fs.mkdirSync(path.dirname(dest), { recursive: true })
        fs.copyFileSync(file, dest)
    }
}

/**
 * Server resource pack from nightwatch.json: either { url, id, sha1 } or
 * { serverProperties: "<path to server.properties>" } to read resource-pack, resource-pack-id and resource-pack-sha1.
 */
function resolveServerResourcePack(config) {
    if(config == null) {
        return null
    }
    if(config.serverProperties) {
        const props = {}
        for(const line of fs.readFileSync(config.serverProperties, 'utf-8').split(/\r?\n/)) {
            const match = line.match(/^([^#=]+)=(.*)$/)
            if(match) {
                props[match[1].trim()] = match[2].trim().replace(/\\(.)/g, '$1')
            }
        }
        if(!props['resource-pack']) {
            return null
        }
        return { url: props['resource-pack'], id: props['resource-pack-id'], sha1: props['resource-pack-sha1'] }
    }
    return config
}

async function fabricModule(mcVersion, loaderVersion) {
    let loader = loaderVersion
    if(loader == null || loader === 'latest') {
        const loaders = JSON.parse((await fetchBuffer(`${META}/versions/loader/${mcVersion}`)).toString())
        loader = loaders.find(l => l.loader.stable).loader.version
    }
    console.log(`Minecraft ${mcVersion}, Fabric loader ${loader}`)

    const profileUrl = `${META}/versions/loader/${mcVersion}/${loader}/profile/json`
    const profile = JSON.parse((await fetchBuffer(profileUrl)).toString())

    const loaderName = `net.fabricmc:fabric-loader:${loader}`
    const libraries = []
    for(const lib of profile.libraries) {
        if(lib.name === loaderName) {
            continue
        }
        console.log(`  library ${lib.name}`)
        libraries.push({
            id: lib.name,
            name: lib.name,
            type: 'Library',
            artifact: await remoteArtifact(mavenUrl(lib.url, lib.name))
        })
    }

    return {
        loader,
        module: {
            id: loaderName,
            name: `Fabric Loader ${loader}`,
            type: 'Fabric',
            artifact: await remoteArtifact(mavenUrl('https://maven.fabricmc.net/', loaderName)),
            subModules: [
                {
                    id: profile.id,
                    name: `Fabric Version Manifest ${profile.id}`,
                    type: 'VersionManifest',
                    artifact: await remoteArtifact(profileUrl)
                },
                ...libraries
            ]
        }
    }
}

async function main() {
    const mcVersion = settings.minecraftVersion
    const fabric = await fabricModule(mcVersion, settings.fabricLoaderVersion)
    const modules = [fabric.module]

    // Mods downloaded from the internet.
    for(const mod of settings.mods ?? []) {
        console.log(`  mod ${mod.name}`)
        modules.push({
            id: mod.id,
            name: mod.name,
            type: 'FabricMod',
            artifact: await remoteArtifact(mod.url)
        })
    }

    // Server resource pack, downloaded in advance to the folder where Minecraft caches it
    // (downloads/<pack id>/<sha1>), so the game does not download it when joining.
    const pack = resolveServerResourcePack(settings.serverResourcePack)
    if(pack != null) {
        console.log(`  server resource pack ${pack.url}`)
        const buf = await fetchBuffer(pack.url)
        const sha1 = crypto.createHash('sha1').update(buf).digest('hex')
        if(sha1 !== pack.sha1.toLowerCase()) {
            throw new Error(`Server resource pack SHA-1 mismatch: expected ${pack.sha1}, got ${sha1}`)
        }
        modules.push({
            id: 'nightwatch-server-resource-pack',
            name: 'Server resource pack',
            type: 'File',
            artifact: { size: buf.length, MD5: md5(buf), url: pack.url, path: `downloads/${pack.id}/${sha1}` }
        })
    }

    // Mods bundled with the launcher.
    copyInto(path.join(SETTINGS_DIR, 'mods'), path.join(BUNDLED_DIR, 'mods'), f => f.endsWith('.jar'))
    for(const file of listFiles(path.join(BUNDLED_DIR, 'mods')).filter(f => f.endsWith('.jar'))) {
        const name = path.basename(file, '.jar')
        console.log(`  bundled mod ${name}`)
        modules.push({
            id: `nightwatch.bundled:${name}:1.0.0`,
            name,
            type: 'FabricMod',
            artifact: bundledArtifact(path.relative(BUNDLED_DIR, file))
        })
    }

    // Client config (config/nightwatch/*) bundled with the launcher.
    const configDir = path.join(BUNDLED_DIR, 'config', 'nightwatch')
    fs.rmSync(configDir, { recursive: true, force: true })
    copyInto(path.join(SETTINGS_DIR, 'client'), configDir)
    for(const file of listFiles(configDir)) {
        const relative = path.relative(BUNDLED_DIR, file)
        console.log(`  bundled file ${relative}`)
        modules.push({
            id: `nightwatch-${relative.split(path.sep).join('-')}`,
            name: relative,
            type: 'File',
            artifact: {
                ...bundledArtifact(relative),
                path: relative.split(path.sep).join('/')
            }
        })
    }

    const distribution = {
        version: '1.0.0',
        rss: '',
        servers: [
            {
                id: `nightwatch-${mcVersion}`,
                name: settings.name,
                description: settings.description ?? `Minecraft ${mcVersion} su Fabric ${fabric.loader}`,
                icon: 'assets/images/SealCircle.png',
                version: '1.0.0',
                address: settings.serverAddress,
                // NightWatchAPI server plugin, used for the player stats in the launcher.
                statsApi: settings.statsApi,
                minecraftVersion: mcVersion,
                javaOptions: {
                    supported: `>=${settings.javaVersion ?? 21}`,
                    suggestedMajor: settings.javaVersion ?? 21
                },
                mainServer: true,
                autoconnect: settings.autoConnect ?? true,
                modules
            }
        ]
    }

    // HTTPS certificate of the NightWatchAPI server plugin (the launcher trusts only this certificate).
    const certificate = path.join(SETTINGS_DIR, 'nightwatch-api.pem')
    if(fs.existsSync(certificate)) {
        fs.copyFileSync(certificate, path.join(ROOT, 'app', 'assets', 'nightwatch-api.pem'))
        console.log('  certificate nightwatch-api.pem')
    } else if(settings.statsApi?.startsWith('https:')) {
        console.warn('  WARNING: nightwatch/nightwatch-api.pem is missing, the launcher cannot connect to the API.')
    }

    const out = path.join(ROOT, 'app', 'assets', 'distribution.json')
    fs.writeFileSync(out, JSON.stringify(distribution, null, 4) + '\n')
    console.log(`Wrote ${out}`)
}

main().catch(err => {
    console.error(err)
    process.exit(1)
})
