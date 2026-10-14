/* Public application files only: authenticated API responses are never cached. */
const CACHE='pkb-shell-v1';
self.addEventListener('install',event=>{event.waitUntil((async()=>{const paths=await fetch('/offline-assets.json',{cache:'no-store'}).then(r=>{if(!r.ok)throw Error('Offline asset manifest unavailable');return r.json()});const cache=await caches.open(CACHE);await cache.addAll(['/index.html',...paths.filter(p=>/^\/assets\/[a-zA-Z0-9_.-]+$/.test(p))]);await self.skipWaiting()})())});
self.addEventListener('activate',event=>event.waitUntil((async()=>{const keys=(await caches.keys()).filter(k=>k.startsWith('pkb-shell-')&&k!==CACHE);await Promise.all(keys.slice(0,-1).map(k=>caches.delete(k)));await self.clients.claim()})()));
self.addEventListener('fetch',event=>{const url=new URL(event.request.url);if(event.request.method!=='GET'||url.origin!==self.location.origin)return;
 if(url.pathname==='/offline'&&event.request.mode==='navigate'){event.respondWith(fetch(event.request).catch(()=>caches.open(CACHE).then(c=>c.match('/index.html')).then(r=>r||Response.error())));return;}
 if(/^\/assets\/[a-zA-Z0-9_.-]+$/.test(url.pathname))event.respondWith(caches.open(CACHE).then(async cache=>(await cache.match(event.request))||fetch(event.request)));
});
