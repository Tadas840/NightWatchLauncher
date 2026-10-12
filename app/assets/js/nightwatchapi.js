/**
 * Client for the NightWatchAPI server plugin (launcher login and player stats).
 *
 * The API uses a self-signed HTTPS certificate. Only that certificate is trusted
 * (app/assets/nightwatch-api.pem, copied from nightwatch/nightwatch-api.pem by
 * scripts/generate-distribution.js), so the connection cannot be intercepted.
 */
const fs    = require('fs')
const http  = require('http')
const https = require('https')
const path  = require('path')

const { DistroAPI } = require('./distromanager')
const ConfigManager = require('./configmanager')

const CERTIFICATE_PATH = path.join(__dirname, '..', 'nightwatch-api.pem')

let certificate = null
function getCertificate() {
    if(certificate == null) {
        certificate = fs.readFileSync(CERTIFICATE_PATH)
    }
    return certificate
}

/**
 * @returns {Promise.<string|null>} Base url of the API without a trailing slash, or null if not configured.
 */
async function getBaseUrl() {
    const distro = await DistroAPI.getDistribution()
    const server = distro.getServerById(ConfigManager.getSelectedServer()) ?? distro.getMainServer()
    const url = server?.rawServer.statsApi
    return url ? url.replace(/\/$/, '') : null
}

/**
 * Send a request to the API.
 *
 * @param {string} apiPath Path, e.g. /auth/login.
 * @param {Object} options method, body (JSON) and timeout in ms.
 * @returns {Promise.<{status: number, data: Object}|null>} null if the API is not configured.
 */
exports.request = async function(apiPath, { method = 'GET', body = null, timeout = 10000 } = {}) {
    const base = await getBaseUrl()
    if(base == null) {
        return null
    }
    const url = new URL(base + apiPath)
    const payload = body != null ? Buffer.from(JSON.stringify(body)) : null

    const options = {
        method,
        timeout,
        headers: payload != null ? { 'Content-Type': 'application/json', 'Content-Length': payload.length } : {}
    }
    if(url.protocol === 'https:') {
        // Trust only the NightWatch certificate. It is self-signed and not bound to a host name,
        // so skip the host name check: only the server holding its private key can present it.
        options.ca = getCertificate()
        options.checkServerIdentity = () => undefined
    }

    return new Promise((resolve, reject) => {
        const req = (url.protocol === 'https:' ? https : http).request(url, options, res => {
            const chunks = []
            res.on('data', chunk => chunks.push(chunk))
            res.on('end', () => {
                let data = {}
                try {
                    data = JSON.parse(Buffer.concat(chunks).toString('utf-8'))
                } catch (_err) {
                    // Not JSON, leave empty.
                }
                resolve({ status: res.statusCode, data })
            })
        })
        req.on('timeout', () => req.destroy(new Error('Request timed out')))
        req.on('error', reject)
        if(payload != null) {
            req.write(payload)
        }
        req.end()
    })
}
