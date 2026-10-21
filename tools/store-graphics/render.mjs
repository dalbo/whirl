import { chromium } from 'playwright';
// usage (from tools/capture, which has Playwright): node ../store-graphics/render.mjs <abs dir with icon.html, feature.html>
const [,, dir] = process.argv;
const b = await chromium.launch();
for (const [name, w, h] of [['icon',512,512],['feature',1024,500]]) {
  const p = await b.newPage({ viewport: { width: w, height: h } });
  await p.goto('file://' + dir + '/' + name + '.html');
  await p.screenshot({ path: dir + '/' + name + '.png', omitBackground: false });
}
await b.close();
