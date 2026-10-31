import {createApp,nextTick} from 'vue'
import ReleaseHistory from '../components/ReleaseHistory.vue'
import {setUiLanguage} from '../i18n'
import ledger from '../data/releases.json'
const fixture=document.querySelector('#fixture'),checks=[]
const assert=(ok,label)=>{if(!ok)throw new Error(label);checks.push(label)}
window.fetch=()=>{throw new Error('Release history must not send account data or request remote services')}
const app=createApp(ReleaseHistory);app.mount(fixture)
try{
 await nextTick()
 assert(fixture.textContent.includes('v'+ledger.current),'Current version is visible')
 assert(fixture.textContent.includes('已发布')&&(!ledger.releases.some(entry=>entry.status!=='published')||fixture.textContent.includes('待发布')),'Publication states match the ledger')
 const entries=fixture.querySelectorAll('.release-entry')
 assert(entries.length===ledger.releases.length,'All historical versions are retained')
 assert(entries[0].querySelector('details').open,'Current changes open by default')
 assert(Boolean(entries[0].querySelector('a[href]'))===(ledger.releases[0].status==='published'),'Download links appear only for published versions')
 assert(entries[1].querySelector('a').href.endsWith('/releases/tag/v'+ledger.releases[1].version),'Published entry links to its own release')
 entries[1].querySelector('summary').click();await nextTick()
 assert(entries[1].querySelector('details').open,'Historical notes expand with the standard details control')
 assert(ledger.releases[0].notes.every(note=>entries[0].textContent.includes(note.zh)),'All release requirements remain visible')
 await setUiLanguage('en',false);await nextTick()
 assert(fixture.textContent.includes('A record of every update.')&&fixture.textContent.includes(ledger.releases[0].title.en),'Both interface and change descriptions switch to English')
 await setUiLanguage('zh',false);await nextTick()
 assert(fixture.textContent.includes('每次更新，都有记录。'),'Switching back preserves the history')
 document.documentElement.dataset.result='passed'
 document.querySelector('#results').textContent=`releases-ui: PASS (${checks.length})`
}finally{app.unmount()}
