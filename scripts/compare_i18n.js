const fs = require('fs');
const path = require('path');

const enPath = path.join(__dirname, '..', 'src', 'main', 'webapp', 'assets', 'i18n', 'en.json');
const arPath = path.join(__dirname, '..', 'src', 'main', 'webapp', 'assets', 'i18n', 'ar.json');

function flatten(obj, prefix = ''){
  const res = {};
  for(const k of Object.keys(obj)){
    const val = obj[k];
    const key = prefix ? `${prefix}.${k}` : k;
    if(val && typeof val === 'object' && !Array.isArray(val)){
      Object.assign(res, flatten(val, key));
    } else {
      res[key] = true;
    }
  }
  return res;
}

function main(){
  const en = JSON.parse(fs.readFileSync(enPath, 'utf8'));
  const ar = JSON.parse(fs.readFileSync(arPath, 'utf8'));
  const enKeys = Object.keys(flatten(en));
  const arKeys = new Set(Object.keys(flatten(ar)));

  const missing = enKeys.filter(k => !arKeys.has(k));
  const extra = [...arKeys].filter(k => !enKeys.includes(k));

  console.log('EN keys:', enKeys.length);
  console.log('AR keys:', arKeys.size);
  console.log('Missing in AR (count):', missing.length);
  if(missing.length>0){
    console.log('\n--- Missing keys in ar.json ---');
    missing.forEach(k => console.log(k));
  }

  console.log('\nExtra keys in AR not in EN (count):', extra.length);
  if(extra.length>0){
    console.log('\n--- Extra keys in ar.json ---');
    extra.forEach(k => console.log(k));
  }
}

main();
