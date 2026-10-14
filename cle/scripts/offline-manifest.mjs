import {readdir,writeFile,readFile} from 'node:fs/promises'
import {createHash} from 'node:crypto'
const files=await readdir(new URL('../dist/assets/',import.meta.url))
const paths=files.filter(f=>/\.(js|css|woff2?)$/.test(f)).map(f=>'/assets/'+f)
const hash=createHash('sha256').update(JSON.stringify(paths)).digest('hex').slice(0,12)
await writeFile(new URL('../dist/offline-assets.json',import.meta.url),JSON.stringify(paths))
const sw=await readFile(new URL('../public/offline-sw.js',import.meta.url),'utf8')
await writeFile(new URL('../dist/offline-sw.js',import.meta.url),sw.replace('pkb-shell-v1','pkb-shell-'+hash))
