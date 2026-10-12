const crypto = require('crypto')
const { DistributionAPI } = require('helios-core/common')
const { RestResponseStatus } = require('helios-core/common')
const fs = require('fs-extra')
const path = require('path')

const ConfigManager = require('./configmanager')

// Old WesterosCraft url.
// exports.REMOTE_DISTRO_URL = 'http://mc.westeroscraft.com/WesterosCraftLauncher/distribution.json'
exports.REMOTE_DISTRO_URL = 'https://helios-files.geekcorner.eu.org/distribution.json'

// Distribution bundled with the launcher (Fabric). Regenerate with scripts/generate-distribution.js.
exports.BUNDLED_DISTRO_PATH = path.join(__dirname, '..', 'distribution.json')

const api = new DistributionAPI(
    ConfigManager.getLauncherDirectory(),
    null, // Injected forcefully by the preloader.
    null, // Injected forcefully by the preloader.
    exports.REMOTE_DISTRO_URL,
    false
)

// Load the bundled distribution instead of pulling it from the remote server.
api.pullRemote = async function() {
    try {
        return {
            data: await fs.readJson(exports.BUNDLED_DISTRO_PATH),
            responseStatus: RestResponseStatus.SUCCESS
        }
    } catch (err) {
        return {
            data: null,
            responseStatus: RestResponseStatus.ERROR,
            error: err
        }
    }
}

exports.DistroAPI = api

// Files bundled with the launcher use artifact urls of the form "bundled:<path in app/assets/bundled>".
const BUNDLED_PREFIX = 'bundled:'
exports.BUNDLED_DIR = path.join(__dirname, '..', 'bundled')

function md5(buf) {
    return crypto.createHash('md5').update(buf).digest('hex')
}

async function installBundledModules(modules) {
    for(const mdl of modules) {
        const url = mdl.rawModule.artifact?.url
        if(url != null && url.startsWith(BUNDLED_PREFIX)) {
            const target = mdl.getPath()
            const expected = mdl.rawModule.artifact.MD5
            if(!await fs.pathExists(target) || md5(await fs.readFile(target)) !== expected) {
                // Read + write instead of copy so it also works from inside the asar archive.
                const data = await fs.readFile(path.join(exports.BUNDLED_DIR, url.substring(BUNDLED_PREFIX.length)))
                await fs.ensureDir(path.dirname(target))
                await fs.writeFile(target, data)
            }
        }
        if(mdl.subModules.length > 0) {
            await installBundledModules(mdl.subModules)
        }
    }
}

/**
 * Copy the files bundled with the launcher (NightWatch client mod, client config)
 * to their location so file validation does not try to download them.
 *
 * @param {HeliosServer} server The server to install bundled files for.
 */
exports.installBundledFiles = async function(server) {
    await installBundledModules(server.modules)
}

/**
 * Write the launcher session of the account to the game folder (config/nightwatch/session.json).
 * The NightWatch client mod sends it to the server, which logs the player in to AuthMe automatically.
 *
 * @param {HeliosServer} server The server being launched.
 * @param {Object} account The selected auth account.
 */
exports.writeLauncherSession = async function(server, account) {
    const file = path.join(ConfigManager.getInstanceDirectory(), server.rawServer.id, 'config', 'nightwatch', 'session.json')
    if(account?.type !== 'offline' || !account.sessionToken) {
        await fs.remove(file)
        return
    }
    await fs.ensureDir(path.dirname(file))
    await fs.writeJson(file, { username: account.username, token: account.sessionToken })
}
