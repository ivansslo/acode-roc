/**
 * Rocdex License Key Generator
 *
 * Generates license keys for Rocdex users.
 * Each key is bound to a user identifier (e.g. email).
 *
 * Usage:
 *   node utils/gen-license.js user@example.com
 *
 * Output:
 *   user@example.com|<hex_signature>
 *
 * The user enters this key in the Rocdex License activation dialog.
 */

const crypto = require("crypto");

function generateLicense(userIdentifier) {
  if (!userIdentifier || userIdentifier.trim() === "") {
    console.error("Usage: node utils/gen-license.js <user-identifier>");
    process.exit(1);
  }

  const user = userIdentifier.trim().toLowerCase();
  const secret = "rocdex:secret2026";
  const raw = user + ":" + secret;
  const hash = crypto.createHash("sha256").update(raw, "utf8").digest("hex");
  const licenseKey = user + "|" + hash;

  console.log("\n================================================");
  console.log("  ROCDEX LICENSE KEY");
  console.log("================================================");
  console.log(`  User:    ${user}`);
  console.log(`  Key:     ${licenseKey}`);
  console.log("================================================");
  console.log("  Give this entire line to the user.");
  console.log("  They enter it in Rocdex > Activate License.");
  console.log("================================================");

  return licenseKey;
}

const userArg = process.argv[2];
generateLicense(userArg);
