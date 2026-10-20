// Renders the bitmap icons from the SVGs of this folder:
//   - the legacy launcher icons of Android before 8 (mipmap-*dpi/ic_launcher.png and ic_launcher_round.png);
//   - the icon of the store listing (matchlab-playstore-512.png).
// The adaptive icon (Android 8+) is not drawn from here: its two layers are vector drawables in androidApp/src/main/res/drawable
// (ic_launcher_foreground.xml and ic_launcher_monochrome.xml) over the color ic_launcher_background in values/colors.xml.
//
// Run from any folder, after `npm install @resvg/resvg-js` (the package is not a dependency of the project):
//   node docs/brand/generate-android-icons.mjs
import { Resvg } from '@resvg/resvg-js';
import { readFileSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const brand = fileURLToPath(new URL('.', import.meta.url));
const res = fileURLToPath(new URL('../../androidApp/src/main/res/', import.meta.url));
// The size of a launcher icon in pixels at each density (48dp).
const densities = { mdpi: 48, hdpi: 72, xhdpi: 96, xxhdpi: 144, xxxhdpi: 192 };

const render = (svgFile, size) =>
  new Resvg(readFileSync(`${brand}${svgFile}`, 'utf8'), { fitTo: { mode: 'width', value: size } }).render().asPng();

for (const [density, size] of Object.entries(densities)) {
  writeFileSync(`${res}mipmap-${density}/ic_launcher.png`, render('matchlab-icon.svg', size));
  writeFileSync(`${res}mipmap-${density}/ic_launcher_round.png`, render('matchlab-icon-round.svg', size));
}
writeFileSync(`${brand}matchlab-playstore-512.png`, render('matchlab-icon-fullbleed.svg', 512));
