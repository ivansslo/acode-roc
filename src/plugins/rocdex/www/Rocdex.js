/**
 * Rocdex Cordova Plugin — JavaScript API
 *
 * Provides access to the Codex CLI web interface manager and license system.
 */
const exec = (action, args = []) =>
  new Promise((resolve, reject) => {
    cordova.exec(resolve, reject, "Rocdex", action, args);
  });

const Rocdex = {
  /**
   * Check whether Node.js is available on the device.
   * @returns {Promise<{ available: boolean, version?: string }>}
   */
  checkNode() {
    return exec("checkNode");
  },

  /**
   * Start the codexapp server on the given port.
   * @param {number} [port=18923]
   * @returns {Promise<{ port: number }>}
   */
  startServer(port = 18923) {
    return exec("startServer", [port]);
  },

  /**
   * Stop the running codexapp server.
   * @returns {Promise<{ stopped: boolean }>}
   */
  stopServer() {
    return exec("stopServer");
  },

  /**
   * Check whether the codexapp server is currently running.
   * @returns {Promise<{ running: boolean, port?: number }>}
   */
  isRunning() {
    return exec("isRunning");
  },

  /**
   * Get the server URL to connect to.
   * @returns {Promise<{ url: string }>}
   */
  getServerUrl() {
    return exec("getServerUrl");
  },

  /**
   * Install / update codexapp via npm.
   * @returns {Promise<{ installed: boolean }>}
   */
  installCodexapp() {
    return exec("installCodexapp");
  },

  /**
   * Check if a valid license is installed.
   * @returns {Promise<{ valid: boolean, licensedUser?: string }>}
   */
  checkLicense() {
    return exec("checkLicense");
  },

  /**
   * Activate license with a key.
   * Format: "user|signature_hex"
   * @param {string} licenseKey
   * @returns {Promise<{ valid: boolean, licensedUser?: string, message?: string }>}
   */
  setLicense(licenseKey) {
    return exec("setLicense", [licenseKey]);
  },

  /**
   * Get current license info.
   * @returns {Promise<{ valid: boolean, licensedUser?: string, packageName?: string }>}
   */
  getLicenseInfo() {
    return exec("getLicenseInfo");
  },
};

module.exports = Rocdex;
