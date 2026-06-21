/**
 * Rocdex Page
 *
 * Provides an in-app interface for the Codex CLI web UI (codexapp)
 * and Rocdex license management.
 */

import "./rocdex.scss";

import Page from "components/page";
import toast from "components/toast";
import actionStack from "lib/actionStack";
import prompt from "dialogs/prompt";
import { hideAd } from "lib/startAd";
import helpers from "utils/helpers";

const SERVER_URL = "http://127.0.0.1:18923";

export default function openRocdex() {
  const $page = Page("Rocdex");
  $page.classList.add("rocdex-page");

  let statusDot = <span className="rocdex-status-dot rocdex-status--off" />;
  let statusLabel = <span className="rocdex-status-label">{strings.rocdex_stopped}</span>;
  let actionBtn = (
    <button className="rocdex-btn rocdex-btn--start">
      {strings.rocdex_start}
    </button>
  );
  let $iframe = <iframe className="rocdex-iframe" src="about:blank" />;

  // ── License UI ──────────────────────────────────────────────────────────
  let licenseSection = <div className="rocdex-license-section" />;

  async function renderLicenseStatus() {
    try {
      if (!window.Rocdex) {
        licenseSection.textContent = "";
        return;
      }
      const info = await window.Rocdex.checkLicense();
      if (info.valid && info.licensedUser) {
        licenseSection.innerHTML = `
          <div class="rocdex-license-badge rocdex-license--active">
            <span class="rocdex-license-icon">&#10003;</span>
            <span>Licensed to: <strong>${info.licensedUser}</strong></span>
          </div>
        `;
      } else {
        licenseSection.innerHTML = `
          <div class="rocdex-license-badge rocdex-license--inactive">
            <span class="rocdex-license-icon">&#9888;</span>
            <span>Unlicensed version — <a href="#" class="rocdex-license-activate-link">Activate License</a></span>
          </div>
        `;
        const link = licenseSection.querySelector(".rocdex-license-activate-link");
        if (link) {
          link.onclick = (e) => {
            e.preventDefault();
            showLicenseDialog();
          };
        }
      }
    } catch {
      licenseSection.innerHTML = `
        <div class="rocdex-license-badge rocdex-license--inactive">
          <span class="rocdex-license-icon">&#9888;</span>
          <span>License check unavailable</span>
        </div>
      `;
    }
  }

  async function showLicenseDialog() {
    const key = await prompt("Enter License Key", "", "Activate", "Cancel", {
      placeholder: "user@example.com|<signature>",
      inputType: "text",
    });
    if (!key || key.trim() === "") return;

    try {
      if (!window.Rocdex) {
        toast("Rocdex plugin not available", "error");
        return;
      }
      const result = await window.Rocdex.setLicense(key.trim());
      if (result.valid) {
        toast("License activated for: " + result.licensedUser, "success");
        renderLicenseStatus();
      } else {
        toast(result.message || "Invalid license key", "error");
      }
    } catch (e) {
      toast("License activation failed: " + e.message, "error");
    }
  }

  // ── Status Bar ──────────────────────────────────────────────────────────
  const $statusBar = (
    <div className="rocdex-status-bar">
      {statusDot}
      {statusLabel}
    </div>
  );

  const $toolbar = (
    <div className="rocdex-toolbar">
      {actionBtn}
      <button
        className="rocdex-btn rocdex-btn--open"
        disabled={true}
        onclick={() => {
          system.openInBrowser(SERVER_URL);
        }}
      >
        {strings.rocdex_open_in_browser}
      </button>
    </div>
  );

  const $container = (
    <div className="rocdex-container">
      <div className="rocdex-header">
        <h2 className="rocdex-title">Rocdex — Codex UI</h2>
        <p className="rocdex-subtitle">
          Codex CLI web interface for Android
        </p>
      </div>
      {licenseSection}
      {$statusBar}
      {$toolbar}
      <div className="rocdex-frame-wrapper">{$iframe}</div>
    </div>
  );

  $page.body = $container;

  // ── State ────────────────────────────────────────────────────────────────
  let serverRunning = false;

  function setStatus(running) {
    serverRunning = running;
    statusDot.className =
      "rocdex-status-dot " +
      (running ? "rocdex-status--on" : "rocdex-status--off");
    statusLabel.textContent = running
      ? strings.rocdex_running
      : strings.rocdex_stopped;
    actionBtn.textContent = running
      ? strings.rocdex_stop
      : strings.rocdex_start;
    actionBtn.className =
      "rocdex-btn " +
      (running ? "rocdex-btn--stop" : "rocdex-btn--start");
    $page.querySelector(".rocdex-btn--open").disabled = !running;
    if (running) {
      $iframe.src = SERVER_URL;
    } else {
      $iframe.src = "about:blank";
    }
  }

  async function refreshStatus() {
    try {
      if (window.Rocdex) {
        const res = await window.Rocdex.isRunning();
        setStatus(res.running);
      }
    } catch {
      setStatus(false);
    }
  }

  async function toggleServer() {
    if (serverRunning) {
      try {
        if (window.Rocdex) {
          await window.Rocdex.stopServer();
        }
        setStatus(false);
        toast(strings.rocdex_server_stopped);
      } catch (e) {
        toast(
          strings.rocdex_failed_stop.replace("{error}", e.message),
          "error",
        );
      }
    } else {
      try {
        if (window.Rocdex) {
          const nodeInfo = await window.Rocdex.checkNode();
          if (!nodeInfo.available) {
            toast(strings.rocdex_node_missing, "warning");
            return;
          }
          await window.Rocdex.startServer(18923);
        }
        setStatus(true);
        toast(strings.rocdex_server_started.replace("{url}", SERVER_URL));
      } catch (e) {
        toast(
          strings.rocdex_failed_start.replace("{error}", e.message),
          "error",
        );
        setStatus(false);
      }
    }
  }

  // ── Wire up events ───────────────────────────────────────────────────
  actionBtn.onclick = toggleServer;

  actionStack.push({
    id: "rocdex",
    action: $page.hide,
  });

  $page.onhide = function () {
    actionStack.remove("rocdex");
    hideAd();
  };

  // ── Initial checks ───────────────────────────────────────────────────
  renderLicenseStatus();
  refreshStatus();

  app.append($page);
  helpers.showAd();
}
