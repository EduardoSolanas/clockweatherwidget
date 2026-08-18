import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const html = readFileSync(new URL('./weather-animation-dashboard.html', import.meta.url), 'utf8');
const routes = [
  'clear-day', 'clear-night', 'partly-day', 'partly-night', 'overcast', 'fog',
  'drizzle', 'rain', 'heavy-rain', 'snow', 'thunder'
];

assert.doesNotMatch(html, /<img\b|<canvas\b|\.png\b/i, 'dashboard must use inline SVG rather than raster or canvas art');
assert.equal((html.match(/<svg\b/g) ?? []).length, 44, '11 originals and 33 alternatives must be inline SVG');
for (const route of routes) assert.match(html, new RegExp(`data-family="${route}"`));
for (const control of ['pause', 'reduce', 'speed', 'filter']) assert.match(html, new RegExp(`id="${control}"`));
for (const constant of ['20000', '3000', '1500', '4000', '6000', '25000', '14 rays', '25 dust', '40 stars', '5 fog layers', '64 rain particles']) {
  assert.ok(html.includes(constant), `missing source-derived constant: ${constant}`);
}
for (const range of ['21–22', '23–24', '25', '26', '27–37', '38–54', '55–67', '68–69', '227–315', '316–485', '486–540', '541–570', '571–637', '638–678', '679–755', '756–916', '917–978']) {
  assert.ok(html.includes(range), `missing source line mapping: ${range}`);
}

const script = html.match(/<script>([\s\S]*?)<\/script>/)?.[1];
assert.ok(script, 'dashboard must have embedded JavaScript');
new Function(script);
console.log('PASS: 44 inline SVG scenes, 11 routes, source constants, controls, and mappings verified.');
