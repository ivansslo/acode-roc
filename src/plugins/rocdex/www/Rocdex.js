/**
 * Rocdex Cordova Plugin — JavaScript API
 *
 * Provides access to the Codex CLI web interface manager.
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
   * @returns {Promise<{ port: number, pid: number }>}
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
};

module.exports = Rocdex;
