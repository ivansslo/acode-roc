/**
 * Rocdex Page — Codex CLI Web UI integration for Acode
 *
 * Lazy-loads the full page module to keep initial bundle size small.
 */
export default function openRocdex() {
	import(/* webpackChunkName: "rocdex" */ "./rocdex").then((mod) => {
		mod.default();
	});
}
