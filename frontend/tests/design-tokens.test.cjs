const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const css = fs.readFileSync(path.join(__dirname, '../css/variables.css'), 'utf8');
const [light, dark] = css.split(':root[data-theme="dark"]');
const tokens = source => Object.fromEntries([...source.matchAll(/(--[\w-]+):\s*(#[\da-f]{6});/gi)].map(m => [m[1], m[2]]));
function luminance(hex) {
  const rgb = [1, 3, 5].map(i => parseInt(hex.slice(i, i + 2), 16) / 255).map(v => v <= .04045 ? v / 12.92 : ((v + .055) / 1.055) ** 2.4);
  return rgb[0] * .2126 + rgb[1] * .7152 + rgb[2] * .0722;
}
function ratio(a, b) {
  const [low, high] = [luminance(a), luminance(b)].sort((a, b) => a - b);
  return (high + .05) / (low + .05);
}
for (const [theme, colors] of [['light', tokens(light)], ['dark', { ...tokens(light), ...tokens(dark) }]]) {
  test(`${theme}: editorial text, badges, buttons and control boundaries retain accessible contrast`, () => {
    const check = (foreground, background, minimum = 4.5) => assert.ok(ratio(colors[foreground], colors[background]) >= minimum, `${foreground} on ${background}: ${ratio(colors[foreground], colors[background]).toFixed(2)} < ${minimum}`);
    for (const background of ['--bg-body', '--bg-surface', '--bg-surface-alt', '--bg-surface-elevated', '--bg-input']) {
      for (const text of ['--text-main', '--text-muted', '--text-subtle', '--text-link']) check(text, background);
    }
    for (const status of ['open', 'assigned', 'in-progress', 'resolved', 'closed']) check(`--status-${status}-text`, `--status-${status}-bg`);
    for (const color of ['accent', 'success', 'danger']) {
      check('--text-on-accent', `--color-${color}`);
      check('--text-on-accent', `--color-${color}-hover`);
    }
    check('--border-control', '--bg-input', 3);
    check('--border-focus', '--bg-input', 3);
  });
}
