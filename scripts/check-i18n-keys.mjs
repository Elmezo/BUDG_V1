/**
 * Compare dot-flattened keys in en.json vs ar.json. Exit 1 if either side is missing keys.
 * Usage: node scripts/check-i18n-keys.mjs
 */
import { readFileSync } from 'fs';
import { fileURLToPath } from 'url';
import { dirname, join } from 'path';

const __dirname = dirname(fileURLToPath(import.meta.url));
const root = join(__dirname, '..');
const enPath = join(root, 'src', 'main', 'webapp', 'assets', 'i18n', 'en.json');
const arPath = join(root, 'src', 'main', 'webapp', 'assets', 'i18n', 'ar.json');

function flatten(obj, prefix = '') {
    const out = new Set();
    if (obj === null || typeof obj !== 'object' || Array.isArray(obj)) {
        return out;
    }
    for (const k of Object.keys(obj)) {
        const path = prefix ? `${prefix}.${k}` : k;
        const v = obj[k];
        if (v !== null && typeof v === 'object' && !Array.isArray(v)) {
            for (const x of flatten(v, path)) out.add(x);
        } else {
            out.add(path);
        }
    }
    return out;
}

const en = JSON.parse(readFileSync(enPath, 'utf8'));
const ar = JSON.parse(readFileSync(arPath, 'utf8'));
const enKeys = flatten(en);
const arKeys = flatten(ar);

const onlyEn = [...enKeys].filter((k) => !arKeys.has(k)).sort();
const onlyAr = [...arKeys].filter((k) => !enKeys.has(k)).sort();

if (onlyEn.length || onlyAr.length) {
    if (onlyEn.length) {
        console.error('Keys in en.json missing from ar.json:');
        onlyEn.forEach((k) => console.error('  ', k));
    }
    if (onlyAr.length) {
        console.error('Keys in ar.json missing from en.json:');
        onlyAr.forEach((k) => console.error('  ', k));
    }
    process.exit(1);
}

console.log(`i18n OK: ${enKeys.size} keys match in en.json and ar.json.`);
