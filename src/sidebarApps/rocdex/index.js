/**
 * Rocdex Sidebar App
 *
 * Quick-access icon in the sidebar that opens the Rocdex page.
 */

import openRocdex from "pages/rocdex";

export default [
	"cloud", // icon
	"rocdex", // id
	"Rocdex", // title
	initApp, // init function
	false, // prepend
	onSelected, // onSelected
];

function initApp(el) {
	el.classList.add("rocdex-sidebar");
}

function onSelected() {
	openRocdex();
}
